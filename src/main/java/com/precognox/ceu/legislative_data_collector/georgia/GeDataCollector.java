package com.precognox.ceu.legislative_data_collector.georgia;

import com.jayway.jsonpath.DocumentContext;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.DownloadedFile;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.DownloadedFileRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.JsonPathUtils;
import com.precognox.ceu.legislative_data_collector.utils.JsonUtils;
import com.precognox.ceu.legislative_data_collector.utils.queue.ExecutorServiceUtils;
import com.precognox.ceu.legislative_data_collector.utils.queue.InfinityDataList;
import com.precognox.ceu.legislative_data_collector.utils.queue.InfinityDbBrowser;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.transaction.Transactional;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
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

import static com.precognox.ceu.legislative_data_collector.utils.XmlUtils.byteToXml;


@Slf4j
@Service
public class GeDataCollector {

    private static final String LIST_BASE_URL = "https://web-api.parliament.ge/api/bills?billTypeId=1&page=";
    private static final String BILLS_API_ITEM_BASE_URL = "https://web-api.parliament.ge/api/bills/";
    private static final String BILL_REVIEW_URL = "https://info.parliament.ge/law/1/bill/{billId}/billReview";
    private static final String BILL_COMMITTEE_URL = "https://info.parliament.ge/law/1/billReview/{billReviewId}/billCommittee";
    private static final String BILL_REVIEW_CONTENT_URL = "https://info.parliament.ge/law/1/billReview/{billReviewId}/billReviewContent";
    private static final String BILL_REVIEW_CONTENT_FILE_URL = "https://info.parliament.ge/file/1/BillReviewContent/{billReviewContentId}";
//    https://info.parliament.ge/swagger-ui.html#/law-controller/getBillsUsingGET
//    private static final String BILL_ALL_ITEM = "https://info.parliament.ge/law/1/bill?confirmedProcedure=true&limit=10&reviewProcedureTypeId=1&sort=%5B%7B%22property%22%3A%22billPackage.registrationDate%22%2C%22direction%22%3A%22ASC%22%7D%5D&start=";
//    private static final String BILL_ALL_ITEM = "https://info.parliament.ge/law/1/bill?limit=10&sort=%5B%7B%22property%22%3A%22billPackage.registrationDate%22%2C%22direction%22%3A%22ASC%22%7D%5D&start=";
    private static final String BILL_ALL_ITEM = "https://info.parliament.ge/law/1/bill?limit=10&reviewProcedureTypeId=2&sort=%5B%7B%22property%22%3A%22billPackage.registrationDate%22%2C%22direction%22%3A%22ASC%22%7D%5D&start=";

    //        https://info.parliament.ge/law/1/billReview/1912/billReviewContent
//        https://info.parliament.ge/file/1/BillReviewContent/208671
    public static final String PAGE_TYPE_BILL = "bill";
    private static final String ITEM_BASE_URL = "https://parliament.ge/legislation/";

    public boolean TEST_MODE = false;

    @Autowired
    private JdbcTemplate dataRepository;

    @Autowired
    private PrimaryKeyGeneratingRepository keyGeneratingRepository;

    @Autowired
    private PageSourceRepository pageSourceRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DownloadedFileRepository downloadedFileRepository;

    @Transactional
    public void runCollection() {
        log.info("Start Collecting!");
//        deleteAllParsedData();
//        collect(LIST_BASE_URL);
//        collectBillsExtraInfo(BILL_ALL_ITEM);
//        parseSingleData("16854");
//        parseRowData();
        parseAllRowData();
//        parseAllRowDataWithStream();
//        testDataRepository();
        log.info("Collection finished");
    }

    private void parseAllRowDataWithStream() {
        try (Stream<PageSource> pageStream = pageSourceRepository.streamAll(Country.GEORGIA)) {
            pageStream.forEach(data -> saveInNewTransaction(GeorgianParser.parseRowData(data)));
        }
    }

