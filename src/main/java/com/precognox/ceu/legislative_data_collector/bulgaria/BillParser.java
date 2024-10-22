package com.precognox.ceu.legislative_data_collector.bulgaria;

import com.google.common.net.HttpHeaders;
import com.precognox.ceu.legislative_data_collector.bulgaria.json.*;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.*;
import com.precognox.ceu.legislative_data_collector.entities.bg.BgCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.JsonUtils;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import one.util.streamex.StreamEx;
import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Nullable;
import javax.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.text.MessageFormat.format;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Slf4j
@Service
public class BillParser {

    @Autowired
    private PageSourceRepository pageSourceRepository;

    @Autowired
    private PrimaryKeyGeneratingRepository billRepository;

    @Autowired
    private ModifiedLawCollector modifiedLawCollector;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final String TRANSCRIPT_FILE_BASE_URL = "https://www.parliament.bg/";
    private static final String MP_API_ENDPOINT_TEMPLATE = "https://www.parliament.bg/api/v1/mp-profile/bg/{0}";
    private static final String LAW_TEXT_API_ENDPOINT_TEMPLATE = "https://www.parliament.bg/api/v1/act/{0}";
    private static final String LAW_TEXT_PAGE_TEMPLATE = "https://www.parliament.bg/bg/desision/ID/{0}";
    private static final String PLENARY_DEBATE_API_ENDPOINT_TEMPLATE = "https://www.parliament.bg/api/v1/pl-sten/{0}";
    private static final String BILL_PAGE_URL_TEMPLATE = "https://www.parliament.bg/bg/bills/ID/{0}";
    private static final String COMMITTEE_TRANSCRIPT_URL_TEMPLATE = "https://www.parliament.bg/api/v1/com-steno/bg/{}";

    private static final String FIRST_STAGE_NAME = "зала първо гласуване";
    private static final String SECOND_STAGE_NAME = "зала второ гласуване";
    private static final Map<String, Integer> STAGE_NUMBERS = Map.of(
            FIRST_STAGE_NAME, 1,
            SECOND_STAGE_NAME, 2
    );

    private static final String VOTING_EXCEL_SHEET_TITLE = "Гласуване по парламентарни групи";
    private static final String DATE_EXPRESSION_REGEX = "\\d{1,2} [а-я]+ \\d{4}";

    private static final Pattern DATE_PUBLICATION_REGEX =
            Pattern.compile("Издаден в София на (" + DATE_EXPRESSION_REGEX + ")");

    private static final Pattern DATE_ENTERING_FORCE_PHRASE =
            Pattern.compile("Законът влиза в сила от (" + DATE_EXPRESSION_REGEX + ")");

    private static final String DATE_ENTERING_FORCE_SAME_DAY_PHRASE = "Законът влиза в сила от деня на обнародването";

    private static final String UNIFIED_LAW_PATTERN = "Обединен в законопроект";
    private static final String REJECTED_STATUS_LABEL = "отхвърлен";

    private static final RestTemplate REST_TEMPLATE = new RestTemplate();

    @Transactional
    public void processStoredPages() {
        Stream<PageSource> storedApiResponses = pageSourceRepository.streamByCountryAndPageType(
                Country.BULGARIA, PageType.BILL_JSON.name()
        );

        storedApiResponses
                .filter(this::isNotProcessed)
                .map(this::parseStoredResponse)
                .forEach(billRepository::save);

        modifiedLawCollector.collectModifiedLaws();
    }

    private boolean isNotProcessed(PageSource source) {
        String apiUrl = source.getPageUrl();
        String billId = apiUrl.substring(apiUrl.lastIndexOf("/") + 1).trim();

        return !billRepository.existsByBillIdAndCountry(billId, Country.BULGARIA);
    }

    @Transactional
    public LegislativeDataRecord processSingleBill(String apiUrl) {
        return parseStoredResponse(pageSourceRepository.findByPageUrl(apiUrl).get());
    }

