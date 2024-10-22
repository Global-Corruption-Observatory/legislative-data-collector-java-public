package com.precognox.ceu.legislative_data_collector.india.new_website;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.india.PageType;
import com.precognox.ceu.legislative_data_collector.india.entities.IndiaCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.india.new_website.api.ApiResponse;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Parses bills from the site's API responses.
 */
@Slf4j
@Service
public class ApiBillParser {

    private final PageSourceRepository pageSourceRepository;
    private final PrimaryKeyGeneratingRepository recordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ApiBillParser(
            PageSourceRepository pageSourceRepository, PrimaryKeyGeneratingRepository recordRepository) {
        this.pageSourceRepository = pageSourceRepository;
        this.recordRepository = recordRepository;
    }

    @Transactional
    public void parseAllBills() {
        log.info("Running bill parser...");

        pageSourceRepository.streamUnprocessedPages(Country.INDIA, PageType.BILL_LIST.name())
                .flatMap(page -> parseBills(page).stream())
                .peek(bill -> log.info("Processed bill: {}", bill.getBillId()))
                .forEach(recordRepository::save);
    }

    public List<LegislativeDataRecord> parseBills(PageSource storedApiResponse) {
        try {
            ApiResponse parsed = objectMapper.readValue(storedApiResponse.getRawSource(), ApiResponse.class);

            return parsed.getRecords().stream()
                    .map(jsonRecord -> parseBill(storedApiResponse.getPageUrl(), jsonRecord))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse API response", e);
        }
    }

    private LegislativeDataRecord parseBill(String pageUrl, ApiResponse.Record jsonRecord) {
        LegislativeDataRecord result = new LegislativeDataRecord();

        result.setDateProcessed(LocalDateTime.now());
        result.setCountry(Country.INDIA);
        result.setBillTitle(jsonRecord.getBillName().strip());
        result.setBillPageUrl(pageUrl);
        result.setBillType(jsonRecord.getBillCategory());
        result.setBillStatus(parseStatus(jsonRecord.getStatus()));
        result.setDateIntroduction(parseDate(jsonRecord.getBillIntroducedDate()));
        result.setBillTextUrl(jsonRecord.getBillIntroducedFile());
        result.setLawTextUrl(jsonRecord.getBillPassedInBothHousesFile());
        result.setOriginType(parseOriginType(jsonRecord.getBillType()));
        result.setDatePassing(parseDate(jsonRecord.getBillAssentedDate()));

        Stream<String> amendmentKeywords = Stream.of("amendment", "amending", "repeal", "repealing");

        boolean originalLaw = amendmentKeywords.noneMatch(
                keyword -> jsonRecord.getBillName().toLowerCase().contains(keyword));

        result.setOriginalLaw(originalLaw);

        //default values, can be overwritten later
        result.setModifiedLawsCount(0);
        result.setAffectingLawsCount(0);

        //bill ID
        if (jsonRecord.getBillNumber() != null && jsonRecord.getBillYear() != null) {
            result.setBillId(jsonRecord.getBillNumber().strip() + " of " + jsonRecord.getBillYear());
        }

        //law ID
        if (jsonRecord.getActNo() != null) {
            result.setLawId(jsonRecord.getActNo().strip() + " of " + jsonRecord.getActYear());
        }

        result.setOriginators(parseOriginators(jsonRecord));

        //stage 1 date
        if (jsonRecord.getBillPassedInLSDate() != null) {
            LegislativeStage stage1 = new LegislativeStage(
                    null, parseDate(jsonRecord.getBillPassedInLSDate()), "Passed in LS");

            result.getStages().add(stage1);
        }

        //stage 2 date
        if (jsonRecord.getBillPassedInRSDate() != null) {
            LegislativeStage stage2 = new LegislativeStage(
                    null, parseDate(jsonRecord.getBillPassedInRSDate()), "Passed in RS");

            result.getStages().add(stage2);
        }

        result.getStages().sort(Comparator.comparing(LegislativeStage::getDate));

        //set stage numbers based on the order of the stages by date
        for (int i = 0; i < result.getStages().size(); i++) {
            result.getStages().get(i).setStageNumber(i + 1);
        }

        result.setStagesCount(result.getStages().size());
        setCountrySpecVars(jsonRecord, result);

        return result;
    }

    private List<Originator> parseOriginators(ApiResponse.Record jsonRecord) {
        if (StringUtils.isNotBlank(jsonRecord.getMinistryName())) {
            //set the ministry name as the originator affiliation, even if the originator name will be empty
            Originator orig =
                    new Originator(jsonRecord.getBillIntroducedBy(), jsonRecord.getMinistryName());

            return List.of(orig);
        }

        return new ArrayList<>();
    }

    private void setCountrySpecVars(ApiResponse.Record jsonRecord, LegislativeDataRecord result) {
        if (StringUtils.isBlank(jsonRecord.getStatus())) {
            return;
        }

        String statusString = jsonRecord.getStatus().toLowerCase();

        if (List.of("withdrawn", "lapsed").contains(statusString)) {
            IndiaCountrySpecificVariables specVars = new IndiaCountrySpecificVariables();
            specVars.setLegislativeDataRecord(result);
            result.setIndiaCountrySpecificVariables(specVars);

            if (statusString.equals("withdrawn")) {
                specVars.setWithdrawn(true);
            } else if (statusString.equals("lapsed")) {
                specVars.setLapsed(true);
            }
        }
    }

    @Nullable
    private OriginType parseOriginType(String billType) {
        return switch (billType.toLowerCase()) {
            case "government" -> OriginType.GOVERNMENT;
            case "private member" -> OriginType.INDIVIDUAL_MP;
            default -> null;
        };
    }

    private LegislativeDataRecord.BillStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }

        return switch (status.toLowerCase()) {
            case "passed", "assented" -> LegislativeDataRecord.BillStatus.PASS;
            case "withdrawn", "lapsed", "negatived", "removed" -> LegislativeDataRecord.BillStatus.REJECT;
            default -> LegislativeDataRecord.BillStatus.ONGOING;
        };
    }

    private LocalDate parseDate(@Nullable String date) {
        if (date == null) {
            return null;
        }

        if (date.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+")) {
            return parseLongDate(date);
        } else if (date.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return parseShortDate(date);
        } else {
            throw new IllegalArgumentException("Invalid date format: " + date);
        }
    }

    private LocalDate parseLongDate(@Nullable String date) {
        return date != null
                ? LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S").parse(date))
                : null;
    }

    private LocalDate parseShortDate(@Nullable String date) {
        return date != null
                ? LocalDate.from(DateTimeFormatter.ofPattern("dd/MM/yyyy").parse(date))
                : null;
    }

}
