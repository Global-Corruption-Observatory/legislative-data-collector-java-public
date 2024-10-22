package com.precognox.ceu.legislative_data_collector.jordan;

import com.jayway.jsonpath.DocumentContext;
import com.precognox.ceu.legislative_data_collector.ScrapingController;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.JsonPathUtils;
import com.precognox.ceu.legislative_data_collector.utils.JsonUtils;
import com.precognox.ceu.legislative_data_collector.utils.queue.InfinityDataList;
import com.precognox.ceu.legislative_data_collector.utils.queue.InfinityDbBrowser;
import kong.unirest.GetRequest;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static com.precognox.ceu.legislative_data_collector.jordan.JordanParser.parseRowData;
import static com.precognox.ceu.legislative_data_collector.utils.ParserHelper.getStringValue;

@Slf4j
@Service
public class JoDataCollector implements ScrapingController {

    private static final String LIST_BASE_URL = "https://lob.gov.jo/?v=1.14&url=ar/Jordanian-Legislation#!?AD=false&LTI=&MS=0&AS=0&AT=&IS=0&CT=0";
    public static final String DETAILS_URL_TEMPLATE = "https://lob.gov.jo/?v=1.14&url=ar/LegislationDetails?LegislationID:LEGISLATION_ID,LegislationType:LEGISLATION_TYPE";
//    public static final String DETAILS_URL_TEMPLATE = "https://lob.gov.jo/?v=1.14&url=ar/LegislationDetails?LegislationID:1129,LegislationType:2,isMod:true";
    private static final String LAWS_API_BASE_URL       = "https://lob.gov.jo/OPSHandler/Customization/LobJo/LobJo.asmx/GetLegislationSearch?LangID=0&SearchData=%7B%22LegislationType%22%3A2%2C%22LegislationTitle%22%3A%22%22%2C%22LegislationNumber%22%3A-1%2C%22LegislationYear%22%3A-1%2C%22LegislationYearTo%22%3A-1%2C%22LegislationYearFrom%22%3A-1%2C%22LegislationStatus%22%3A-1%2C%22MatchingSearch%22%3A0%2C%22ArticleSearch%22%3A0%2C%22ArticleText%22%3A%22%22%2C%22Issuer%22%3A0%2C%22CourtType%22%3A0%2C%22FromHome%22%3A0%7D";
    private static final String REGULATION_API_BASE_URL = "https://lob.gov.jo/OPSHandler/Customization/LobJo/LobJo.asmx/GetLegislationSearch?LangID=0&SearchData=%7B%22LegislationType%22%3A3%2C%22LegislationTitle%22%3A%22%22%2C%22LegislationNumber%22%3A-1%2C%22LegislationYear%22%3A-1%2C%22LegislationYearTo%22%3A-1%2C%22LegislationYearFrom%22%3A-1%2C%22LegislationStatus%22%3A-1%2C%22MatchingSearch%22%3A0%2C%22ArticleSearch%22%3A0%2C%22ArticleText%22%3A%22%22%2C%22Issuer%22%3A0%2C%22CourtType%22%3A0%2C%22FromHome%22%3A0%7D";
    public static final String LAWS_API_DETAILS_URL = "https://lob.gov.jo/OPSHandler/Customization/LobJo/LobJo.asmx/GetLegislationDetails";
    private static final String LAWS_API_MODIFIED_URL = "https://lob.gov.jo/OPSHandler/Customization/LobJo/LobJo.asmx/GetlegislationModified";
    public static final String LAWS_API_RELATED_URL_TEMPLATE = "https://lob.gov.jo/OPSHandler/Customization/LobJo/LobJo.asmx/GetAssociatedSystems?PageIndex=1&SearchData=%7B%22LegislationType%22%3ARELATED_LEGISLATION_TYPE%2C%22LegislationNumber%22%3A-1%2C%22LegislationYear%22%3A-1%2C%22LegislationName%22%3A%22%22%7D";
    private static final boolean TEST_MODE = false;

    @Autowired
    private PrimaryKeyGeneratingRepository keyGeneratingRepository;

    @Autowired
    private PageSourceRepository pageSourceRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Override
    @Transactional
    public void runScraping(List<String> args) {
        log.info("Start Collecting!");
        collect(LAWS_API_BASE_URL);
        collect(REGULATION_API_BASE_URL);

        //parseAllRowDataWithStream();
        parseAllRowData();

        log.info("Collection finished");
    }