    private void deleteAllParsedData() {
//        keyGeneratingRepository.deleteAllDataRecord();
        int pageSize = 10;
        int currentPage = 0;
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        InfinityDbBrowser<LegislativeDataRecord> infinityDbBrowser = new InfinityDbBrowser<>(pageSize, pageable -> {
            log.info("current PageNumber = " + pageable.getPageNumber());
            return keyGeneratingRepository.findAll(pageable);
        });
        infinityDbBrowser.setCurrentPage(currentPage);
//        infinityDbBrowser.setLastPage(currentPage + 2000);
        InfinityDataList<LegislativeDataRecord> dataList = new InfinityDataList<>(executorService, infinityDbBrowser);

        dataList.forEach(data -> {
            try {
                log.info("START data.getId = " + data.getRecordId());
                keyGeneratingRepository.deleteWithRelatedData(data);
                log.info("END data.getId = " + data.getRecordId());
            } catch (Exception ex) {
                log.error("Failed to parse: " + data.getRecordId(), ex);
            }
        });
        log.info("FINISH dataList");
    }

    private void testDataRepository() {
//        pageSourceRepository.
        RowCallbackHandler rowCallbackHandler = rs -> modify(rs);
        dataRepository.query("SELECT * FROM page_source", rowCallbackHandler);
    }

