package com.precognox.ceu.legislative_data_collector.poland;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.poland.constants.PageType;
import com.precognox.ceu.legislative_data_collector.poland.json.BillJson;
import com.precognox.ceu.legislative_data_collector.poland.json.ProcessJson;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.JsonUtils;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * This class is responsible for the raw source collection. BILL_JSON-s for PolandBillApiDataParser.class;
 * PROCESS_JSON-s for PolandProcessApiDataParser.class; COMMITTEE_JSON-s for committee names and committee roles;
 * COMMITTEES_TABLE-s for PolandCommitteesTableParser.class; IMPACT_ASSESSMENT-s for PolandImpactAssessmentTableParser.class;
 * PROCESS_HTML-s for PolandProcessTableParser.class; MP_JSON-s for parsing MP Originators in PolandRecordBuilder.class.
 */
@Slf4j
@Service
public class PolandDataCollector {

    private static final int API_URL_BEFORE_YEAR_INDEX_POSITION = 36; // https://api.sejm.gov.pl/eli/acts/DU/1997/1026
    private static final int API_URL_AFTER_YEAR_INDEX_POSITION = 40; // https://api.sejm.gov.pl/eli/acts/DU/1997/1026
    private static final int LAWS_IN_1997_WITH_NO_PROCESSES = 168;
    private static final Pattern LAW_PAGE_URL_REGEX = Pattern.compile("^.*((DU/199[789])|(DU/20)).*$");
    private static final Pattern USTAWA_REGEX = Pattern.compile("\"title\":\"Ustawa");
    private static final String LAWS_COUNT_PER_YEAR_API_URL_TEMPLATE = "https://api.sejm.gov.pl/eli/acts/DU/%s";
    private static final String LAW_API_URL_TEMPLATE = "https://api.sejm.gov.pl/eli/acts/DU/%s/%s";
    private static final int START_YEAR = 1997; // for the very first run it was 1918
    private static final int END_YEAR = 2023;
    private static final int FIRST_TERM = 3;
    private static final int LAST_TERM = 9;
    private static final String BILL_HTML_URL_TEMPLATE = "https://isap.sejm.gov.pl/isap.nsf/download.xsp/%s/H/text.html";
    private static final String PROCESS_HTML_URL_TEMPLATE_FROM_TERM3 = "https://orka.sejm.gov.pl/proc%s.nsf/opisy/%s.htm";
    private static final String PROCESS_HTML_URL_TEMPLATE_FROM_TERM7 = "https://www.sejm.gov.pl/Sejm%s.nsf/PrzebiegProc.xsp?nr=%s";
    private static final String COMMITTEE_API_URL_TEMPLATE = "https://api.sejm.gov.pl/sejm/term%s/committees";
    private static final String MP_LIST_API_URL_TEMPLATE = "https://api.sejm.gov.pl/sejm/term%s/MP";
    private static final String COMMITTEE_TABLE_URL_TEMPLATE = "https://www.sejm.gov.pl/SQL2.nsf/poskomprocall?OpenAgent&%s&%s";
    private static final String IMPACT_ASSESSMENT_URL_TEMPLATE_FROM_TERM3 = "https://orka.sejm.gov.pl/rexdomk%s.nsf/Opdodr?OpenPage&nr=%s";
    private static final String IMPACT_ASSESSMENT_URL_TEMPLATE_FROM_TERM7 = "https://www.sejm.gov.pl/Sejm%s.nsf/opinieBAS.xsp?nr=%s";
    private static final List<String> OLDER_WEBPAGE_TERMS = List.of("3", "4", "5", "6");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PageSourceRepository pageSourceRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public PolandDataCollector(PageSourceRepository pageSourceRepository, TransactionTemplate transactionTemplate
    ) {
        this.pageSourceRepository = pageSourceRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public void runCollection() {
        downloadActApiResponses();
        downloadProcessApiResponses();
        downloadCommitteeApiResponses();
        downloadCommitteesTablesRawSources();
        downloadImpactAssessmentRawSources();
        downloadProcessHtmlRawSources();
        downloadMpNameAndAffiliationListApiResponses();
        downloadBillsInHtmlForm();
    }

    // For example: "https://api.sejm.gov.pl/eli/acts/DU/2018/317"
    private void downloadActApiResponses() {
        log.info("Collecting Act-API responses for Poland.");
        final List<Integer> itemsInYears = getBillItemsInYears();

        for (int year = START_YEAR; year <= END_YEAR; year++) {
            for (int item = 1; item <= itemsInYears.get(year - START_YEAR); item++) {
                String url = String.format(LAW_API_URL_TEMPLATE, year, item);
                savePageSource(url, PageType.BILL_JSON);
            }
        }
        log.info("Collecting Act-API responses for Poland is finished.");
    }

    // For example https://api.sejm.gov.pl/eli/acts/DU/1997's responses "totalCount" key has a value of 1136.
    // In the first data-shipment the last processed law's law_id is "Dz.U. 2023 poz. 2029"
    @NotNull
    private List<Integer> getBillItemsInYears() {
        List<Integer> itemsInYears = new ArrayList<>();
        for (int year = START_YEAR; year <= END_YEAR; year++) {
            HttpResponse<JsonNode> totalCountJson =
                    Unirest.get(String.format(LAWS_COUNT_PER_YEAR_API_URL_TEMPLATE, year)).asJson();
            itemsInYears.add(totalCountJson.getBody().getObject().optInt("totalCount", 0));
        }
        return itemsInYears;
    }

    // For example: "https://api.sejm.gov.pl/sejm/term8/processes/2147"
    private void downloadProcessApiResponses() {
        log.info("Collecting Process-API responses for Poland.");

        List<String> collectedProcessUrls = getBillJsons()
                .filter(billJson -> !billJson.getPrints().isEmpty())
                .map(billJson -> billJson.getPrints().get(0).getLinkToProcessApi())
                .toList();
        collectedProcessUrls.forEach(url -> savePageSource(url, PageType.PROCESS_JSON));

        log.info("Collecting Process-API responses for Poland is finished.");
    }

    // For example: https://api.sejm.gov.pl/sejm/term8/committees
    private void downloadCommitteeApiResponses() {
        log.info("Collecting Committee API-responses for Poland.");

        for (int termNo = FIRST_TERM; termNo <= LAST_TERM; termNo++) {
            String url = String.format(COMMITTEE_API_URL_TEMPLATE, termNo);
            savePageSource(url, PageType.COMMITTEE_JSON);
        }

        log.info("Collecting Committee API-responses for Poland is finished.");
    }

    // For example: https://www.sejm.gov.pl/SQL2.nsf/poskomprocall?OpenAgent&8&2147
    private void downloadCommitteesTablesRawSources() {
        log.info("Start collecting Committee HTML-sources for Poland.");

        List<String> committeeTableUrls = getTermAndProcessNumbersFromRawSource().stream()
                .map(entry -> String.format(COMMITTEE_TABLE_URL_TEMPLATE, entry.getKey(), entry.getValue()))
                .toList();
        committeeTableUrls.forEach(url -> savePageSource(url, PageType.COMMITTEES_TABLE));

        log.info("Collecting Committee HTML-sources for Poland is finished.");
    }

    // For example: https://orka.sejm.gov.pl/rexdomk6.nsf/Opdodr?OpenPage&nr=3610
    private void downloadImpactAssessmentRawSources() {
        log.info("Collecting Impact assessment HTML-sources for Poland.");

        List<String> iaUrls = new ArrayList<>();
        for (ImmutablePair<String, String> termAndPrNumber : getTermAndProcessNumbersFromRawSource()) {
            String url;
            if (OLDER_WEBPAGE_TERMS.contains(termAndPrNumber.getKey())) {
                url = String.format(IMPACT_ASSESSMENT_URL_TEMPLATE_FROM_TERM3, termAndPrNumber.getKey(), termAndPrNumber.getValue());
            } else {
                url = String.format(IMPACT_ASSESSMENT_URL_TEMPLATE_FROM_TERM7, termAndPrNumber.getKey(), termAndPrNumber.getValue());
            }
            iaUrls.add(url);
        }
        iaUrls.forEach(url -> savePageSource(url, PageType.IMPACT_ASSESSMENT));

        log.info("Collecting Impact assessment HTML-sources for Poland is finished.");
    }

    // URL-s could be in form like: "https://orka.sejm.gov.pl/proc3.nsf/opisy/1235.htm"
    // or from term 7 like: "https://www.sejm.gov.pl/Sejm9.nsf/PrzebiegProc.xsp?nr=3413"
    private void downloadProcessHtmlRawSources() {
        log.info("Collecting Legislative process HTML-sources for Poland.");

        List<String> processUrls = new ArrayList<>();
        for (ImmutablePair<String, String> entry : getTermAndProcessNumbersFromRawSource()) {
            String url;
            if (OLDER_WEBPAGE_TERMS.contains(entry.getKey())) {
                url = String.format(PROCESS_HTML_URL_TEMPLATE_FROM_TERM3, entry.getKey(), entry.getValue());
            } else {
                url = String.format(PROCESS_HTML_URL_TEMPLATE_FROM_TERM7, entry.getKey(), entry.getValue());
            }
            processUrls.add(url);
        }
        processUrls.forEach(processUrl -> savePageSource(processUrl, PageType.PROCESS_HTML));

        log.info("Collecting Legislative process HTML-sources for Poland is finished.");
    }

    private List<ImmutablePair<String, String>> getTermAndProcessNumbersFromRawSource() {
        List<PageSource> processPageSources =
                pageSourceRepository.findAllByCountryAndPageType(Country.POLAND, PageType.PROCESS_JSON.name());
        List<ImmutablePair<String, String>> termsAndProcessNumbers = new ArrayList<>();
        ProcessJson processJson;
        for (PageSource processPageSource : processPageSources) {
            try {
                processJson = objectMapper.readValue(processPageSource.getRawSource(), ProcessJson.class);
                termsAndProcessNumbers.add(new ImmutablePair<>(processJson.getTermOfOffice(), processJson.getNumber()));
            } catch (JsonProcessingException e) {
                log.error("Process-JSON processing error at {} url " + e, processPageSource.getPageUrl());
            }
        }
        return termsAndProcessNumbers;
    }

    // For example: https://api.sejm.gov.pl/sejm/term8/MP
    private void downloadMpNameAndAffiliationListApiResponses() {
        log.info("Collecting MP name- and affiliation API-responses for Poland.");

        for (int termNo = FIRST_TERM; termNo <= LAST_TERM; termNo++) {
            String url = String.format(MP_LIST_API_URL_TEMPLATE, termNo);
            savePageSource(url, PageType.MP_JSON);
        }

        log.info("Collecting MP name- and affiliation API-responses for Poland finished.");
    }

    private void savePageSource(String url, PageType pageType) {
        if (pageSourceRepository.existsByPageUrl(url)) {
            log.info("Skipping downloaded page: {}", url);
        } else {
            HttpResponse<String> response = Unirest.get(url).asString();

            if (response.isSuccess()) {
                PageSource pageSource = new PageSource();
                pageSource.setRawSource(response.getBody());
                pageSource.setPageUrl(url);
                pageSource.setPageType(pageType.name());
                pageSource.setCountry(Country.POLAND);
                pageSource.setCollectionDate(LocalDate.now());
                pageSource.setSize(response.getBody().length());

                transactionTemplate.executeWithoutResult(transactionStatus -> pageSourceRepository.save(pageSource));

                log.info("Stored page: {}", url);
            } else {
                log.error("Error response {} for URL {}", response.getStatus(), url);
            }
            try {
                // slow down downloading a bit to avoid weird connection issues with source pages
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.error("InterruptedException for URL {}", url);
            }
        }
    }

    private void downloadBillsInHtmlForm() {
        log.info("Collecting Bill HTML-sources for Poland.");

        List<String> htmlUrls = new ArrayList<>();
        getBillJsons()
                .filter(BillJson::getTextHTML)
                .map(BillJson::getBillAddressForPdfText)
                .toList()
                .forEach(url -> htmlUrls.add(String.format(BILL_HTML_URL_TEMPLATE, url)));
        htmlUrls.forEach(htmlUrl -> savePageSource(htmlUrl, PageType.BILL_HTML));

        log.info("Collecting Bill HTML-sources for Poland finished.");
    }

    // As an agreement we process data only from where legislative process itself is available
    // (from DU/1997/1026 until DU/2023/2029 for the first shipment).
    public Stream<PageSource> getUnprocessedAndFilteredBillPageSources() {
        return pageSourceRepository
                .streamUnprocessedBillsOnAltPageUrl(Country.POLAND)
                .filter(pageSource -> pageSource.getPageUrl().matches(LAW_PAGE_URL_REGEX.pattern()))
                .filter(source -> source.getRawSource().contains(USTAWA_REGEX.pattern()))
                .sorted(Comparator.comparingInt(item -> Integer.parseInt(item.getPageUrl()
                        .substring(API_URL_BEFORE_YEAR_INDEX_POSITION, API_URL_AFTER_YEAR_INDEX_POSITION))))
                .skip(LAWS_IN_1997_WITH_NO_PROCESSES);
    }

    private Stream<BillJson> getBillJsons() {
        return getUnprocessedAndFilteredBillPageSources()
                .map(PageSource::getRawSource)
                .map(json -> JsonUtils.parseToObject(json, BillJson.class))
                .sorted(Comparator.comparing(BillJson::getBillAddressForPdfText));
    }
}
