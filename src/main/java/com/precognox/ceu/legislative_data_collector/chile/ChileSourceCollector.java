package com.precognox.ceu.legislative_data_collector.chile;

import com.jayway.jsonpath.DocumentContext;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.exceptions.PageResponseException;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.BatchProcessingUtils;
import com.precognox.ceu.legislative_data_collector.utils.JsonPathUtils;
import com.precognox.ceu.legislative_data_collector.utils.UnirestUtils;
import kong.unirest.HttpResponse;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.precognox.ceu.legislative_data_collector.chile.utils.AffectingLawsCalculatorChile.ITEMS_PER_PAGE_DEFAULT;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.AFFECTING_LAWS_DETAILED_URL;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.BILL_LIST_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.BILL_ORIGINATOR_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.BILL_PAGE_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.BILL_TEXT_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.COMMITTEES_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.LAW_LIST_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.LAW_ORIGINATOR_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.LAW_PAGE_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.LAW_TERMINATION_DATE_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.LEGISLATIVE_STAGES_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.MODIFIED_LAWS_URL;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.ORIGINATOR_PAGE_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.PROCEDURE_TYPE_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.JSON_TYPE_AFFECTING_LAWS_DETAILED;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.JSON_TYPE_LAW;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.JSON_TYPE_LAWS_LIST;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.JSON_TYPE_LAW_TERMINATION;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.JSON_TYPE_MODIFIED_LAWS;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.JSON_TYPE_ORIGINATOR_LIST;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_BILL;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_BILL_LIST;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_BILL_ORIGINATORS;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_BILL_PROCEDURE;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_COMMITTEES;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_LAW_ORIGINATOR;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_LEGISLATIVE_STAGES;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.TEXT_TYPE_BILL_TEXT;

/**
 * Collects all pages that could be necessary
 */
@Slf4j
@Service
public class ChileSourceCollector {
    private final static String LAW_PAGE_SEARCH_STRING = "-1#normal#on||2#normal#XX1||117#normal#on||118#normal#on";
    private final static Pattern PROY_ID_REGEX = Pattern.compile("proyid=(\\d+)");
    private final static Charset CHILEAN_CHAR_SET = Charset.forName("Cp1252");
    private final static int PROCESSING_BATCH_SIZE = 80;
    private PageSourceRepository pageRepository;

    @Autowired
    public ChileSourceCollector(PageSourceRepository pageRepository) {
        this.pageRepository = pageRepository;
    }

    //To refresh the pages they need to be deleted first. Also, the list pages of the bills/laws are saved as well.
    //So to collect new bills/laws these list pages need to be deleted and recollected. (Or at least the first law list page and the most recent bill list page)

    //Sometimes with the collection of the modified laws pages the HTTP response is a 200, but it gets some kind of error page and other endpoints from the website return 403 (for about 5 min)
    //Only encountered this problem when I was collecting this single page type, never ran into it while running normal collection. If it occurs RuntimeException will be thrown
    public void collectSources() {
        collectLawsListJSONs();
        collectBillListPages();
        collectLawJSONs();
        collectBillPages();
    }

    private void collectLawsListJSONs() {
        String searchString = createGETParameterFromSearchString(LAW_PAGE_SEARCH_STRING);
        int pageIndex = 1;
        int pageSize = 500;
        int responseLength;

        do {
            String url = getLawListAPIString(searchString, pageIndex, pageSize);

            if (!pageRepository.existsByPageUrl(url)) {
                String response;

                try {
                    response = getResponseAsStringFromApi(url, JSON_TYPE_LAWS_LIST);
                } catch (DataCollectionException ex) {
                    throw new RuntimeException(ex);
                }

                DocumentContext jsonResponse = JsonPathUtils.parseJson(response);
                int total = JsonPathUtils.findByJsonPath(jsonResponse, "$[1].totalitems");
                log.info("Collecting the list of laws from page {}. [{}/page, {} total, {} remaining] ", pageIndex, pageSize, total, total - ((pageIndex - 1) * pageSize));
                List<String> lawListArray = JsonPathUtils.findListByJsonPath(jsonResponse, "$[0]");
                responseLength = lawListArray.size();

                if (responseLength > 0) {
                    PageSource source = createPageSource(lawListArray.toString(), url, JSON_TYPE_LAWS_LIST);
                    pageRepository.save(source);
                }
            } else {
                responseLength = 1;
            }
            pageIndex++;
        } while (responseLength > 0);
    }