    private LegislativeDataRecord parseStoredResponse(PageSource pageSource) {
        log.info("Processing bill: {}", pageSource.getPageUrl());

        BillJson parsedApiResponse = JsonUtils.parseToObject(pageSource.getRawSource(), BillJson.class);

        LegislativeDataRecord record = new LegislativeDataRecord();
        record.setCountry(Country.BULGARIA);
        record.setBillTitle(parsedApiResponse.getBillTitle());
        record.setDatePassing(parseDate(parsedApiResponse.getDatePassing()));
        record.setDateIntroduction(parseDate(parsedApiResponse.getDateIntroduction()));
        record.setBillId(parsedApiResponse.getActId());
        record.setBillPageUrl(format(BILL_PAGE_URL_TEMPLATE, record.getBillId()));

        parseOriginalLaw(record);
        parseNumOfModifiedLaws(record);
        parseBillStatus(parsedApiResponse, record);
        parseLawTextVariables(parsedApiResponse, record);
        parseOriginators(parsedApiResponse, record);
        parseCommittees(parsedApiResponse, record);
        parseDateEnteringForce(record);

        List<PageSource> transcriptPages = parsedApiResponse.getPlenaryTranscripts().stream()
                .map(PlenaryTranscript::getTranscriptId)
                .distinct()
                .map(this::getTranscriptPage)
                .filter(Objects::nonNull)
                .toList();

        List<PlenaryDebateJson> parsedTranscriptJsons = transcriptPages.stream()
                .map(PageSource::getRawSource)
                .map(json -> JsonUtils.parseToObject(json, PlenaryDebateJson.class))
                .toList();

        if (!parsedTranscriptJsons.isEmpty()) {
            int plenarySize = parsedTranscriptJsons.stream()
                    .map(PlenaryDebateJson::getDebateText)
                    .map(TextUtils::removeHtml)
                    .mapToInt(TextUtils::getLengthWithoutWhitespace)
                    .sum();

            record.setPlenarySize(plenarySize);
        }

        parseStages(parsedApiResponse, record);

        List<PlenaryDebateJson> secondVoteDebateJsons = parsedApiResponse.getPlenaryTranscripts().stream()
                .filter(transcript -> "зала второ гласуване".equalsIgnoreCase(transcript.getStageName().trim()))
                .map(PlenaryTranscript::getTranscriptId)
                .distinct()
                .map(this::getTranscriptPage)
                .filter(Objects::nonNull)
                .map(PageSource::getRawSource)
                .map(json -> JsonUtils.parseToObject(json, PlenaryDebateJson.class))
                .toList();

        if (!secondVoteDebateJsons.isEmpty()) {
            Optional<String> votesExcelUrl = secondVoteDebateJsons.stream()
                    .map(PlenaryDebateJson::getFiles)
                    .flatMap(List::stream)
                    .filter(json -> VOTING_EXCEL_SHEET_TITLE.equals(json.getName()))
                    .map(PlenaryDebateJson.DebateFile::getFilePath)
                    .filter(path -> path.endsWith(".xlsx") || path.endsWith(".xls"))
                    .findFirst();

            votesExcelUrl.ifPresent(excelUrl -> parseVotesExcel(record, excelUrl));
        }

        parseCountrySpecificVars(parsedApiResponse, record);

        return record;
    }

    private void parseOriginalLaw(LegislativeDataRecord record) {
        record.setOriginalLaw(!isModifyingBill(record.getBillTitle()));
    }

    private void parseNumOfModifiedLaws(LegislativeDataRecord record) {
        int modLaws = record.getOriginalLaw() || record.getBillStatus() != LegislativeDataRecord.BillStatus.PASS ? 0 : 1;
        record.setModifiedLawsCount(modLaws);
    }

