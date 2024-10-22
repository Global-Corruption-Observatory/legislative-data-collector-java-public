package com.precognox.ceu.legislative_data_collector.uk;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import kong.unirest.GetRequest;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RawSourceCollector {

    @Autowired
    private PageSourceRepository pageSourceRepository;

    private static final int BILLS_BATCH_SIZE = 100;
    private static final String LIST_BILLS_ENDPOINT = CommonConstants.BILLS_API_BASE_URL + "/Bills";

    public void collectBillLists() {
        int currentBatch = 0;
        HttpResponse<JsonNode> listBillsResponse = null;
        JSONArray items = null;

        do {
            log.debug("Fetching batch " + currentBatch + " of bills");

            GetRequest request = Unirest.get(LIST_BILLS_ENDPOINT)
                    .queryString("Take", BILLS_BATCH_SIZE)
                    .queryString("Skip", currentBatch * BILLS_BATCH_SIZE);

            if (!pageSourceRepository.existsByPageUrl(request.getUrl())) {
                listBillsResponse = request.asJson();

                if (listBillsResponse.isSuccess()) {
                    items = listBillsResponse.getBody().getObject().optJSONArray("items");

                    if (items != null && !items.isEmpty()) {
                        PageSource stored = new PageSource();
                        stored.setCountry(Country.UK);
                        stored.setPageType(PageType.BILL_LIST_API_RESPONSE.name().toUpperCase());
                        stored.setPageUrl(request.getUrl());
                        stored.setRawSource(listBillsResponse.getBody().toString());

                        pageSourceRepository.save(stored);

                        log.info("Stored response for request: {}", request.getUrl());
                    }
                } else {
                    log.error("Got error response: {} for request: {}", request.getUrl(), listBillsResponse.getStatus());
                }
            } else {
                log.info("Skipping request: {}", request.getUrl());
            }

            currentBatch++;
        } while (listBillsResponse == null || (listBillsResponse.isSuccess() && items != null && !items.isEmpty()));
    }

}
