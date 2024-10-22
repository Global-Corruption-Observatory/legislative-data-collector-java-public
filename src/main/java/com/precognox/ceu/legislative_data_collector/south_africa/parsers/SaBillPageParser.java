package com.precognox.ceu.legislative_data_collector.south_africa.parsers;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.south_africa.SaPageCollector;
import com.precognox.ceu.legislative_data_collector.south_africa.SaPageType;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.precognox.ceu.legislative_data_collector.utils.DateUtils.parseSouthAfricaDate;

@Slf4j
@Service
@AllArgsConstructor
public class SaBillPageParser {
    private static final Pattern PROCEDURE_TYPE_PATTERN = Pattern.compile("\\b(\\d+)\\b");
    private final PageSourceRepository pageSourceRepository;
    private final PrimaryKeyGeneratingRepository recordRepository;
    private final SaPageCollector saPageCollector;
    private final PdfParser pdfParser;

    @Transactional
    public void parseAllPages() {
        log.info("Querying unprocessed pages...");

        pageSourceRepository.streamUnprocessedBillPages(Country.SOUTH_AFRICA)
                .forEach(page -> {
                    log.info("Processing bill page: {}", page.getPageUrl());
                    recordRepository.save(parsePage(page));
                });
    }

    public LegislativeDataRecord parsePage(PageSource source) {
        Element page = Jsoup.parse(source.getRawSource()).body();
        LegislativeDataRecord record = new LegislativeDataRecord(Country.SOUTH_AFRICA);
        record.setBillPageUrl(source.getPageUrl());

        Optional<Element> billTitleAndIdElement = getBillTitleAndIdElement(page);

        if (billTitleAndIdElement.isPresent()) {
            parseBillId(billTitleAndIdElement.get()).ifPresent(record::setBillId);
            record.setBillTitle(parseBillTitle(billTitleAndIdElement.get(), record.getBillId()));
        }

        parseProcedureTypeNational(page).ifPresent(record::setProcedureTypeNational);
        record.setProcedureTypeStandard(parseProcedureTypeStandard(record.getProcedureTypeNational()));

        record.setOriginalLaw(parseOriginalLaw(record.getBillTitle()));
        parseBillTextUrl(page).ifPresent(record::setBillTextUrl);

        record.setDateIntroduction(parseDateIntroduction(page));

        List<LegislativeStage> legislativeStages = parseLegislativeStages(page);
        record.setStages(legislativeStages);
        record.setStagesCount(legislativeStages.size());

        return record;
    }

    public Optional<Element> getBillTitleAndIdElement(Element page) {
        return Optional.ofNullable(page.selectFirst("h1"));
    }

    public Optional<String> parseBillId(Element billTitleAndIdElement) {
        return Optional.ofNullable(billTitleAndIdElement.selectFirst("span"))
                .map(Element::text)
                .map(billId -> billId.replaceAll("[()]", ""));
    }

    public String parseBillTitle(Element billTitleAndIdElement, String billId) {
        return billTitleAndIdElement.text().replace("(" + billId + ")", "").trim();
    }

    public Optional<String> parseProcedureTypeNational(Element page) {
        return Optional.ofNullable(page.selectFirst("p:contains(Section)"))
                .map(Element::text);
    }

    public LegislativeDataRecord.ProcedureType parseProcedureTypeStandard(String procedureTypeNational) {
        if (procedureTypeNational == null) {
            log.warn("ProcedureTypeNational is null, unable to parse procedure type standard.");
            return null;
        }

        LegislativeDataRecord.ProcedureType procedureType = null;

        Optional<Integer> procedureTypeNumber = PROCEDURE_TYPE_PATTERN
                .matcher(procedureTypeNational)
                .results()
                .map(billTitle -> billTitle.group(1))
                .findFirst()
                .map(this::getProcedureTypeNumber);

        if (procedureTypeNumber.isPresent()) {
            switch (procedureTypeNumber.get()) {
                case 75, 76 -> procedureType = LegislativeDataRecord.ProcedureType.REGULAR;
                case 74, 77 -> procedureType = LegislativeDataRecord.ProcedureType.EXCEPTIONAL;
            }
        }
        return procedureType;
    }