    private String createGETParameterFromSearchString(String searchString) {
        return searchString.replaceAll("#", "\\%23").replaceAll("\\|", "\\%7C");
    }

    private String getLawListAPIString(String searchString, int page, int pageSize) {
        return String.format(LAW_LIST_API, searchString, page, pageSize);
    }

    private String getResponseAsStringFromApi(String url, String type) throws DataCollectionException {
        if (type.equals(TEXT_TYPE_BILL_TEXT)) {
            try {
                return UnirestUtils.tikaReadText(url, CHILEAN_CHAR_SET);
            } catch (Exception ex) {
                throw new DataCollectionException(String.format("Unable to read contents of the file. %s: %s", ex.getClass(), ex.getMessage()));
            }
        } else {
            try {
                HttpResponse<byte[]> response = UnirestUtils.retryGetByteResponseFrom(url);
                return new String(response.getBody(), StandardCharsets.UTF_8);
            } catch (PageResponseException | UnirestException ex) {
                throw new PageResponseException(String.format("%s not responded. Scraping aborted!", url), ex);
            }
        }
    }

    private PageSource createPageSource(String text, String url, String type) {
        PageSource source = new PageSource();
        source.setCountry(Country.CHILE);
        source.setPageType(type);
        source.setPageUrl(url);
        source.setCollectionDate(LocalDate.now());
        source.setRawSource(text);
        source.setSize(text.length());

        return source;
    }

    private void collectBillListPages() {
        LocalDate start = LocalDate.of(1818, 1, 1); //Chile was founded in 1818 officially
        LocalDate end = LocalDate.of(1818, 12, 31); //As my test, the earliest laws are from 1990
        start = LocalDate.of(1985, 1, 1);
        end = LocalDate.of(1985, 12, 31);
        do {
            log.info("Collecting list of bills from {}", start.getYear());
            String url = String.format(BILL_LIST_API, createBillListDate(start), createBillListDate(end));
            if (!pageRepository.existsByPageUrl(url)) {
                Optional<PageSource> source = getPageSourceFromAPI(url, PAGE_TYPE_BILL_LIST);
                if (source.isPresent()) {
                    pageRepository.save(source.get());
                } else {
                    throw new RuntimeException("Bills list page not responded as expected. Scraping aborted! See exception in log!");
                }
            } else {
                log.warn("Bills from {} are already in the database, wont recollect them", start.getYear());
            }
            start = start.plusYears(1);
            end = end.plusYears(1);
        } while (LocalDate.now().compareTo(start) > 0);
    }

