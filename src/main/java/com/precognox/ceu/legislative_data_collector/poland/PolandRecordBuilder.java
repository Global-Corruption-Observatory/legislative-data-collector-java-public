package com.precognox.ceu.legislative_data_collector.poland;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.poland.constants.PageType;
import com.precognox.ceu.legislative_data_collector.poland.constants.PolishTranslations;
import com.precognox.ceu.legislative_data_collector.poland.json.BillJson;
import com.precognox.ceu.legislative_data_collector.poland.json.MpJson;
import com.precognox.ceu.legislative_data_collector.poland.parsers.PolandBillApiDataParser;
import com.precognox.ceu.legislative_data_collector.poland.parsers.PolandCommitteesTableParser;
import com.precognox.ceu.legislative_data_collector.poland.parsers.PolandImpactAssessmentTableParser;
import com.precognox.ceu.legislative_data_collector.poland.parsers.PolandProcessApiDataParser;
import com.precognox.ceu.legislative_data_collector.poland.parsers.PolandProcessTableParser;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * This class is responsible for the record-building from five different page-sources, in consecutive steps.
 * The starting point is the bill-JSON (processed with PolandBillApiDataParser) which contains the relating process-JSON
 * URL (processed with PolandProcessApiDataParser) and the legislative process webpage's URL (processed with
 * PolandProcessTableParser).
 * From bill-JSON we get the term number and the process number as well. With them the committee-table's webpage
 * (processed with PolandCommitteesTableParser) and the impact assessment's webpage (processed with
 * PolandCommitteesTableParser) could be reached.
 */
@Slf4j
@Service
public class PolandRecordBuilder {

