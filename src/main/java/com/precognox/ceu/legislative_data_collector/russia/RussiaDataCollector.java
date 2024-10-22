package com.precognox.ceu.legislative_data_collector.russia;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.RawPageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.JsonUtils;
import com.precognox.ceu.legislative_data_collector.utils.ObjectPool;
import com.precognox.ceu.legislative_data_collector.utils.XmlUtils;
import com.precognox.ceu.legislative_data_collector.utils.queue.InfinityDataList;
import com.precognox.ceu.legislative_data_collector.utils.queue.InfinityDbBrowser;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverUtil;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverWaitExtend;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.exception.SQLGrammarException;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.transaction.Transactional;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RussiaDataCollector {

    private static final String DETAILS_BASE_URL = "https://sozd.duma.gov.ru/bill/";
    private static final String EXPORTED_FILE_PATH = readParam("EXPORTED_FILE_PATH", null);
    private static final String EXPORTED_FILE_NAME = "Законопроекты на 10.06.2022.xlsx";//Just for test

    private static final int EXCELL_HEAD_ROW = 2;
    public static final String DOCUMENT_VIEW_URL = "http://publication.pravo.gov.ru/Document/View/";
    public static final String DOCUMENT_VIEW_HTML_URL = "http://actual.pravo.gov.ru/text.html#pnum=";
    private final List<String> excellHead = new ArrayList<>();

    private final PrimaryKeyGeneratingRepository keyGeneratingRepository;
    @Autowired
    private PageSourceRepository pageSourceRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;
    private ObjectPool<WebDriver> webDriverPool;

    @Autowired
    public RussiaDataCollector(PrimaryKeyGeneratingRepository keyGeneratingRepository) {
        this.keyGeneratingRepository = keyGeneratingRepository;
    }

    public static String readParam(String key, String defaultValue) {
        return Optional.ofNullable(System.getProperty(key, System.getenv(key))).orElse(defaultValue);
    }

    @Transactional
    public void runCollectionAndParsing() {
        webDriverPool = new ObjectPool<>(this::createWebDriver, WebDriver::close, 40);

        try {
            List<Map<String, String>> itemList = readItemList();
            collectData(itemList);

            parseAllRowData();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            log.info("START finally");
            webDriverPool.close();
        }
    }

    private WebDriver createWebDriver() {
        return new WebDriverUtil().createChromeWebDriver("");
    }

    private Function<String, String> getPageCache() {
        return url -> {
            if (StringUtils.isBlank(url)) {
                return null;
            }
            if (url.contains(DOCUMENT_VIEW_URL)) {
                url = url.replace(DOCUMENT_VIEW_URL, DOCUMENT_VIEW_HTML_URL);
            }
            String result = pageSourceRepository.findByPageUrl(url).map(pageSource -> pageSource.getRawSource()).orElse(null);
            if (result != null) {
                return result;
            }
            if (url.contains(DOCUMENT_VIEW_HTML_URL)) {
                WebDriver webDriver = webDriverPool.borrowObject();
                webDriver.get(url);
                WebDriverWaitExtend.waitSec(2);
                WebDriver iFrame = webDriver.switchTo().frame(webDriver.findElement(By.xpath("//iframe[@class=\"doc-body\"]")));
                String lawText = iFrame.findElement(By.xpath("//body")).getText();
                webDriver.switchTo().defaultContent();
                webDriverPool.returnObject(webDriver);

                if (lawText != null) {
                    PageSource pageSource = new PageSource();
                    pageSource.setCountry(Country.RUSSIA);
                    pageSource.setPageType(PageType.law_text.name());
                    pageSource.setPageUrl(url);
                    pageSource.setRawSource(lawText);
                    saveInNewTransaction(pageSource);
                }
                return lawText;
            } else {
                try {
                    result = XmlUtils.openAsText(url);
                    if (result != null) {
                        PageSource pageSource = new PageSource();
                        pageSource.setCountry(Country.RUSSIA);
                        pageSource.setPageType(PageType.amendment.name());
                        pageSource.setPageUrl(url);
                        pageSource.setRawSource(result);
                        saveInNewTransaction(pageSource);
                    }
                    return result;
                } catch (IOException e) {
                    log.error("downloadFileAndConvertToText ", e);
                    return null;
                }
            }
        };
    }

    //Debug only
    public void parseOneRowData(String billId) {
        Optional<PageSource> item = pageSourceRepository.findByPageUrl(DETAILS_BASE_URL + billId);
        if (item.isPresent()) {
            LegislativeDataRecord record = RussiaParser.parseRowData(item.get(), getPageCache());
            log.info("record.id=" + record.getId());
            saveInNewTransaction(record);
        }
    }

    /**
     * Parses the stored data in the database.
     */
    private void parseAllRowData() {
        int pageSize = 20;
        int currentPage = 0;
        ExecutorService executorService = Executors.newFixedThreadPool(25);
        InfinityDbBrowser<PageSource> infinityDbBrowser = new InfinityDbBrowser<>(pageSize, pageable -> {
            log.info("current PageNumber = " + pageable.getPageNumber());
            return pageSourceRepository.findAllByCountryAndPageType(pageable, Country.RUSSIA, PageType.bill.name());
        });
        infinityDbBrowser.setCurrentPage(currentPage);

        //this is the list of stored sources
        InfinityDataList<PageSource> dataList = new InfinityDataList<>(executorService, infinityDbBrowser);

        dataList.forEach(data -> {
            try {
                log.info("START data.getId = " + data.getId());
                saveInNewTransaction(RussiaParser.parseRowData(data, getPageCache()));
                log.info("END data.getId = " + data.getId());
            } catch (Exception ex) {
                log.error("Failed to parse: " + data.getPageUrl(), ex);
            }
        });
        log.info("FINISH dataList");
    }

    public void postProcess1() {
        int pageSize = 20;
        int currentPage = 0;
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        InfinityDbBrowser<LegislativeDataRecord> infinityDbBrowser = new InfinityDbBrowser<>(pageSize, pageable -> {
            log.debug("current PageNumber = " + pageable.getPageNumber());
            return keyGeneratingRepository.findAll(pageable); // totdo add  Country.RUSSIA
        });
        infinityDbBrowser.setCurrentPage(currentPage);
        InfinityDataList<LegislativeDataRecord> dataList = new InfinityDataList<>(executorService, infinityDbBrowser);

        dataList.forEach(data -> {
            try {
                log.debug("START data.getId = " + data.getId());
                replaceModifiedLaws(data);
                log.debug("END data.getId = " + data.getId());
            } catch (Exception ex) {
                log.error("Failed to parse: " + data.getBillId(), ex);
                ex.printStackTrace();
            }
        });
        log.info("FINISH dataList");
    }

    public void postProcess2() {
        int pageSize = 20;
        int currentPage = 0;
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        InfinityDbBrowser<LegislativeDataRecord> infinityDbBrowser = new InfinityDbBrowser<>(pageSize, pageable -> {
            log.debug("current PageNumber = " + pageable.getPageNumber());
            return keyGeneratingRepository.findAll(pageable); // totdo add  Country.RUSSIA
        });
        infinityDbBrowser.setCurrentPage(currentPage);
        InfinityDataList<LegislativeDataRecord> dataList = new InfinityDataList<>(executorService, infinityDbBrowser);

        dataList.forEach(data -> {
            try {
                log.debug("START data.getId = " + data.getId());
                replaceAffectedLaws(data);
                log.debug("END data.getId = " + data.getId());
            } catch (Exception ex) {
                log.error("Failed to parse: " + data.getBillId(), ex);
            }
        });
        log.info("FINISH dataList");
    }

    private LegislativeDataRecord replaceModifiedLaws(LegislativeDataRecord data) {
        Set<String> affectedLawIdSet = new HashSet<>();
        Set<String> affectedLaws = data.getModifiedLaws();
        for (String affectedLaw : affectedLaws) {
            List<LegislativeDataRecord> affectedLawDataList = keyGeneratingRepository.findByCountryAndBillTitle(Country.RUSSIA, affectedLaw);
            if (affectedLawDataList.isEmpty()) {
                affectedLawIdSet.add(affectedLaw);
            } else {
                affectedLawDataList.forEach(dataRecord -> affectedLawIdSet.add(dataRecord.getBillId()));
            }
        }
        data.setModifiedLaws(affectedLawIdSet);
        mergeInNewTransaction(data);
        return data;
    }

    private LegislativeDataRecord replaceAffectedLaws(LegislativeDataRecord data) {
        List<LegislativeDataRecord> dataList = keyGeneratingRepository.findByModifiedLawId(data.getBillId());
        data.setAffectingLawsCount(dataList.size());

        for (LegislativeDataRecord legislativeDataRecord : dataList) {
            if (data.getAffectingLawsFirstDate() == null) {
                data.setAffectingLawsFirstDate(legislativeDataRecord.getDateIntroduction());
            }
            if (data.getAffectingLawsFirstDate() != null
                    && data.getAffectingLawsFirstDate().isAfter(legislativeDataRecord.getDateIntroduction())) {
                data.setAffectingLawsFirstDate(legislativeDataRecord.getDateIntroduction());
            }
        }

        mergeInNewTransaction(data);
        return data;
    }

    private synchronized void saveInNewTransaction(PageSource data) {
        transactionTemplate.execute(status -> pageSourceRepository.save(data));
    }

    private synchronized void saveInNewTransaction(LegislativeDataRecord data) {
        keyGeneratingRepository.save(data);
    }

    private void mergeInNewTransaction(LegislativeDataRecord data) {
        keyGeneratingRepository.mergeInNewTransaction(data);
    }

    /**
     * Iterates on the rows of the downloaded XLS file, then fetches the bill details for each row, and stores in in the database.
     *
     * @param itemList The list of rows in the XLS.
     */
    private void collectData(List<Map<String, String>> itemList) {
        itemList.stream().forEach(item -> collectDataWithRetry(item));
    }

    private void collectDataWithRetry(Map<String, String> item) {
        try {
            collectData(item);
        } catch (SQLGrammarException ex) {
            log.error("Retry collectData", ex);
            collectData(item);
        }
    }

    /**
     * Fetches a bill's detail page from the website and stores the raw source of the page. A {@link RawPageSource} entity is saved to the database, and an empty {@link LegislativeDataRecord} linked to it.
     *
     * Inside the {@link RawPageSource}, the URL is set to the bill's page on the website. The source will contain a JSON with the following keys:
     *  - listItem: the row from the XLS file
     *  - details: the HTML page source of the bill's page
     *
     * @param item A row from the downloaded XLS. Only the bill ID is used from this.
     */
    private void collectData(Map<String, String> item) {
        LegislativeDataRecord data = new LegislativeDataRecord();
        data.setCountry(Country.RUSSIA);
        RawPageSource r = new RawPageSource();

        //this is used to build the JSON which will be stored as the "raw source" in the DB
        Map<String, Object> rawMap = new HashMap<>();
        data.setRawPageSource(r);

        rawMap.put("listItem", item);
        String billID = item.get("Номер, наименование");
        billID = billID.split("[^\\d-]")[0];

        String detailsUrl = DETAILS_BASE_URL + billID;
        r.setUrl(detailsUrl);

        Optional<LegislativeDataRecord> dataRecord = keyGeneratingRepository.findByRawPageSourceUrl(detailsUrl);
        if (!dataRecord.isPresent()) {
            log.info("Download details: " + detailsUrl);
            String details = Unirest.get(DETAILS_BASE_URL + billID).asString().getBody();
            rawMap.put("details", details);

            r.setRawSource(JsonUtils.toString(rawMap));
            keyGeneratingRepository.save(data);
        } else {
            log.info("Found details: " + detailsUrl);
        }
    }

    /**
     * Reads the exported XLS file from the website
     *
     * @return A List of rows from the XLS file, elements are Maps with the table header as the key for each value.
     *
     * @throws IOException If there is a problem reading the XLS file.
     */
    private List<Map<String, String>> readItemList() throws IOException {
        List<Map<String, String>> itemList = new ArrayList<>();
        try (InputStream is = new FileInputStream(EXPORTED_FILE_PATH)) {

            XSSFWorkbook workbook = new XSSFWorkbook(is);
            XSSFSheet sheet = workbook.getSheetAt(0);
            int currentRow = 0;
            //Iterate through each rows one by one
            Iterator<Row> rowIterator = sheet.iterator();
            while (rowIterator.hasNext()) {
                currentRow++;
                Row row = rowIterator.next();

                if (currentRow == EXCELL_HEAD_ROW) {
                    excellHead.addAll(getRowAsList(row).stream().filter(headText -> !headText.isEmpty()).collect(Collectors.toList()));
                } else if (currentRow > EXCELL_HEAD_ROW) {
                    Map<String, String> rowAsMap = new HashMap<>();
                    List<String> rowAsList = getRowAsList(row);
                    for (int i = 0; i < excellHead.size(); i++) {
                        String headText = excellHead.get(i);
                        if (rowAsList.size() > i) {
                            rowAsMap.put(headText, rowAsList.get(i));
                        }
                    }
                    itemList.add(rowAsMap);
                }
            }
        }

        return itemList;
    }

    private List<String> getRowAsList(Row row) {
        List<String> cells = new ArrayList<>();
        Iterator<Cell> cellIterator = row.cellIterator();
        while (cellIterator.hasNext()) {
            Cell cell = cellIterator.next();
            //Check the cell type and format accordingly
            switch (cell.getCellType()) {
                case NUMERIC:
                    cells.add(Integer.toString(Double.valueOf(cell.getNumericCellValue()).intValue()));
                    break;
                case STRING:
                    cells.add(cell.getStringCellValue());
                    break;
                default:
                    cells.add(cell.getStringCellValue());
                    break;
            }
        }
        return cells;
    }
}