    private String createBillListDate(LocalDate date) {
        return String.format("%d/%d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private Optional<PageSource> getPageSourceFromAPI(String url, String type) {
        Optional<String> sourceString;

        try {
            sourceString = Optional.ofNullable(getResponseAsStringFromApi(url, type));
        } catch (DataCollectionException ex) {
            log.error(String.valueOf(ex));
            sourceString = Optional.empty();
        }

        PageSource source = null;
        if (sourceString.isPresent()) {
            source = createPageSource(sourceString.get(), url, type);
        } else {
            log.warn("{} data from {} couldn't be collected", type, url);
        }

        return Optional.ofNullable(source);
    }

    private void collectLawJSONs() {
        List<String> lawNormIds = pageRepository.findByPageTypeAndCountry(JSON_TYPE_LAWS_LIST, Country.CHILE)
                .stream()
                .map(PageSource::getRawSource)
                .map(jsonString -> JsonPathUtils.getNestedObject(jsonString, "$..IDNORMA", new ArrayList<>()))
                .flatMap(List::stream)
                .map(Object::toString)
                .map(String::trim)
                .distinct()
                .toList();

        BatchProcessingUtils.completeInBatches(collectLawInformationsTask(), lawNormIds, PROCESSING_BATCH_SIZE);
    }

    private Consumer<List<String>> collectLawInformationsTask() {
        return (List<String> normIds) -> {
            createAndSavePageSourceFromURLWithAPI(normIds.stream(), LAW_PAGE_API, JSON_TYPE_LAW, "Collecting law information from {}");
            createAndSavePageSourceFromURLWithAPI(normIds.stream(), LAW_TERMINATION_DATE_API, JSON_TYPE_LAW_TERMINATION, "Collecting law termination dates from {}");

            List<PageSource> originatorListPages = createPageSourceFromURLWithAPI(
                    normIds.stream(),
                    LAW_ORIGINATOR_API,
                    JSON_TYPE_ORIGINATOR_LIST,
                    "Collecting originators list for law from {}"
            );

            pageRepository.saveAll(originatorListPages);

            Stream<String> lawOriginatorPageIds = originatorListPages
                    .stream()
                    .map(PageSource::getRawSource)
                    .map(jsonString -> JsonPathUtils.getNestedObject(jsonString, "$..i", new ArrayList<>()))
                    .flatMap(List::stream)
                    .map(Object::toString)
                    .distinct();
            createAndSavePageSourceFromURLWithAPI(lawOriginatorPageIds, ORIGINATOR_PAGE_API, PAGE_TYPE_LAW_ORIGINATOR, "Collecting originator page from {}");

            collectModifiedLawInformation(normIds);
        };
    }

    private void createAndSavePageSourceFromURLWithAPI(Stream<String> ids, String APIbase, String type, String infoMessage) {
        pageRepository.saveAll(createPageSourceFromURLWithAPI(ids, APIbase, type, infoMessage));
    }

    private List<PageSource> createPageSourceFromURLWithAPI(
            Stream<String> ids, String APIbase, String type, String infoMessage) {
        Optional<String> infoMessageOpt = Optional.ofNullable(infoMessage);

        return ids.map(id -> String.format(APIbase, id))
                .filter(url -> !pageRepository.existsByPageUrl(url))
                .peek(url -> infoMessageOpt.ifPresent(message -> log.info(message, url)))
                .map(url -> getPageSourceFromAPI(url, type))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private void collectModifiedLawInformation(List<String> ids) {
        List<PageSource> modifiedListSources = ids.stream()
                .map(id -> String.format(MODIFIED_LAWS_URL, ITEMS_PER_PAGE_DEFAULT, id))
                .filter(url -> !pageRepository.existsByPageUrl(url))
                .peek(url -> log.info("Collecting modified laws information from {}", url))
                .map(url -> getPageSourceFromAPI(url, JSON_TYPE_MODIFIED_LAWS))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isModifiedList)
                .toList();

        List<PageSource> affectingListSources = ids.stream()
                .map(id -> String.format(AFFECTING_LAWS_DETAILED_URL, ITEMS_PER_PAGE_DEFAULT, id))
                .filter(url -> !pageRepository.existsByPageUrl(url))
                .peek(url -> log.info("Collecting affecting laws information from {}", url))
                .map(url -> getPageSourceFromAPI(url, JSON_TYPE_AFFECTING_LAWS_DETAILED))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        long wrongMod = modifiedListSources.stream()
                .filter(source -> !hasAllModifiedLaws(source))
                .count();

        long wrongAff = affectingListSources.stream()
                .filter(source -> !hasAllModifiedLaws(source))
                .count();

        if (wrongAff > 0) {
            throw new RuntimeException("Modified/Affecting laws list is missing elements. Check the log to see where the error occured");
        }

        if (wrongMod > 0) {
            throw new RuntimeException("Modified/Affecting laws list is missing elements. Check the log to see where the error occured");
        }

        pageRepository.saveAll(affectingListSources);
        pageRepository.saveAll(modifiedListSources);
    }

    private boolean isModifiedList(PageSource source) {
        String typeText = JsonPathUtils.getNestedObject(source.getRawSource(),"$.ultimaColumna");
        return "Parte Modificatoria".equals(typeText);
    }

    //Right now default page size is 5000, the most I found with a single law was ~2700, but to be sure this function checks if everything is right.
    //It also ensures that the json has the required structure. If either is incorrect the program will throw a RuntimeException
    private boolean hasAllModifiedLaws(PageSource source) {
        String json = source.getRawSource();
        Number countOfItems = JsonPathUtils.getNestedObject(json, "$.cantidadAciertos");
        Number itemsInJSON = JsonPathUtils.getNestedObject(json, "$.aciertos.length()");
        if(Objects.isNull(countOfItems) || Objects.isNull(itemsInJSON)) {
            log.info("Probable 403. NULL AT {}",source.getPageUrl());
            return false;
        }
        int all = countOfItems.intValue();
        int has = itemsInJSON.intValue();
        if(all <= has) {
            return true;
        }
        log.info("NOT ALL {}",source.getPageUrl());
        return false;
    }

    private void collectBillPages() {
        List<String> billIds = pageRepository.findByPageTypeAndCountry(PAGE_TYPE_BILL_LIST, Country.CHILE)
                .stream()
                .map(PageSource::getRawSource)
                .map(Jsoup::parse)
                .map(page -> page.selectXpath("/html/body/div/div[2]/div/div[1]/table/tbody/tr/td[2]/a"))
                .flatMap(Elements::stream)
                .filter(Objects::nonNull)
                .map(Element::ownText)
                .map(String::trim)
                .distinct()
                .toList();

        BatchProcessingUtils.completeInBatches(collectBillSourcePagesTask(), billIds, PROCESSING_BATCH_SIZE);
    }

    private Consumer<List<String>> collectBillSourcePagesTask() {
        return (List<String> billIds) -> {
            List<PageSource> billPages = createPageSourceFromURLWithAPI(billIds.stream(), BILL_PAGE_API, PAGE_TYPE_BILL, "Collecting bill page from {}");
            pageRepository.saveAll(billPages);

            List<String> proyIds = billPages
                    .stream()
                    .map(PageSource::getRawSource)
                    .map(Jsoup::parse)
                    .map(page -> page.selectXpath("/html/head/script").first())
                    .filter(Objects::nonNull)
                    .map(Element::data)
                    .map(PROY_ID_REGEX::matcher)
                    .filter(Matcher::find)
                    .map(matcher -> matcher.group(1))
                    .distinct()
                    .toList();

            List<PageSource> legislativeStagePages = createPageSourceFromURLWithAPI(proyIds.stream(), LEGISLATIVE_STAGES_API, PAGE_TYPE_LEGISLATIVE_STAGES, "Collecting legislative stages page from {}");
            pageRepository.saveAll(legislativeStagePages);

            createAndSavePageSourceFromURLWithAPI(proyIds.stream(), COMMITTEES_API, PAGE_TYPE_COMMITTEES, "Collecting committees page from {}");

            createAndSavePageSourceFromURLWithAPI(proyIds.stream(), BILL_ORIGINATOR_API, PAGE_TYPE_BILL_ORIGINATORS, "Collecting bill originators page from {}");

            createAndSavePageSourceFromURLWithAPI(proyIds.stream(), PROCEDURE_TYPE_API, PAGE_TYPE_BILL_PROCEDURE, "Collecting procedure type page from {}");

            Stream<String> billTextPDFEndPoints = legislativeStagePages
                    .stream()
                    .map(PageSource::getRawSource)
                    .map(Jsoup::parse)
                    .map(page -> page.selectXpath("/html/body/div[1]/div/div/div[2]/div/div/div/table[1]/tbody/tr/td[5]/a[text()='Mensaje/Moción']").first())
                    .filter(Objects::nonNull)
                    .map(linkElement -> linkElement.attr("href"));
            createAndSavePageSourceFromURLWithAPI(billTextPDFEndPoints, BILL_TEXT_API, TEXT_TYPE_BILL_TEXT, "Collecting bill text from {}");
        };
    }


    /**
     * Affecting laws page urls have the number of affecting_record items in them. This is right now set to 5000 to make sure that a single page will hold all the information for a law (the most I found was 2700)
     * If there will be one page with more than 5000 items, with this function all the necessary urls can be renamed in the database, after setting ITEMS_PER_PAGE_DEFAULT.
     * (So no recollection will be needed, except for the page that has more than 5000 items.
     */
    public void renameAffectingDetailedUrls() {
        Pattern regex = Pattern.compile("idNorma=(\\d+)");
        List<PageSource> renamed = pageRepository.findByPageTypeAndCountry(JSON_TYPE_AFFECTING_LAWS_DETAILED, Country.CHILE)
                .stream()
                .peek(source -> {
                    Matcher m = regex.matcher(source.getPageUrl());
                    m.find();
                    String norm = m.group(1);
                    String nwUrl = String.format(AFFECTING_LAWS_DETAILED_URL, ITEMS_PER_PAGE_DEFAULT, norm);
                    source.setPageUrl(nwUrl);
                })
                .toList();
        log.info("Saving renaming");
        pageRepository.saveAll(renamed);
    }
}
