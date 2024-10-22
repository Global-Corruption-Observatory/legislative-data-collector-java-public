package com.precognox.ceu.legislative_data_collector.australia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precognox.ceu.legislative_data_collector.australia.json.DataJson;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.utils.BaseUtils.readParam;

@Slf4j
@Service
public class AustraliaCollector {

    private static final boolean ENABLE_WEB1_CONFIG_RESULT_CACHE =
            Boolean.valueOf(readParam("ENABLE_WEB1_CONFIG_RESULT_CACHE", Boolean.TRUE.toString()));
    public static final String CONFIG_RESULT_PAGE_TYPE = "config_result";

    public static final int CONNECTION_TIMEOUT = Long.valueOf(Duration.ofMinutes(20l).toMillis()).intValue();
    public static final String PAGE_END_REGEX = "\"page_end\":[ ]*([\\d]+)[^\\d]";
    public static final String PAGE_START_REGEX = "\"page_start\":[ ]*([\\d]+)[^\\d]";

    private final ObjectMapper objectMapper = new ObjectMapper();
    public static final String CONFIG_DIR = System.getProperty("user.dir", "") + "/configs/australia/";
    public static final String CONFIG_PATH = CONFIG_DIR + "scraper.json";

    public static final String PYETL_URL = readParam("PYETL_URL", null);
    public static final String PYETL_USER = readParam("PYETL_USER", null);
    public static final String PYETL_PASSWORD = readParam("PYETL_PASSWORD", null);


    @Autowired
    private PrimaryKeyGeneratingRepository keyGeneratingRepository;

    @Autowired
    private PageSourceRepository pageSourceRepository;

    private synchronized void save(LegislativeDataRecord data) {
        keyGeneratingRepository.save(data);
    }

    public void collectAndParseWithPythonDownloader() {
        File configFile = new File(CONFIG_PATH);
        log.info("Current config: " + configFile.getAbsolutePath());

        if (!configFile.exists()) {
            log.error("Missing config file! Path: " + configFile.getAbsolutePath());
        }

        String configString = readConfigString(configFile);
        configString = configString.replace("\"type\": \"elastic\"", "\"type\": \"result\"");

        HttpResponse<String> response = Unirest.get(
                        "https://www.aph.gov.au/Parliamentary_Business/Bills_Legislation/Bills_Search_Results?page=1&drt=1&drv=0&drvH=0&pnu=0&pnuH=47&ps=100&ito=1&q=&bs=1&pbh=1&bhor=1&ra=1&np=1&pmb=1&g=1&st=2")
                .asString();
        Document page = Jsoup.parse(response.getBody());
        Optional<Integer> pageEnd = getMaxPageNumber(page);

        if (pageEnd.isPresent()) {
            for (int currentPage = 1; currentPage <= pageEnd.get(); currentPage++) {
                String newConfigString = configString.replaceFirst(PAGE_END_REGEX, "\"page_end\": " + currentPage)
                        .replaceFirst(PAGE_START_REGEX, "\"page_start\": " + currentPage);
                DataJson[] results = readConfigResult(newConfigString);
                log.info("Current page (" + currentPage + ") results size: " + results.length);

                for (DataJson dataJson : results) {
                    LegislativeDataRecord dataRecord = AustraliaParser.toDataRecord(dataJson);
                    if (!keyGeneratingRepository.existsByBillPageUrl(dataRecord.getBillPageUrl())) {
                        save(dataRecord);
                    } else {
                        log.info("Record already exists: " + dataRecord.getBillId());
                    }
                }
            }
        }

        log.info("Collection finished");
    }

    private Optional<Integer> getMaxPageNumber(Document page) {
        Optional<Element> totalResultsElement =
                Optional.ofNullable(page.selectFirst("#main_0_content_0_pTotalResults"));

        if (totalResultsElement.isPresent()) {
            // Extract the text content
            String totalResultsText = totalResultsElement.get().text();

            // Use a regular expression to extract the number
            String totalResultsNumber = totalResultsText.replaceAll("\\D+", "");

            log.info("Total Results: " + totalResultsNumber);
            return Optional.of((int) Math.ceil(Double.parseDouble(totalResultsNumber) / 10));
        } else {
            log.warn("Element not found.");
            return Optional.empty();
        }
    }

    private DataJson[] readConfigResult(String config) {
        Optional<PageSource> configResultCache = Optional.empty();

        if (ENABLE_WEB1_CONFIG_RESULT_CACHE) {
            configResultCache = pageSourceRepository.findByPageUrlAndMetadata(PYETL_URL, config);
        }

        String responseString;

        if (configResultCache.isPresent()) {
            responseString = configResultCache.get().getRawSource();
        } else {
            Unirest.config().reset();
            Unirest.config().verifySsl(false);

            HttpResponse<String> response = Unirest.post(PYETL_URL)
                    .header("Content-Type", "application/json")
                    .basicAuth(PYETL_USER, PYETL_PASSWORD)
                    .body(config)
                    .connectTimeout(CONNECTION_TIMEOUT)
                    .socketTimeout(CONNECTION_TIMEOUT)
                    .asString();

            responseString = response.getBody();

            PageSource pageSource = new PageSource();
            pageSource.setCountry(Country.AUSTRALIA);
            pageSource.setPageType(CONFIG_RESULT_PAGE_TYPE);
            pageSource.setPageUrl(PYETL_URL);
            pageSource.setMetadata(config);
            pageSource.setRawSource(responseString);

            if (ENABLE_WEB1_CONFIG_RESULT_CACHE) {
                pageSourceRepository.save(pageSource);
            }
        }

        try {
            return objectMapper.readValue(responseString, DataJson[].class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String readConfigString(File configFile) {
        try {
            return FileUtils.readFileToString(configFile, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