    private void collect(String apiBaseUrl) {
        Unirest.config().verifySsl(false);
        int currentPage = 1;
        Integer maxPage;

        do {
            log.info("CurrentPage=" + currentPage);

            HttpResponse<JsonNode> lawsResponse = Unirest.get(apiBaseUrl)
                    .queryString("PageIndex", currentPage)
                    .asJson();
            DocumentContext context = JsonPathUtils.parseJson(lawsResponse.getBody().toString());
            Double maxPageAsDouble = JsonPathUtils.findByJsonPath(context, "$.Value.TotalPages");
            maxPage = maxPageAsDouble.intValue();

            log.info("MaxPage=" + maxPage);

            if (TEST_MODE && currentPage > 2) {
                break;
            }

            List<Map<String, Object>> lawsLists =
                    JsonPathUtils.findByJsonPath(context, "$.Value.List", Collections.EMPTY_LIST);

            for (Map<String, Object> law : lawsLists) {
                PageSource data = new PageSource();
                data.setCountry(Country.JORDAN);
                data.setPageType("bill");
                Map<String, Object> rawMap = new HashMap<>();
                rawMap.put("listItem", law);

                String legislationID = getStringValue(law, "pmk_ID");
                String legislationType = getStringValue(law, "Type");
                String isMod = getStringValue(law, "isMod");

                GetRequest lawDetailsRequest = Unirest.get(LAWS_API_DETAILS_URL)
                        .queryString("LangID", 0)
                        .queryString("LegislationID", legislationID)
                        .queryString("LegislationType", legislationType)
                        .queryString("isMod", isMod);

                data.setPageUrl(lawDetailsRequest.getUrl());
                rawMap.put("url", lawDetailsRequest.getUrl());
                String details = lawDetailsRequest.asJson().getBody().toString();
                rawMap.put("details", details);

                GetRequest lawModifiedRequest = Unirest.get(LAWS_API_MODIFIED_URL)
                        .queryString("LangID", 0)
                        .queryString("LegislationID", legislationID)
                        .queryString("LegislationType", legislationType);

                String modifiedValue = lawModifiedRequest.asJson().getBody().toString();
                rawMap.put("modifiedValue", modifiedValue);

                DocumentContext modifiedContext = JsonPathUtils.parseJson(Objects.toString(rawMap.get("modifiedValue")));
                List<Map<String, Object>> modifiedValueList =
                        JsonPathUtils.findByJsonPath(modifiedContext, "$.Value", Collections.EMPTY_LIST);

                findOrDownloadModifiedLaws(modifiedValueList, lawDetailsRequest.getUrl());

                data.setRawSource(JsonUtils.toString(rawMap));
                saveInNewTransaction(data);
            }

            currentPage++;
        } while (maxPage >= currentPage);
    }

    private void parseData(String url) {
        Optional<PageSource> pageSource = pageSourceRepository.findByPageUrl(url);
        Pair<LegislativeDataRecord, PageSource> result = parseRowData(pageSource.get(), this::findOrDownloadModifiedLaws);
        saveInNewTransaction(result.getSecond());
        saveInNewTransaction(result.getFirst());

        log.info("Finish {};", result.getSecond().getPageUrl());
    }