    public Integer getProcedureTypeNumber(String procedureTypeNationalString) {
        if (StringUtils.isNumeric(procedureTypeNationalString)) {
            return Integer.parseInt(procedureTypeNationalString);
        } else {
            log.warn("Unable to parse procedure type number: " + procedureTypeNationalString);
            return null;
        }
    }

    public Optional<String> parseBillTextUrl(Element page) {
        return Optional.ofNullable(page.select("div.tab-content").first())
                .map(div -> div.select("div.tab-pane").last())
                .map(url -> url.attr("data-url"));
    }

    public Boolean parseOriginalLaw(String billTitle) {
        return !billTitle.toLowerCase().contains("amendment");
    }

    public LocalDate parseDateIntroduction(Element page) {
        Optional<String> dateIntroduction = Optional.ofNullable(page.select("div.NA, div.NCOP").first())
                .map(div -> div.select("div:contains(Bill introduced)").last())
                .map(Element::previousElementSibling)
                .map(Element::text);

        return dateIntroduction.map(DateUtils::parseSouthAfricaDate).orElse(null);
    }

    public List<LegislativeStage> parseLegislativeStages(Element page) {
        List<LegislativeStage> legislativeStages = new ArrayList<>();

        Optional<Element> billEvents = Optional.ofNullable(page.select("div.bill-events").first());

        if (billEvents.isPresent()) {
            Elements stageNames = billEvents.get().getElementsByTag("h4");

//        The last stage is the office of the president, it is not considered as a stage according to the annotation
            stageNames.remove(stageNames.select("h4:contains(Office of the President)").first());

            int nationalAssemblyIndex = 0;
            int nationalCouncilOfProvincesIndex = 0;

            for (Element stage : stageNames) {
                int stageNumber = stageNames.indexOf(stage);
                String stageName = stage.text();

                if (stageName.equals("National Assembly")) {
                    parseStageData(billEvents.get(), "NA", nationalAssemblyIndex, stageNumber, stageName)
                            .ifPresent(legislativeStages::add);
                    nationalAssemblyIndex++;
                } else if (stageName.equals("National Council of Provinces")) {
                    parseStageData(billEvents.get(), "NCOP", nationalCouncilOfProvincesIndex,
                                   stageNumber, stageName).ifPresent(legislativeStages::add);
                    nationalCouncilOfProvincesIndex++;
                }
            }
        }
        return legislativeStages;
    }

    private Optional<LegislativeStage> parseStageData(
            Element billEvents,
            String stageType,
            int index,
            int stageNumber,
            String stageName) {
        Element stageElement = billEvents.select("div." + stageType).get(index);
        Optional<Element> dateElement = Optional.ofNullable(stageElement
                                                                    .getElementsByClass("col-xs-4 col-md-2 text-muted")
                                                                    .first());

        if (dateElement.isPresent()) {
            String dateString = dateElement.get().text();
            LocalDate dateStage = parseSouthAfricaDate(dateString);

            int stageSize = parseStageSize(stageElement);

            LegislativeStage legislativeStage = new LegislativeStage(
                    stageNumber + 1,
                    dateStage,
                    stageName,
                    stageSize
            );
            return Optional.of(legislativeStage);
        }
        return Optional.empty();
    }

    private int parseStageSize(Element stageElement) {
        int stageSize = 0;
        Element page;

        List<String> stageUrls = stageElement.getElementsByClass("col-xs-8 col-md-10")
                .stream()
                .map(div -> div.getElementsByTag("a"))
                .map(Elements::first)
                .filter(Objects::nonNull)
                .map(a -> "https://pmg.org.za" + a.attr("href")).toList();

        for (String stageUrl : stageUrls) {
            downloadPage(stageUrl);

            page = getStageSizePageSource(stageUrl);

            String text = page.getElementsByTag("p").text();
            stageSize += TextUtils.getLengthWithoutWhitespace(text);
        }

        return stageSize;
    }

    private void downloadPage(String stageUrl) {
        saPageCollector.downloadPage(stageUrl, SaPageType.SPEECH_OR_REPORT);
    }

    private Element getStageSizePageSource(String stageUrl) {
        return Jsoup.parse(pageSourceRepository.getByPageUrl(stageUrl).getRawSource());
    }
}