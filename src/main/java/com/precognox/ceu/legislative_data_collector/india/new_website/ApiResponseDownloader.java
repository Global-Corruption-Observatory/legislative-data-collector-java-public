package com.precognox.ceu.legislative_data_collector.india.new_website;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.india.PageType;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.time.LocalDate;

@Slf4j
@Service
public class ApiResponseDownloader {

    private final PageSourceRepository pageSourceRepository;

    private static final int PAGE_SIZE = 100;
    private static final String API_URL_TEMPLATE = "https://sansad.in/api_rs/legislation/getBills?loksabha=&sessionNo=&billName=&house=&ministryName=&billType=&billCategory=&billStatus=&introductionDateFrom=&introductionDateTo=&passedInLsDateFrom=&passedInLsDateTo=&passedInRsDateFrom=&passedInRsDateTo=&page={0}&size={1}&locale=en&sortOn=billIntroducedDate&sortBy=desc";

    @Autowired
    public ApiResponseDownloader(PageSourceRepository pageSourceRepository) {
        this.pageSourceRepository = pageSourceRepository;
    }

    public void downloadPages() {
        int currentPage = 1;
        int totalPages = 0;

        do {
            String apiUrl = MessageFormat.format(API_URL_TEMPLATE, currentPage, PAGE_SIZE);
            HttpResponse<JsonNode> resp = Unirest.get(apiUrl).asJson();

            if (resp.isSuccess()) {
                PageSource stored = new PageSource();
                stored.setCountry(Country.INDIA);
                stored.setPageType(PageType.BILL_LIST.name());
                stored.setPageUrl(apiUrl);
                stored.setRawSource(resp.getBody().toPrettyString());
                stored.setMetadata("Page " + currentPage);
                stored.setCollectionDate(LocalDate.now());
                stored.setSize(resp.getBody().toPrettyString().length());

                pageSourceRepository.save(stored);
                log.info("Stored page {} of API response with size: {}", currentPage, stored.getSize());

                JSONObject metadata = resp.getBody().getObject().optJSONObject("_metadata");

                if (metadata != null) {
                    totalPages = metadata.optInt("totalPages");
                }
            } else {
                log.error("Error response received from API: {}, URL was: {}", resp.getStatus(), apiUrl);
            }

            currentPage++;
        } while (currentPage <= totalPages);
    }

}