    public void parseAllRowDataWithStream() {
//        try (Stream<PageSource> pageStream = pageSourceRepository.streamAllWithRawData(Country.JORDAN)) {
//        try (Stream<PageSource> pageStream = pageSourceRepository.streamByCountryAndPageType(Country.JORDAN, "bill")) {
        try (Stream<PageSource> pageStream = pageSourceRepository.streamByCountryAndPageType(Country.JORDAN, "amended_law")) {
            pageStream.parallel().forEach(data -> {
                try {
                    Pair<LegislativeDataRecord, PageSource> result = parseRowData(data, this::findOrDownloadModifiedLaws);
                    log.info("Finish parseRowData{};", result.getSecond().getPageUrl());
                    saveInNewTransaction(result.getSecond());
                    saveInNewTransaction(result.getFirst());
                    log.info("Finish {};", result.getSecond().getPageUrl());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void parseAllRowData() {
        int pageSize = 20;
        int currentPage = 0;

        ExecutorService executorService = Executors.newFixedThreadPool(27);

        InfinityDbBrowser<PageSource> infinityDbBrowser = new InfinityDbBrowser<>(pageSize, pageable -> {
            log.info("current PageNumber = " + pageable.getPageNumber());
            return pageSourceRepository.findAll(pageable);
        });

        infinityDbBrowser.setCurrentPage(currentPage);
        infinityDbBrowser.setLastPage(currentPage + 2000);
        InfinityDataList<PageSource> dataList = new InfinityDataList<>(executorService, infinityDbBrowser);

        dataList.forEach(data -> {
            try {
//                log.info("START data.getId = " + data.getId());
                Pair<LegislativeDataRecord, PageSource> result = parseRowData(data, this::findOrDownloadModifiedLaws);
                saveInNewTransaction(result.getSecond());
                saveInNewTransaction(result.getFirst());
//                log.info("END data.getId = " + data.getId());
            } catch (Exception ex) {
                log.error("Failed to parse: " + data.getPageUrl(), ex);
            }
        });

        log.info("FINISH dataList");
    }

    private synchronized void saveInNewTransaction(PageSource data) {
        if (data != null) {
            try {
                transactionTemplate.execute(status -> pageSourceRepository.save(data));
            } catch (Exception ex) {
                log.error("Failed to save LegislativeDataRecord in saveInNewTransaction: " + data.getId() + " URL: " + data.getPageUrl(), ex);
            }
        }
    }

    private synchronized void saveInNewTransaction(LegislativeDataRecord data) {
        try {
            keyGeneratingRepository.save(data);
        } catch (Exception ex) {
            log.error("Failed to save LegislativeDataRecord in saveInNewTransaction: " + data.getRecordId() + " URL: " + data.getBillPageUrl(), ex);
        }
    }

    private synchronized boolean existsByPageUrl(String url) {
        return pageSourceRepository.existsByPageUrl(url);
    }

    private synchronized Optional<PageSource> findPageSourceByUrl(String url) {
        return pageSourceRepository.findByPageUrl(url);
    }

//    private void parseRowData() {
//        List<LegislativeDataRecord> dataRecordList = findAll();
//        dataRecordList.stream()
//                .parallel()
//                .forEach(data -> save(JordanParser.parseRowData(data)));
//    }

    private List<LegislativeDataRecord> findAll() {
        return keyGeneratingRepository.findAll();
    }

    private void save(LegislativeDataRecord data) {
        keyGeneratingRepository.save(data);
    }

    private synchronized List<PageSource> findOrDownloadModifiedLaws(
            List<Map<String, Object>> modifiedValueList, String metadata) {
        log.info("findOrDownloadModifiedLaws:{}", modifiedValueList.size());

        List<PageSource> result = new ArrayList<>();

        for (Map<String, Object> amendedLaw : modifiedValueList) {
            PageSource data = new PageSource();
            data.setCountry(Country.JORDAN);
            data.setPageType("amended_law");
            data.setMetadata(metadata);
            Map<String, Object> rawMap = new HashMap<>();

            String affectingLegislationID = Objects.toString(amendedLaw.get("ModLeg"));
            String affectingLawsLegislationType = Objects.toString(amendedLaw.get("ModLegType"));

            GetRequest affectingLawsDetailsRequest = Unirest.get(LAWS_API_DETAILS_URL)
                    .queryString("LangID", 0)
                    .queryString("LegislationID", affectingLegislationID)
                    .queryString("LegislationType", affectingLawsLegislationType)
                    .queryString("isMod", true);

            log.info("affectingLawsDetails:{}", affectingLawsDetailsRequest.getUrl());
            data.setPageUrl(affectingLawsDetailsRequest.getUrl());

            Optional<PageSource> pageSource = findPageSourceByUrl(data.getPageUrl());
            if(pageSource.isPresent()) {
                result.add(pageSource.get());
            } else {
                rawMap.put("url", data.getPageUrl());

                Map<String, Object> law = new HashMap<>();
                law.put("pmk_ID", affectingLegislationID);
                law.put("Type", affectingLawsLegislationType);
                law.put("Name", amendedLaw.get("ModName"));
                law.put("Status_AR", null);
                law.put("isMod", true);

                rawMap.put("listItem", law);

                String affectingLawsDetails = affectingLawsDetailsRequest.asJson().getBody().toString();
                rawMap.put("details", affectingLawsDetails);

                data.setRawSource(JsonUtils.toString(rawMap));
                saveInNewTransaction(data);
                result.add(data);
            }
        }

        return result;
    }


}
