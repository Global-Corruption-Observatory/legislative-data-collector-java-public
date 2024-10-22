package com.precognox.ceu.legislative_data_collector.chile.recordbuilders;

import com.precognox.ceu.legislative_data_collector.chile.tableparsers.CommitteePageParser;
import com.precognox.ceu.legislative_data_collector.chile.tableparsers.LegislativeStagePageParser;
import com.precognox.ceu.legislative_data_collector.chile.tableparsers.OriginatorsPageParser;
import com.precognox.ceu.legislative_data_collector.chile.tableparsers.ProcedurePageParser;
import com.precognox.ceu.legislative_data_collector.chile.utils.OriginalLawCalculator;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.exceptions.NotInDatabaseException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.precognox.ceu.legislative_data_collector.chile.recordbuilders.LawRecordBuilder.LAW_ID_FORMATTER;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.BILL_ORIGINATOR_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.COMMITTEES_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.LEGISLATIVE_STAGES_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.PROCEDURE_TYPE_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_BILL_ORIGINATORS;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_BILL_PROCEDURE;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_COMMITTEES;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_LEGISLATIVE_STAGES;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.TEXT_TYPE_BILL_TEXT;

@Slf4j
public class BillRecordBuilder extends RecordBuilder {

    public final static Set<String> UNHANDLED_BILL_STATUSES = new HashSet<>();
    private final static Pattern LAW_ID_ON_BILL_PAGE_REGEX = Pattern.compile("^Ley\\D*([\\d.]+)|^N\\D*([\\d.]+)|^([\\d.]+)");
    private final static int LAW_ID_REGEX_OPTION_COUNT = 3;
    //The variation of D.S.N. also links to 'Decreto's
    private final static Pattern DECRETO_REGEX = Pattern.compile("(decreto|^D\\W*S\\W*N)", Pattern.CASE_INSENSITIVE);
    private final static Pattern BILL_ID_URL_EXTRACT_REGEX = Pattern.compile("nboletin=([\\d-]+)");
    private final static String BILL_PAGE_URL_FORMATTER = "http://www.senado.cl/appsenado/templates/tramitacion/index.php?boletin_ini=%s";
    private final static String STATUS_TEXT_MAIN_KEY = "main";
    private final static String STATUS_TEXT_SECONDARY_KEY = "secondary";
    private final static Pattern BILL_ID_EXTRACT_REGEX = Pattern.compile("([\\d-]+)");
    private final static Pattern PROY_ID_EXTRACT_REGEX = Pattern.compile("proyid=(\\d+)");
    private final static Pattern BILL_TEXT_START_REGEX = Pattern.compile("(?:[aA]rt[.]?|art\\S*?c*u*l[o0])[\\s ]*(?:1\\D|[lL][.º]|[uUúÚ]́*nico|[pP]rimero|uno)", Pattern.CASE_INSENSITIVE); //There is an accent character before the star which is after the U in unico
    private final static Pattern BILL_TEXT_VALIDATION_HAS_LETTER_REGEX = Pattern.compile("[a-zA-Z]");
    private final static List<Pattern> BILL_TEXT_VALIDATION_DIGITIZATION_PENDING_REGEXES = List.of(
            Pattern.compile("(?:documentaci.n|digitaci.n)\\s*pendiente", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pendiente\\s*su\\s*digitaci.n", Pattern.CASE_INSENSITIVE),
            Pattern.compile("en\\s*digitación", Pattern.CASE_INSENSITIVE)
    );
    private final static Boolean FROM_TEXT_NODE = true;
    private final static Boolean FROM_DATA_NODE = false;

    @Override
    protected void buildRecord(
            PageSource pageSource, Optional<LegislativeDataRecord> recordOpt) throws DataCollectionException {
        Document page = Jsoup.parse(pageSource.getRawSource());
        Map<String, Optional<String>> statusTexts = getStatusTexts(page);
        Optional<String> lawIdOpt = statusTexts.get(STATUS_TEXT_SECONDARY_KEY);
        if (lawIdOpt.isPresent() && DECRETO_REGEX.matcher(lawIdOpt.get()).find()) {
            throw new DataCollectionException("Collection of this bill is not required as its law id contains the word Decreto");
        }
        record = recordOpt.orElseGet(() -> getRecordIfLawExists(lawIdOpt));
        record.setCountry(Country.CHILE);

        setUpCollections();

        try {
            String url = getTextFromPageByXpath(page, "/html/body/div/div[1]/div[1]/table/tbody/tr[7]/td[2]");
            record.setBillPageUrl(url);
        } catch (DataCollectionException ex) {
            record.setBillPageUrl(pageSource.getPageUrl());
        }

        try {
            LegislativeDataRecord.BillStatus status = getBillStatusFromPage(statusTexts);
            if (status.equals(LegislativeDataRecord.BillStatus.ONGOING) && isArchived(page)) {
                record.setBillStatus(LegislativeDataRecord.BillStatus.REJECT);
            } else {
                record.setBillStatus(status);
            }
        } catch (DataCollectionException ex) {
            log.error(ex.getMessage());
        }

        try {
            String billId = getTextFromPageByXpathWithRegex(page, "/html/body/div/div[1]/table/tbody/tr/td[1]", BILL_ID_EXTRACT_REGEX, FROM_TEXT_NODE);
            record.setBillId(billId);
        } catch (DataCollectionException ex) {
            log.error(String.format("Bill id: %s", ex.getMessage()));
        }

        try {
            String title = getTextFromPageByXpath(page, "/html/body/div/div[1]/div[1]/table/tbody/tr[1]/td[2]");
            record.setBillTitle(title);
            new OriginalLawCalculator().fillOriginalLaw(record);
        } catch (DataCollectionException ex) {
            log.error(String.format("Bill title: %s", ex.getMessage()));
        }

        try {
            String type = getTextFromPageByXpath(page, "/html/body/div/div[1]/div[1]/table/tbody/tr[4]/td[2]");
            getCountryVariables().setBillTypeChile(type);
        } catch (DataCollectionException ex) {
            log.error(String.format("Bill type chile: %s", ex.getMessage()));
        }

        if (record.getChileCountrySpecificVariables().getBillTypeChile().trim().equalsIgnoreCase("Reforma constitucional")) {
            throw new DataCollectionException(
                    "Collection of this bill is not required as its type is Reforma constitucional");
        }

        String proyId;
        try {
            proyId = getTextFromPageByXpathWithRegex(page, "/html/head/script", PROY_ID_EXTRACT_REGEX, FROM_DATA_NODE);
        } catch (DataCollectionException ex) {
            //Maybe allow to return the record with the values that could be set and not throw this exception
            throw new DataCollectionException(String.format("%s. Cannot continue scraping as proyId is required to identify the rest of the pages belonging to this record. {url %s}", ex.getMessage(), pageSource.getPageUrl()));
        }

        try {
            Document legislativeStagesPage = getPageForBillFromDB(proyId, LEGISLATIVE_STAGES_API, PAGE_TYPE_LEGISLATIVE_STAGES);
            LegislativeStagePageParser legStageParser = new LegislativeStagePageParser(legislativeStagesPage);

            List<LegislativeStage> legislativeStages = legStageParser.parseLegislativeStages();
            record.setStages(legislativeStages);
            record.setStagesCount(legislativeStages.size());

            //Has its own try catch to not block the other variables, if this not exist
            try {
                LocalDate dateOfIntroduction = legStageParser.getDateOfIntroduction();
                record.setDateIntroduction(dateOfIntroduction);
            } catch (DataCollectionException ex) {
                log.error(ex.getMessage());
            }

            Optional<PageSource> billTextSource = legStageParser.getBillTextUrl().flatMap(
                    url -> readService.findByPageTypeAndPageUrl(TEXT_TYPE_BILL_TEXT, url)
            );

            billTextSource.ifPresent(source -> {
                record.setBillTextUrl(source.getPageUrl());
                String wholeText = source.getRawSource();
                Matcher firstArticle = BILL_TEXT_START_REGEX.matcher(wholeText);

                if (firstArticle.find()) {
                    String billText = wholeText.substring(firstArticle.start());
                    record.setBillText(billText.trim());
                    getCountryVariables().setBillTextError(false);
                } else {
                    log.warn("Couldn't find the first article of the bill text in {} [id: {}]", source.getPageUrl(), source.getId());
                    getCountryVariables().setBillTextError(true);
                    if (isCorrectBillText(wholeText)) {
                        record.setBillText(wholeText.trim());
                    }
                }
            });
        } catch (NotInDatabaseException ex) {
            log.error("Legislative stages page not saved! Cannot collect information about legislative stages or bill text!");
        } catch (DataCollectionException ex) {
            record.setStagesCount(0);
            log.error(ex.getMessage());
        }

        try {
            Document committeesPage = getPageForBillFromDB(proyId, COMMITTEES_API, PAGE_TYPE_COMMITTEES);
            CommitteePageParser committeePageParser = new CommitteePageParser(committeesPage);
            List<Committee> committees = committeePageParser.getCommittees();

            //avoid UnsupportedOperationEx when changing the list later
            record.setCommittees(new ArrayList<>(committees));
            record.setCommitteeCount(committees.size());
            record.setCommitteeHearingCount(committeePageParser.getHearingCount());
        } catch (NotInDatabaseException ex) {
            log.error("Committees page not saved!");
        } catch (DataCollectionException ex) {
            record.setCommitteeCount(0);
            log.error(ex.getMessage());
        }

        //originators preferably set from the law page, only if there is no law page will be set from here (As no originator on the law page means government origin, it should not be overwritten from here)
        if (!record.getBillStatus().equals(LegislativeDataRecord.BillStatus.PASS)) {
            try {
                Document billOriginatorsPage = getPageForBillFromDB(proyId, BILL_ORIGINATOR_API, PAGE_TYPE_BILL_ORIGINATORS);
                List<Originator> originators = new OriginatorsPageParser(billOriginatorsPage).getOriginators();
                record.setOriginators(originators);

                if (!originators.isEmpty() && originators.stream().noneMatch(
                        orig -> orig.getName().toLowerCase().contains("ministerio"))) {
                    record.setOriginType(OriginType.INDIVIDUAL_MP);
                }
            } catch (NotInDatabaseException ex) {
                log.error("Bill originators page not saved");
            } catch (DataCollectionException ex) {
                log.error(ex.getMessage());
            }
        }

        try {
            Document procedurePage = getPageForBillFromDB(proyId, PROCEDURE_TYPE_API, PAGE_TYPE_BILL_PROCEDURE);
            LegislativeDataRecord.ProcedureType procedureType = new ProcedurePageParser(procedurePage).getProcedureType();
            record.setProcedureTypeStandard(procedureType);
            String nationalProcedureType = (procedureType.equals(LegislativeDataRecord.ProcedureType.EXCEPTIONAL)) ? "Excepcional (Urgencia)" : "Estándar";
            record.setProcedureTypeNational(nationalProcedureType);
        } catch (NotInDatabaseException ex) {
            log.error("Procedure type page not saved");
        } catch (DataCollectionException ex) {
            log.error(ex.getMessage());
        }
    }

    @Override
    protected Optional<LegislativeDataRecord> getRecordForSource(PageSource source) {
        Matcher matcher = BILL_ID_URL_EXTRACT_REGEX.matcher(source.getPageUrl());

        if (matcher.find()) {
            String url = String.format(BILL_PAGE_URL_FORMATTER, matcher.group(1));
            return readService.findByBillPageUrl(url);
        }

        return Optional.empty();
    }

    //The first is the real status text but it sometimes not enough to determine the status (usually if it is Tramitación Terminada. According to the annotation it is PASSED but it often appears with REJECTED law labels)
    //So the second needs to be checked whether it is a REJECTED status text or not. Also it can contain the law number.
    private Map<String, Optional<String>> getStatusTexts(Document page) {
        Element statusElement = page.selectXpath("/html/body/div/div[1]/div[1]/table/tbody/tr[5]/td[2]").first();
        Element moreInformation = page.selectXpath("/html/body/div/div[1]/div[1]/table/tbody/tr[6]/td[2]").first();
        Optional<String> mainText = (Objects.nonNull(statusElement)) ? Optional.of(statusElement.text().trim()) : Optional.empty();
        Optional<String> secondaryText = (Objects.nonNull(moreInformation)) ? Optional.of(moreInformation.text().trim()) : Optional.empty();
        return Map.of(STATUS_TEXT_MAIN_KEY, mainText,
                STATUS_TEXT_SECONDARY_KEY, secondaryText);
    }

    private LegislativeDataRecord getRecordIfLawExists(Optional<String> possibleLawId) {
        if (possibleLawId.isPresent()) {
            String lawIdText = possibleLawId.get();
            Matcher lawIdMatcher = LAW_ID_ON_BILL_PAGE_REGEX.matcher(lawIdText);
            if (lawIdMatcher.find()) {
                Optional<String> idNumOpt = Optional.empty();
                for (int i = 1; i <= LAW_ID_REGEX_OPTION_COUNT; i++) {
                    idNumOpt = Optional.ofNullable(lawIdMatcher.group(i));
                    if (idNumOpt.isPresent()) {
                        break;
                    }
                }
                String idNum = idNumOpt.get().replaceAll("\\.", ""); //The matcher found a match, so one of the 3 groups must exits
                String lawId = String.format(LAW_ID_FORMATTER, idNum);
                Optional<LegislativeDataRecord> record = readService.findRecordByLawId(lawId);
                return record.orElseGet(() -> {
                    LegislativeDataRecord newRecord = new LegislativeDataRecord();
                    newRecord.setLawId(lawId);
                    return newRecord;
                });
            }
        }
        return new LegislativeDataRecord();
    }

    private LegislativeDataRecord.BillStatus getBillStatusFromPage(Map<String, Optional<String>> texts) throws DataCollectionException {
        if (Objects.nonNull(record.getLawId())) {
            return LegislativeDataRecord.BillStatus.PASS;
        }
        if (texts.get(STATUS_TEXT_MAIN_KEY).isPresent()) {
            String mainStatusText = texts.get(STATUS_TEXT_MAIN_KEY).get();
            if (mainStatusText.equalsIgnoreCase("Tramitación terminada")) {
                if (texts.get(STATUS_TEXT_SECONDARY_KEY).isPresent()) {
                    String moreInformationText = texts.get(STATUS_TEXT_SECONDARY_KEY).get();
                    if (LAW_ID_ON_BILL_PAGE_REGEX.matcher(moreInformationText).find()) {
                        return LegislativeDataRecord.BillStatus.PASS;
                    } else {
                        return determineBillStatus(moreInformationText);
                    }
                } else {
                    //If no other information it may be considered PASSED as default
                    throw new DataCollectionException("Bill status cannot be determined as \"Tramitación terminada\" can belong to multiple options and there is no more information about it");
                }
            } else {
                return determineBillStatus(mainStatusText);
            }
        } else {
            throw new DataCollectionException("No Bill status element found");
        }
    }

    private LegislativeDataRecord.BillStatus determineBillStatus(String statusText) {
        return switch (statusText.toLowerCase().trim()) {
            case "publicado" -> LegislativeDataRecord.BillStatus.PASS;
            case "archivado",
                    "retirado",
                    "inadmisible",
                    "rechazado" -> LegislativeDataRecord.BillStatus.REJECT;
            default -> {
                UNHANDLED_BILL_STATUSES.add(statusText);
                yield LegislativeDataRecord.BillStatus.ONGOING;
            }
        };
    }

    private boolean isArchived(Document page) {
        try {
            String refundidoText = getTextFromPageByXpath(page, "/html/body/div/div[1]/div[1]/table/tbody/tr[4]/td[4]");
            return !refundidoText.isBlank();
        } catch (DataCollectionException e) {
            return false;
        }
    }

    private String getTextFromPageByXpathWithRegex(Document page, String xpath, Pattern regex, boolean fromTextNode) throws DataCollectionException {
        Element element = page.selectXpath(xpath).first();
        if (Objects.isNull(element)) {
            throw new DataCollectionException(String.format("Couldn't find element on page by xpath %s", xpath));
        }
        String text = fromTextNode ? element.text().trim() : element.data().trim();
        Matcher matcher = regex.matcher(text);
        //If regex contains group it returns only the grouped part otherwise the whole match
        if (matcher.find()) {
            try {
                return matcher.group(1);
            } catch (IndexOutOfBoundsException ex) {
                return matcher.group();
            }
        } else {
            throw new DataCollectionException("Required part not found in text");
        }
    }

    private String getTextFromPageByXpath(Document page, String xpath) throws DataCollectionException {
        Element element = page.selectXpath(xpath).first();
        if (Objects.isNull(element)) {
            throw new DataCollectionException("Element not found on the page");
        }
        return element.text().trim();
    }

    private Document getPageForBillFromDB(String proyid, String API, String type) throws NotInDatabaseException {
        String url = String.format(API, proyid);
        return readService.findByPageTypeAndPageUrl(type, url)
                .map(PageSource::getRawSource)
                .map(Jsoup::parse)
                .orElseThrow(() -> new NotInDatabaseException("Page not found in the database"));
    }

    //If the required part not found in the bill text, the whole text should be included, but some general problems which lead to some probably not wanted texts (like blank) so these will not be saved (Their download url will be though)
    private boolean isCorrectBillText(String text) {
        if (text.isBlank()) {
            log.warn("Bill text is blank!");
            return false;
        }
        //There are PDFs where only the date from the stampes are read. This filters out those ones.
        if (!BILL_TEXT_VALIDATION_HAS_LETTER_REGEX.matcher(text).find()) {
            log.warn("Bill text couldn't be extracted properly");
            return false;
        }
        //These are words with text like digitación pendiente, which to me means they are as good as blank texts, so they are filtered out as well
        boolean underDigitization = BILL_TEXT_VALIDATION_DIGITIZATION_PENDING_REGEXES
                .stream()
                .map(regex -> regex.matcher(text))
                .anyMatch(Matcher::find);

        if (underDigitization) {
            log.warn("Bill text not digitized.");
            return false;
        }
        return true;
    }
}