    private void modify(ResultSet rs) {
        try {
            System.out.println(rs.getString("page_type"));
            PageSource pageSource = rs.unwrap(PageSource.class);
//            System.out.println("pageSource");
            System.out.println(pageSource.getPageUrl());

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

//    private void parseRowData() {
//        List<LegislativeDataRecord> dataRecordList = findAll();
//        dataRecordList.stream()
////                .parallel()
//                .forEach(data -> save(GeorgianParser.parseRowData(data)));
//    }

    private void parseSingleData(String id) {
        String pageUrl = ITEM_BASE_URL + id;
        PageSource pageSource = pageSourceRepository.getByPageUrl(pageUrl);
        save(GeorgianParser.parseRowData(pageSource));
    }

    private void parseRowData() {
        List<PageSource> dataRecordList = findAll();
        dataRecordList.stream()
//                .parallel()
                .forEach(data -> {
                    save(GeorgianParser.parseRowData(data));
                });
    }

    private void parseAllRowData() {
//    private void parseAllRowDataV2() {
        int pageSize = 2;
        int currentPage = 0;
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        InfinityDbBrowser<PageSource> infinityDbBrowser = new InfinityDbBrowser<>(pageSize, pageable -> {
            log.info("current PageNumber = " + pageable.getPageNumber());
            return findAll(pageable);
        });
        infinityDbBrowser.setCurrentPage(currentPage);
//        infinityDbBrowser.setLastPage(currentPage + 2000);
        InfinityDataList<PageSource> dataList = new InfinityDataList<>(executorService, infinityDbBrowser);

        dataList.forEach(data -> {
            try {
                log.info("START data.getId = " + data.getId());
                saveInNewTransaction(GeorgianParser.parseRowData(data));
                log.info("END data.getId = " + data.getId());
            } catch (Exception ex) {
                log.error("Failed to parse: " + data.getPageUrl(), ex);
            }
        });
        log.info("FINISH dataList");
    }

    private synchronized Page<PageSource> findAll(Pageable pageable) {
        return pageSourceRepository.findAll(pageable);
    }

    private synchronized List<PageSource> findAll() {
        return pageSourceRepository.findAll();
    }

    private synchronized void save(LegislativeDataRecord data) {
        keyGeneratingRepository.save(data);
    }

    private synchronized void saveInNewTransaction(LegislativeDataRecord data) {
        keyGeneratingRepository.save(data);
    }

    private synchronized Optional<PageSource> findByPageUrl(String pageUrl) {
        Optional<PageSource> pageSource = pageSourceRepository.findByPageUrl(pageUrl);
        return pageSource;
    }

    private void collectBillsExtraInfo(String apiBaseUrl) {
        Unirest.config().verifySsl(false);
        int maxPageCount = 9999999;
        int currentPage = 0;
        Integer maxPage;
        do {
            log.info("CurrentPage="+currentPage);
            String currentUrl = apiBaseUrl + currentPage;
            String currentPageSource = getResponseBody(currentUrl);
            DocumentContext context = JsonPathUtils.parseJson(currentPageSource);
            Number totalItemCount = JsonPathUtils.findByJsonPath(context, "$.total");
            maxPage = totalItemCount.intValue()/10;
            log.info("MaxPage="+maxPage);
            if (TEST_MODE && currentPage > maxPageCount) {
                break;
            }
            List<Map<String, Object>> lawsLists = JsonPathUtils.findByJsonPath(context, "$.list", Collections.EMPTY_LIST);

            ExecutorServiceUtils.forEach(lawsLists, lawsLists.size(), billsExtraInfo -> {
                        String id = Objects.toString(billsExtraInfo.get("id"));
                        log.info("Start Collect: " + id);
                        String itemUrl = ITEM_BASE_URL + id;

                        Optional<PageSource> pageSourceResult = findByPageUrl(itemUrl);
                        if (pageSourceResult.isPresent()) {
                            log.info("Found Page Source: " + itemUrl);
                            PageSource pageSource = pageSourceResult.get();
                            String rawSource = pageSource.getRawSource();
                            DocumentContext rawSourceContext = JsonPathUtils.parseJson(rawSource);
                            Map<String, Object> rawMap = JsonPathUtils.findByJsonPath(rawSourceContext, "$");

                            if(!rawMap.containsKey("billsExtraInfo")) {
                                rawMap.put("billsExtraInfo", billsExtraInfo);
                                removeBytesData(rawMap);
                                pageSource.setRawSource(JsonUtils.toString(rawMap));
                                savePageSource(pageSource);
                            }
                        } else {
                            PageSource pageSource = new PageSource();
                            pageSource.setCountry(Country.GEORGIA);
                            pageSource.setPageType(PAGE_TYPE_BILL);
                            pageSource.setPageUrl(itemUrl);

                            Map<String, Object> rawMap = new HashMap<>();
//                            rawMap.put("listItem", law);
                            rawMap.put("listItem", billsExtraInfo);
                            rawMap.put("billsExtraInfo", billsExtraInfo);
                            rawMap.put("url", itemUrl);

                            rawMap.put("details", JsonPathUtils.toObject(getResponseBody(BILLS_API_ITEM_BASE_URL + id)));
                            rawMap.put("bill_item", JsonPathUtils.toObject(getResponseBody("https://web-api.parliament.ge/api/bill-packages/" + id + "/bills")));
                            List<Map> billReviewList = JsonPathUtils.toList(getResponseBody(BILL_REVIEW_URL.replace("{billId}", id)));

                            rawMap.put("bill_review", billReviewList);

                            for (Map billReview : billReviewList) {
                                List<List<Map>> billCommitteeList = new ArrayList<>();
                                List<Map> billReviewContentList = new ArrayList<>();
                                String billReviewId = Objects.toString(billReview.get("id"));
                                List<Map> billCommittee = JsonPathUtils.toList(getResponseBody(BILL_COMMITTEE_URL.replace("{billReviewId}", billReviewId)));
                                billCommitteeList.add(billCommittee);
                                billReview.put("bill_committee", billCommitteeList);

                                String billReviewContentResponseBody = getResponseBody(BILL_REVIEW_CONTENT_URL.replace("{billReviewId}", billReviewId));
                                DocumentContext billReviewContentDocumentContext = JsonPathUtils.parseJson(billReviewContentResponseBody);
                                List<Map> billReviewContentItems = JsonPathUtils.findByJsonPath(billReviewContentDocumentContext, "$", new ArrayList<>());
                                for (Map billReviewContentItem : billReviewContentItems) {
                                    String billReviewContentId = Objects.toString(billReviewContentItem.get("id"));
                                    try {
                                        String billReviewContentUrl = BILL_REVIEW_CONTENT_FILE_URL.replace("{billReviewContentId}", billReviewContentId);
                                        byte[] billReviewContentFile = createConnection(billReviewContentUrl).execute().bodyAsBytes();
                                        billReviewContentItem.put("contentUrl", billReviewContentUrl);
//                                billReviewContentItem.put("contentAsBytes", billReviewContentFile);
                                        save(billReviewContentUrl, billReviewContentFile);
                                        billReviewContentItem.put("contentAsXml", byteToXml(billReviewContentFile));
                                        billReviewContentList.add(billReviewContentItem);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                                billReview.put("bill_ReviewContent", billReviewContentList);
                            }

                            pageSource.setRawSource(JsonUtils.toString(rawMap));
                            savePageSource(pageSource);
                        }
                    }
            );
            currentPage++;
        } while (maxPage >= currentPage);
    }

    private void removeBytesData(Map<String, Object> rawMap) {
        List<Map> bill_review = (List<Map>) rawMap.getOrDefault("bill_review", Collections.EMPTY_LIST);
        for (Map billReview : bill_review) {
            List<Map> bill_ReviewContentList = (List<Map>) billReview.getOrDefault("bill_ReviewContent", Collections.EMPTY_LIST);
            for (Map bill_ReviewContent : bill_ReviewContentList) {
                String contentUrl = Objects.toString(bill_ReviewContent.getOrDefault("contentUrl", null));
                List<Integer> contentAsBytesList = (List<Integer>) bill_ReviewContent.getOrDefault("contentAsBytes", Collections.EMPTY_LIST);
                byte[] bytes = convertIntegersToBytes(contentAsBytesList);
                save(contentUrl, bytes);
                bill_ReviewContent.remove("contentAsBytes");
            }
        }
    }

    public static byte[] convertIntegersToBytes(List<Integer> integers) {
        if (integers != null) {
            byte[] outputBytes = new byte[integers.size() * 4];

            for (int i = 0; i < integers.size(); i++) {
                outputBytes[i] = integers.get(i).byteValue();
            }
            return outputBytes;
        } else {
            return null;
        }
    }

    private void collect(String apiBaseUrl) {
        Unirest.config().verifySsl(false);
        int maxPageCount = 9999999;
        int currentPage = 0;
        Integer maxPage;
        do {
            log.info("CurrentPage="+currentPage);
            String currentUrl = apiBaseUrl + currentPage;
            String currentPageSource = getResponseBody(currentUrl);
            DocumentContext context = JsonPathUtils.parseJson(currentPageSource);
            Number totalItemCount = JsonPathUtils.findByJsonPath(context, "$.total");
            maxPage = totalItemCount.intValue()/10;
            log.info("MaxPage="+maxPage);
            if (TEST_MODE && currentPage > maxPageCount) {
                break;
            }
            List<Map<String, Object>> lawsLists = JsonPathUtils.findByJsonPath(context, "$.data", Collections.EMPTY_LIST);

//            for (Map<String, Object> law : lawsLists) {
            ExecutorServiceUtils.forEach(lawsLists, lawsLists.size(), law -> {
                String id = Objects.toString(law.get("id"));
                log.info("Start Collect: " + id);
                String itemUrl = ITEM_BASE_URL + id;
                if (existByPageUrl(itemUrl)) {
                    log.info("Found Page Source: " + itemUrl);
//                    continue;
                } else {
                    PageSource pageSource = new PageSource();
                    pageSource.setCountry(Country.GEORGIA);
                    pageSource.setPageType(PAGE_TYPE_BILL);
                    pageSource.setPageUrl(itemUrl);

                    Map<String, Object> rawMap = new HashMap<>();
                    rawMap.put("listItem", law);
                    rawMap.put("url", itemUrl);

                    rawMap.put("details", JsonPathUtils.toObject(getResponseBody(BILLS_API_ITEM_BASE_URL + id)));
                    rawMap.put("bill_item", JsonPathUtils.toObject(getResponseBody("https://web-api.parliament.ge/api/bill-packages/" + id + "/bills")));
                    List<Map> billReviewList = JsonPathUtils.toList(getResponseBody(BILL_REVIEW_URL.replace("{billId}", id)));

                    rawMap.put("bill_review", billReviewList);

                    for (Map billReview : billReviewList) {
                        List<List<Map>> billCommitteeList = new ArrayList<>();
                        List<Map> billReviewContentList = new ArrayList<>();
                        String billReviewId = Objects.toString(billReview.get("id"));
                        List<Map> billCommittee = JsonPathUtils.toList(getResponseBody(BILL_COMMITTEE_URL.replace("{billReviewId}", billReviewId)));
                        billCommitteeList.add(billCommittee);
                        billReview.put("bill_committee", billCommitteeList);

                        String billReviewContentResponseBody = getResponseBody(BILL_REVIEW_CONTENT_URL.replace("{billReviewId}", billReviewId));
                        DocumentContext billReviewContentDocumentContext = JsonPathUtils.parseJson(billReviewContentResponseBody);
                        List<Map> billReviewContentItems = JsonPathUtils.findByJsonPath(billReviewContentDocumentContext, "$", new ArrayList<>());
                        for (Map billReviewContentItem : billReviewContentItems) {
                            String billReviewContentId = Objects.toString(billReviewContentItem.get("id"));
                            try {
                                String billReviewContentUrl = BILL_REVIEW_CONTENT_FILE_URL.replace("{billReviewContentId}", billReviewContentId);
                                byte[] billReviewContentFile = createConnection(billReviewContentUrl).execute().bodyAsBytes();
                                billReviewContentItem.put("contentUrl", billReviewContentUrl);
//                                billReviewContentItem.put("contentAsBytes", billReviewContentFile);
                                save(billReviewContentUrl, billReviewContentFile);
                                billReviewContentItem.put("contentAsXml", byteToXml(billReviewContentFile));
                                billReviewContentList.add(billReviewContentItem);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        billReview.put("bill_ReviewContent", billReviewContentList);
                    }

                    pageSource.setRawSource(JsonUtils.toString(rawMap));
                    savePageSource(pageSource);
                }
            }
            );
            currentPage++;
        } while (maxPage >= currentPage);
    }

    private synchronized void savePageSource(PageSource pageSource) {
        transactionTemplate.execute(status -> pageSourceRepository.save(pageSource));
    }

    private synchronized boolean existByPageUrl(String itemUrl) {
        return pageSourceRepository.existsByPageUrl(itemUrl);
    }

    private List<String> toBillReviewIdList(Object billReviewList) {
        List<String> billReviewIdList = new ArrayList<>();
        if (billReviewList instanceof Map) {
            String billReviewId = Objects.toString(((Map<?, ?>) billReviewList).getOrDefault("id", null), null);
            if (billReviewId != null) {
                billReviewIdList.add(billReviewId);
            }
        } else if (billReviewList instanceof List) {
            List<Map> mapList = (List<Map>) billReviewList;
            for (Map map : mapList) {
                billReviewIdList.addAll(toBillReviewIdList(map));
            }
        }
        return billReviewIdList;
    }

    private String getResponseBody(String url) {
        try {
            Connection connect = createConnection(url);
            Connection.Response response = connect.execute();
            return response.body();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Connection createConnection(String url) {
        Connection connect = Jsoup.connect(url);
        connect = connect.ignoreContentType(true);
        connect = connect.header("accept-language", "en").header("Accept", "application/json");
        return connect
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.0.0 Safari/537.36")
                .referrer("https://www.google.com/")
                .timeout(50000);
    }



    private void save(String url, byte[] content) {
        if (!downloadedFileRepository.existsByUrl(url)) {
            DownloadedFile downloadedFile = new DownloadedFile();
            downloadedFile.setUrl(url);
//            downloadedFile.setFilename();
            downloadedFile.setContent(content);
            saveDownloadedFile(downloadedFile);
        }
    }

    private synchronized void saveDownloadedFile(DownloadedFile downloadedFile) {
        transactionTemplate.execute(status ->  downloadedFileRepository.save(downloadedFile));
    }
}