    private static final String COMMITTEE_TABLE_URL_TEMPLATE =
            "https://www.sejm.gov.pl/SQL2.nsf/poskomprocall?OpenAgent&%s&%s";
    private static final String IMPACT_ASSESSMENT_URL_TEMPLATE_TERM3_TO_TERM6 =
            "https://orka.sejm.gov.pl/rexdomk%s.nsf/Opdodr?OpenPage&nr=%s";
    private static final String IMPACT_ASSESSMENT_URL_TEMPLATE_TERM7_TO_TERM9 =
            "https://www.sejm.gov.pl/Sejm%s.nsf/opinieBAS.xsp?nr=%s";
    private static final String REGEX_FOR_GOVERNMENT_ORIGINATOR =
            "(?m)(?!§[ ]*\\d+[.][ ]*)((?:Minister[ \\n][A-ZŻŹĆĄŚĘŁÓŃ][^.]+)|(?:Minister(?: \\n)[A-ZŻŹĆĄŚĘŁÓŃ][^.]+)|(?:Prezes Rady[ \\n]*Ministrów)|(?:minister[ \\n][A-ZŻŹĆĄŚĘŁÓŃ][^.]+)|(?:Prezes Urzędu[ \\n][A-ZŻŹĆĄŚĘŁÓŃ][^.]+)|(?:Minister[ \\n-][a-zżźćńółęąśA-ZŻŹĆĄŚĘŁÓŃ][^.]+))(?:(?:(?:(?:[.]?[ ]*[\\n][\\s]*[Zz][ ](?:[\\p{L}]+[ ])?(?:szac|pow|g| )))|(?:(?:[.]?[ \\n]*[(\\/][-][)\\/][ ]))|(?:[.]?[\\n][ ]*(?:[Ww]z.))|(?:[.]?[\\n][ ]*(?:z up.))))";
    private static final String REGEX_FOR_PARLIAMENT_ORIGINATOR = "(?sm)[(][-][)]([^;.]+)[;.]?";
    private static final String REGEX_FOR_SENATE_PRESIDENT_AND_CIVIC_ORIGINATORS = "(?sm)[(][-][)]([^\\n;.]+)[\\n]?";
    private static final String REGEX_FOR_OTHER_ORIGINATOR = "(?sm)[(\\/][-][)\\/]([^\\n;.\\/]+)[\\n]?";
    private static final String N_A = "N/A";
    private static final String PRESIDENT_AFFILIATION = "Prezydent Rzeczypospolitej Polskiej";
    private static final String BILL_TEXT_URL_BASE_URL = "https://orka.sejm.gov.pl";
    private static final Pattern BILL_TEXT_PDF_URL_OLD_REGEX = Pattern.compile("^.*(Druki[456]).*$");
    private static final Pattern BILL_TEXT_PDF_URL_NEW_REGEX = Pattern.compile("^.*(Sejm[789]).*$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PageSourceRepository pageSourceRepository;
    private final PrimaryKeyGeneratingRepository recordRepository;
    private final PolandDataCollector polandDataCollector;
    private final PolandProcessApiDataParser processApiDataParser;
    private final PolandBillApiDataParser billApiDataParser;
    private final PolandProcessTableParser processTableParser;
    private final PolandCommitteesTableParser committeesTableParser;
    private final PolandImpactAssessmentTableParser impactAssessmentTableParser;
    private final PdfParser pdfParser;

    @Autowired
    public PolandRecordBuilder(PageSourceRepository pageSourceRepository,
                               PrimaryKeyGeneratingRepository recordRepository,
                               PolandDataCollector polandDataCollector,
                               PolandProcessApiDataParser processApiDataParser,
                               PolandBillApiDataParser billApiDataParser,
                               PolandProcessTableParser processTableParser,
                               PolandCommitteesTableParser committeesTableParser,
                               PolandImpactAssessmentTableParser impactAssessmentTableParser,
                               PdfParser pdfParser
    ) {
        this.pageSourceRepository = pageSourceRepository;
        this.recordRepository = recordRepository;
        this.polandDataCollector = polandDataCollector;
        this.processApiDataParser = processApiDataParser;
        this.billApiDataParser = billApiDataParser;
        this.processTableParser = processTableParser;
        this.committeesTableParser = committeesTableParser;
        this.impactAssessmentTableParser = impactAssessmentTableParser;
        this.pdfParser = pdfParser;
    }

    // for testing purposes
    public void buildOneDataRecord() {
        PageSource billPageSource = polandDataCollector.getUnprocessedAndFilteredBillPageSources()
                .filter(source -> source.getPageUrl().equalsIgnoreCase("https://api.sejm.gov.pl/eli/acts/DU/2023/2029"))
                .findFirst()
                .get();
        LegislativeDataRecord dataRecord = parsePage(billPageSource);
        recordRepository.merge(parsePage(billPageSource));
        log.info("Data record with record id {} created for testing purposes", dataRecord.getRecordId());
    }

    public void buildDataRecords() {
        Stream<PageSource> unprocessedBillPageSources = polandDataCollector.getUnprocessedAndFilteredBillPageSources();
        unprocessedBillPageSources.forEach(source -> {
            LegislativeDataRecord dataRecord = parsePage(source);
            recordRepository.save(dataRecord);
        });
    }

    public void parseBillTextsAndOriginators() {
        recordRepository.streamAll(Country.POLAND)
                .forEach(dataRecord -> {
                    parseBillText(dataRecord.getBillTextUrl()).ifPresent(dataRecord::setBillText);
                    if (dataRecord.getBillId() != null) {
                        setOriginatorsFromBillText(dataRecord);
                    }
                    recordRepository.merge(dataRecord);
                });
    }

    private LegislativeDataRecord parsePage(PageSource billPageSource) {
        log.info("Building data-record started for {}", billPageSource.getPageUrl());

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();
        BillJson billJson = billApiDataParser.parseBillApiData(billPageSource, dataRecord);
        if (!billJson.getPrints().isEmpty()) {
            String processApiUrl = billJson.getPrints().get(0).getLinkToProcessApi();
            try {
                processApiDataParser.parseProcessApiData(processApiUrl, dataRecord);
            } catch (NotFoundException e) {
                log.error("Expected process-api url not found in: {} - {}", billJson.getLawId(), processApiUrl);
            }
            String processUrl = billJson.getPrints().get(0).getLinkToProcess();
            try {
                processTableParser.parseProcessTableData(processUrl, dataRecord);
            } catch (NotFoundException e) {
                log.error("Expected process url not found in: {} - {}", billJson.getLawId(), processUrl);
            }
            String term = billJson.getPrints().get(0).getTerm().toString();
            String processNumber = billJson.getPrints().get(0).getProcessNumber();

            String committeeTableUrl = String.format(COMMITTEE_TABLE_URL_TEMPLATE, term, processNumber);
            Optional<PageSource> committeePageSource = pageSourceRepository.findByPageUrl(committeeTableUrl);
            committeePageSource.ifPresentOrElse(pageSource -> committeesTableParser.parseCommittees(pageSource, dataRecord),
                    () -> log.error("Expected committee table url not found in: {} - {}", billJson.getLawId(), committeeTableUrl));

            String impactAssessmentUrl = Integer.parseInt(term) < 7 ?
                    String.format(IMPACT_ASSESSMENT_URL_TEMPLATE_TERM3_TO_TERM6, term, processNumber) :
                    String.format(IMPACT_ASSESSMENT_URL_TEMPLATE_TERM7_TO_TERM9, term, processNumber);
            pageSourceRepository.findByPageUrl(impactAssessmentUrl)
                    .ifPresentOrElse(pageSource -> impactAssessmentTableParser.parseImpactAssessment(pageSource, dataRecord),
                            () -> {
                                dataRecord.setImpactAssessmentDone(Boolean.FALSE);
                                log.info("Impact assessment table for {} does not exist", impactAssessmentUrl);
                            });
        } else {
            log.error("The process url in {} is missing", dataRecord.getLawId());
        }

        dataRecord.setDateProcessed(LocalDateTime.now());

        log.info("Building data-record finished for {}", dataRecord.getAltBillPageUrl());
        return dataRecord;
    }

    private Optional<String> parseBillText(String billTextUrl) {
        Optional<String> pdfUrl = getBillTextPdfUrl(billTextUrl);
        if (pdfUrl.isPresent()) {
            return pdfUrl.flatMap(pdfParser::tryPdfTextExtraction);
        } else {
            return Optional.empty();
        }
    }

    private Optional<String> getBillTextPdfUrl(String billTextUrl) {
        if (billTextUrl != null) {
            if (billTextUrl.endsWith(".pdf")) {
                return Optional.of(billTextUrl);
            } else if (billTextUrl.matches(BILL_TEXT_PDF_URL_OLD_REGEX.pattern())) {
                return getOldPdfUrl(billTextUrl);
            } else if (billTextUrl.matches(BILL_TEXT_PDF_URL_NEW_REGEX.pattern())) {
                return getNewPdfUrl(billTextUrl);
            }
        }
        return Optional.empty();
    }

    private Optional<String> getOldPdfUrl(String billTextUrl) {
        HttpResponse<String> response = Unirest.get(billTextUrl).asString();
        if (response.isSuccess()) {
            Document billTextPage = Jsoup.parse(response.getBody());
            List<Element> pdfUrls = billTextPage.getElementsByTag("a").stream()
                    .filter(a -> a.text().contains(".pdf") || a.text().contains(".PDF"))
                    .toList();
            if (!pdfUrls.isEmpty()) {
                String pdfUrl = pdfUrls.get(0).attr("href").trim();
                return Optional.of(pdfUrl.startsWith("https") ? pdfUrl : BILL_TEXT_URL_BASE_URL + pdfUrl);
            } else {
                return Optional.empty();
            }
        }
        log.error("Error response {} for URL {}", response.getStatus(), billTextUrl);

        return Optional.empty();
    }

    private Optional<String> getNewPdfUrl(String billTextUrl) {
        HttpResponse<String> response = Unirest.get(billTextUrl).asString();
        if (response.isSuccess()) {
            Document billTextPage = Jsoup.parse(response.getBody());
            List<Element> pdfUrls = billTextPage.getElementsByTag("a").stream()
                    .filter(li -> li.hasClass("pdf"))
                    .toList();
            if (!pdfUrls.isEmpty()) {
                String pdfUrl = pdfUrls.get(0).attr("href").trim(); // we need here the first finding
                return Optional.of(pdfUrl.startsWith("https") ? pdfUrl : BILL_TEXT_URL_BASE_URL + pdfUrl);
            } else {
                return Optional.empty();
            }
        }
        log.error("Error response {} for URL {}", response.getStatus(), billTextUrl);

        return Optional.empty();
    }

    private void setOriginatorsFromBillText(LegislativeDataRecord dataRecord) {
        if (dataRecord.getBillText() != null
                && (dataRecord.getBillText().startsWith("Druk") || dataRecord.getBillText().startsWith("RZECZYPOS"))) {
            List<Originator> originators = new ArrayList<>();
            if (dataRecord.getOriginType() == OriginType.GOVERNMENT) { // like https://www.sejm.gov.pl/Sejm9.nsf/druk.xsp?nr=1834
                Originator primeMinister = new Originator();
                primeMinister.setName(""); // Client has asked to leave in this case  name-field empty
                primeMinister.setAffiliation(TextUtils.findText(dataRecord.getBillText(), REGEX_FOR_GOVERNMENT_ORIGINATOR));
                originators.add(primeMinister);
                dataRecord.setOriginators(originators);
                // like https://www.sejm.gov.pl/Sejm9.nsf/druk.xsp?nr=3383, originator MPs are listed on the first page
            } else if (dataRecord.getOriginType() == OriginType.PARLIAMENT) {
                parseMpOriginatorsFromBillText(dataRecord, REGEX_FOR_PARLIAMENT_ORIGINATOR, originators);
            } else if (dataRecord.getOriginType() == OriginType.SENATE) { // like https://www.sejm.gov.pl/Sejm9.nsf/druk.xsp?nr=1938
                Originator senator = new Originator(); // senator is not an MP, no party info is available, just the name on first page
                senator.setName(TextUtils.findText(dataRecord.getBillText(), REGEX_FOR_SENATE_PRESIDENT_AND_CIVIC_ORIGINATORS));
                senator.setAffiliation(N_A);
                originators.add(senator);
                dataRecord.setOriginators(originators);
            } else if (dataRecord.getOriginType() == OriginType.PRESIDENT) { // like https://www.sejm.gov.pl/Sejm9.nsf/druk.xsp?nr=1388
                Originator president = new Originator(); // president is not an MP, no party info is available
                president.setName(TextUtils.findText(dataRecord.getBillText(), REGEX_FOR_SENATE_PRESIDENT_AND_CIVIC_ORIGINATORS));
                president.setAffiliation(PRESIDENT_AFFILIATION);
                originators.add(president);
                dataRecord.setOriginators(originators);
            } else if (dataRecord.getOriginType() == OriginType.CIVIC) { // like https://www.sejm.gov.pl/Sejm8.nsf/druk.xsp?nr=870
                parseMpOriginatorsFromBillText(dataRecord, REGEX_FOR_SENATE_PRESIDENT_AND_CIVIC_ORIGINATORS, originators);
            } else if (dataRecord.getOriginType() == OriginType.OTHER) { // https://orka.sejm.gov.pl/Druki5ka.nsf/wgdruku/1639
                parseMpOriginatorsFromBillText(dataRecord, REGEX_FOR_OTHER_ORIGINATOR, originators);
            }
        }
    }

    private void parseMpOriginatorsFromBillText(LegislativeDataRecord dataRecord, String regex,
                                                List<Originator> originators
    ) {
        List<String> originatorNames = TextUtils.findTexts(dataRecord.getBillText(), regex).stream()
                .map(name -> name.replaceAll("\\s+", " "))
                .toList();
        originatorNames.forEach(originatorName -> getMPs().stream()
                .filter(originator -> originator.getName().equalsIgnoreCase(originatorName))
                .findFirst()
                .ifPresent(originators::add));
        dataRecord.setOriginators(originators);
    }

    private List<Originator> getMPs() {
        List<PageSource> mpSources =
                pageSourceRepository.findByCountryAndPageType(Country.POLAND, PageType.MP_JSON.name());
        List<Originator> possibleOriginators = new ArrayList<>();
        Map<String, String> partyFullNamesMap = PolishTranslations.getPartyFullNames();
        MpJson[] mpJsonArray;
        for (PageSource source : mpSources) {
            try {
                mpJsonArray = objectMapper.readValue(source.getRawSource(), MpJson[].class);
                for (MpJson json : mpJsonArray) {
                    Originator mp = new Originator();
                    mp.setName(json.getFullName());
                    mp.setAffiliation(json.getParty() != null ?
                            json.getParty() + " - " + partyFullNamesMap.get(json.getParty()) : "N/A");
                    possibleOriginators.add(mp);
                }
            } catch (JsonProcessingException e) {
                log.error("MP JSON processing error at {}", source.getPageUrl());
            }
        }
        return possibleOriginators;
    }
}