    @Nullable
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.startsWith("0")) {
            return null;
        }

        //format is 2018-03-19 00:00:00
        String cleanDateStr = dateStr.replace("00:00:00", "").trim();
        return LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse(cleanDateStr));
    }

    private void parseBillStatus(BillJson parsedApiResponse, LegislativeDataRecord record) {
        if (record.getDatePassing() != null && isNotEmpty(parsedApiResponse.getLawTitle())) {
            record.setBillStatus(LegislativeDataRecord.BillStatus.PASS);
        } else if (!parsedApiResponse.getActivities().isEmpty()) {
            Activity lastActivity = parsedApiResponse.getActivities().get(parsedApiResponse.getActivities().size() - 1);

            if (REJECTED_STATUS_LABEL.equals(lastActivity.getEventName())) {
                record.setBillStatus(LegislativeDataRecord.BillStatus.REJECT);
            } else {
                record.setBillStatus(LegislativeDataRecord.BillStatus.ONGOING);
            }
        }
    }

    private void parseLawTextVariables(BillJson parsedApiResponse, LegislativeDataRecord record) {
        getLawText(parsedApiResponse).ifPresent(record::setLawText);

        if (record.getLawText() != null) {
            record.setLawSize(TextUtils.getLengthWithoutWhitespace(record.getLawText()));
            record.setLawTextUrl(format(LAW_TEXT_PAGE_TEMPLATE, record.getBillId()));
        }
    }

    private Optional<String> getLawText(BillJson parsedApiResponse) {
        if (isNotEmpty(parsedApiResponse.getLawTitle())) {
            String url = format(LAW_TEXT_API_ENDPOINT_TEMPLATE, parsedApiResponse.getActId());
            ResponseEntity<String> response = REST_TEMPLATE.getForEntity(url, String.class);

            LawTextJson lawTextJson = JsonUtils.parseToObject(response.getBody(), LawTextJson.class);

            if (isNotEmpty(lawTextJson.getLawText())) {
                return Optional.of(TextUtils.removeHtml(lawTextJson.getLawText()));
            }
        }

        return Optional.empty();
    }

    private void parseOriginators(BillJson apiResponse, LegislativeDataRecord record) {
        if (!apiResponse.getGovOriginators().isEmpty()) {
            record.setOriginType(OriginType.GOVERNMENT);
        } else if (!apiResponse.getOriginators().isEmpty()) {
            OriginType originType =
                    apiResponse.getOriginators().size() > 1 ? OriginType.GROUP_MP : OriginType.INDIVIDUAL_MP;

            record.setOriginType(originType);

            List<Originator> originators = apiResponse.getOriginators().stream()
                    .map(this::buildOriginator)
                    .toList();

            record.setOriginators(originators);
        }
    }

    private Originator buildOriginator(com.precognox.ceu.legislative_data_collector.bulgaria.json.Originator dto) {
        String name = String.join(" ", dto.getMpName1(), dto.getMpName2(), dto.getMpName3());

        String mpApiUrl = format(MP_API_ENDPOINT_TEMPLATE, dto.getMpId().toString());

        PageSource storedMpJson = pageSourceRepository.findByPageUrl(mpApiUrl).orElseGet(
                () -> downloadOriginatorJson(mpApiUrl)
        );

        if (storedMpJson != null) {
            MpJson parsedJson = JsonUtils.parseToObject(storedMpJson.getRawSource(), MpJson.class);
            String aff = parsedJson.getAffiliation();

            return new Originator(name, aff);
        }

        return new Originator(name);
    }

    private PageSource downloadOriginatorJson(String mpApiUrl) {
        ResponseEntity<String> getMpResponse = REST_TEMPLATE.getForEntity(mpApiUrl, String.class);

        if (getMpResponse.getStatusCode().is2xxSuccessful()) {
            PageSource entity = PageSource.builder()
                    .country(Country.BULGARIA)
                    .pageType(PageType.MP_PAGE_JSON.name())
                    .pageUrl(mpApiUrl)
                    .rawSource(getMpResponse.getBody())
                    .build();

            transactionTemplate.execute(status -> pageSourceRepository.save(entity));

            return entity;
        }

        log.error(
                "Error returned from API: {} - {}, on URL: {}",
                getMpResponse.getStatusCodeValue(),
                getMpResponse.getBody(), mpApiUrl
        );

        return null;
    }

    private void parseCommittees(BillJson apiResponse, LegislativeDataRecord record) {
        List<Committee> committees = apiResponse.getCommittees()
                .stream()
                .map(dto -> new Committee(dto.getCommitteeName(), dto.getCommitteeRole()))
                .toList();

        record.setCommittees(committees);
        record.setCommitteeCount(committees.size());

        long committeesHearings = StreamEx.of(apiResponse.getReports())
                .append(apiResponse.getReports2())
                .append(apiResponse.getReports2_1())
                .filter(act -> isNotEmpty(act.getCommitteeName()))
                .count();

        if (committeesHearings > 0) {
            record.setCommitteeHearingCount(Math.toIntExact(committeesHearings));
        }

        Stream<String> committeeDatesFromReports = apiResponse.getReports()
                .stream()
                .filter(report -> isNotEmpty(report.getCommitteeName()))
                .map(Report::getDate);

        Stream<String> committeeDatesFromActivities = apiResponse.getActivities()
                .stream()
                .filter(activity -> isNotEmpty(activity.getCommitteeName()))
                .map(Activity::getDate);

        Optional<LocalDate> earliest = Stream.concat(committeeDatesFromReports, committeeDatesFromActivities)
                .map(this::parseDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder());

        earliest.ifPresent(record::setCommitteeDate);
    }

    private void parseDateEnteringForce(LegislativeDataRecord record) {
        if (record.getLawText() != null) {
            Optional<LocalDate> pubDate = DATE_PUBLICATION_REGEX.matcher(record.getLawText()).results()
                    .map(result -> result.group(1))
                    .map(this::parseDateExpr)
                    .findFirst();

            if (record.getLawText().contains(DATE_ENTERING_FORCE_SAME_DAY_PHRASE) && pubDate.isPresent()) {
                record.setDateEnteringIntoForce(pubDate.get());
            } else {
                Matcher matcher = DATE_ENTERING_FORCE_PHRASE.matcher(record.getLawText());

                if (matcher.find()) {
                    String dateExpr = matcher.group(1);
                    record.setDateEnteringIntoForce(parseDateExpr(dateExpr));
                } else pubDate.ifPresent(localDate -> record.setDateEnteringIntoForce(localDate.plusDays(3)));
            }
        }
    }

    private LocalDate parseDateExpr(String dateExpr) {
        String[] parts = dateExpr.split(" ");

        int day = Integer.parseInt(parts[0]);
        int month = Constants.MONTH_TRANSLATIONS.get(parts[1]);
        int year = Integer.parseInt(parts[2]);

        return LocalDate.of(year, month, day);
    }

    private void parseStages(BillJson billJson, LegislativeDataRecord record) {
        Map<String, List<Activity>> groups =
                billJson.getActivities().stream()
                        .filter(activity -> STAGE_NUMBERS.containsKey(activity.getStageName()))
                        .collect(Collectors.groupingBy(Activity::getStageName));

        List<LegislativeStage> legislativeStages = groups.entrySet().stream()
                .map(group -> mapToStageEntity(group.getKey(), group.getValue()))
                .toList();

        if (!legislativeStages.isEmpty()) {
            record.setStages(legislativeStages);
            record.setStagesCount(legislativeStages.size());
        }
    }

    private LegislativeStage mapToStageEntity(String stageName, List<Activity> activities) {
        Optional<LocalDate> stageStartDate = activities.stream()
                .map(Activity::getDate)
                .map(this::parseDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder());

        String eventList = activities.stream()
                .map(Activity::getEventName)
                .collect(Collectors.joining(", "));

        String longName = format("{0} ({1})", eventList, stageName);

        int debateSize = activities.stream()
                .map(Activity::getPlenarySessionId)
                .filter(Objects::nonNull)
                .distinct()
                .mapToInt(this::getDebateSize)
                .sum();

        LegislativeStage entity = new LegislativeStage();
        entity.setStageNumber(STAGE_NUMBERS.get(stageName));
        entity.setName(longName);
        entity.setDate(stageStartDate.get());
        entity.setDebateSize(debateSize);

        return entity;
    }

    private int getDebateSize(String plenarySessionId) {
        if (isNotEmpty(plenarySessionId) && !"0".equals(plenarySessionId)) {
            PageSource transcriptPageJson = getTranscriptPage(plenarySessionId);

            if (transcriptPageJson != null) {
                PlenaryDebateJson plenaryDebateJson = JsonUtils.parseToObject(
                        transcriptPageJson.getRawSource(), PlenaryDebateJson.class
                );

                return TextUtils.getLengthWithoutWhitespace(TextUtils.removeHtml(plenaryDebateJson.getDebateText()));
            }
        }

        return 0;
    }

    @Nullable
    private PageSource getTranscriptPage(String transcriptId) {
        if (!"1".equals(transcriptId)) {
            String url = format(PLENARY_DEBATE_API_ENDPOINT_TEMPLATE, transcriptId);
            return pageSourceRepository.findByPageUrl(url).orElseGet(() -> downloadTranscriptPage(url));
        }

        return null;
    }

    @Nullable
    private PageSource downloadTranscriptPage(String url) {
        try {
            ResponseEntity<String> resp = REST_TEMPLATE.getForEntity(url, String.class);
            String contentType = resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);

            if (resp.getStatusCode().is2xxSuccessful() && "application/json".equals(contentType)) {
                PageSource storedPage = PageSource.builder()
                        .country(Country.BULGARIA)
                        .pageUrl(url)
                        .pageType(PageType.PLENARY_TRANSCRIPT_JSON.name())
                        .rawSource(resp.getBody())
                        .build();

                transactionTemplate.execute(status -> pageSourceRepository.save(storedPage));

                return storedPage;
            }

            log.warn("Invalid response from API: {} - {} for URL: {}", resp.getStatusCodeValue(), contentType, url);
        } catch (RestClientException e) {
            log.error("Error getting URL: " + url);
            log.error(e.toString());
        }

        return null;
    }

    private void parseCountrySpecificVars(BillJson billJson, LegislativeDataRecord record) {
        BgCountrySpecificVariables specVars = new BgCountrySpecificVariables();
        specVars.setLegislativeDataRecord(record);
        specVars.setUnifiedLaw(false);
        record.setBgSpecificVariables(specVars);

        if (billJson.getGazetteNumber() != null && billJson.getGazetteNumberYear() != null) {
            String gazetteNumber = billJson.getGazetteNumber() + "/" + billJson.getGazetteNumberYear();

            if (!"0/0".equals(gazetteNumber)) {
                specVars.setGazetteNumber(gazetteNumber);
            }
        }

        List<BillJson.RelatedBillListItem> relatedBillList = null;

        if (isNotEmpty(billJson.getTransLabel()) && billJson.getTransLabel().startsWith(UNIFIED_LAW_PATTERN)) {
            relatedBillList = billJson.getTransList();
        } else if (billJson.getUnionList() != null && !billJson.getUnionList().isEmpty()) {
            relatedBillList = billJson.getUnionList();
        }

        if (relatedBillList != null) {
            List<String> relatedBillIds = relatedBillList
                    .stream()
                    .map(BillJson.RelatedBillListItem::getBillId)
                    .map(Object::toString)
                    .toList();

            specVars.setUnifiedLaw(true);
            specVars.setUnifiedLawReferences(String.join(", ", relatedBillIds));
        }
    }

    private void parseVotesExcel(LegislativeDataRecord record, String excelUrl) {
        if (record.getFinalVoteFor() != null) {
            throw new IllegalStateException("Record already has votes");
        }

        ResponseEntity<byte[]> xlsResp = REST_TEMPLATE.getForEntity(TRANSCRIPT_FILE_BASE_URL + excelUrl, byte[].class);

        if (xlsResp.getStatusCode().is2xxSuccessful()) {
            try (Workbook workbook = excelUrl.endsWith(".xlsx")
                    ? new XSSFWorkbook(new ByteArrayInputStream(xlsResp.getBody()))
                    : new HSSFWorkbook(new ByteArrayInputStream(xlsResp.getBody()))) {
                Iterator<Sheet> sheetIterator = workbook.sheetIterator();

                if (sheetIterator.hasNext()) {
                    Sheet sheet = sheetIterator.next();
                    Row votesRow = findRowWithVotes(record, sheet);

                    if (votesRow != null) {
                        record.setFinalVoteFor(cleanAndParse(votesRow.getCell(1)));
                        record.setFinalVoteAgainst(cleanAndParse(votesRow.getCell(2)));
                        record.setFinalVoteAbst(cleanAndParse(votesRow.getCell(3)));
                    }
                }
            } catch (IOException | UnsupportedFileFormatException e) {
                log.error("Failed to process excel file: " + excelUrl, e);
            }
        } else {
            log.warn("Error response for URL: {} - {}", excelUrl, xlsResp.getStatusCodeValue());
        }
    }

    private static Integer cleanAndParse(Cell votesCell) {
        return switch (votesCell.getCellType()) {
            case NUMERIC -> Double.valueOf(votesCell.getNumericCellValue()).intValue();
            case STRING -> Integer.valueOf(votesCell.getStringCellValue().replaceAll("\\D", ""));
            default -> throw new IllegalStateException("Unexpected value: " + votesCell.getCellType());
        };
    }

    @Nullable
    private Row findRowWithVotes(LegislativeDataRecord record, Sheet sheet) {
        int lastRowNum = sheet.getLastRowNum();

        int bestFitRowIndex = -1;
        int bestFitRowRatio = 0;

        for (int rowIndex = 0; rowIndex <= lastRowNum; rowIndex++) {
            Row currentRow = sheet.getRow(rowIndex);

            if (currentRow != null && currentRow.getLastCellNum() > 0) {
                String firstCellText = currentRow.getCell(0).getStringCellValue().replaceAll("\\s+", " ");

                if (firstCellText.contains("второ гласувание") || firstCellText.contains("второ гласуване")) {
                    int fuzzyMatchRatio = FuzzySearch.ratio(firstCellText, record.getBillTitle());

                    if (fuzzyMatchRatio > bestFitRowRatio) {
                        bestFitRowRatio = fuzzyMatchRatio;
                        bestFitRowIndex = rowIndex;
                    }
                }
            }
        }

        Row votesRow = null;

        if (bestFitRowRatio > 40) {
            votesRow = sheet.getRow(bestFitRowIndex + 2);
        }

        return votesRow;
    }

    private boolean isModifyingBill(String billTitle) {
        return Constants.MODIFYING_LAW_PATTERNS.stream().anyMatch(billTitle::startsWith);
    }
}
