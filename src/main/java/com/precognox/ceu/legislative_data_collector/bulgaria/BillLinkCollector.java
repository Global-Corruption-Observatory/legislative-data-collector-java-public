package com.precognox.ceu.legislative_data_collector.bulgaria;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import kong.unirest.HttpResponse;
import kong.unirest.MimeTypes;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BillLinkCollector {

    private final PageSourceRepository pageSourceRepository;
    private static final String API_URL = "https://www.parliament.bg/api/v1/fn-bills";

    public BillLinkCollector(PageSourceRepository pageSourceRepository) {
        this.pageSourceRepository = pageSourceRepository;
    }

    public void downloadBillList() {
        HttpResponse<String> allBillsResp =
                Unirest.post(API_URL).contentType(MimeTypes.JSON).body("{\"search\":1}").asString();

        if (allBillsResp.isSuccess()) {
            PageSource storedSource = PageSource.builder()
                    .pageUrl(API_URL)
                    .rawSource(allBillsResp.getBody())
                    .country(Country.BULGARIA)
                    .pageType(PageType.BILL_LIST_JSON.name())
                    .build();

            pageSourceRepository.save(storedSource);
        } else {
            log.error("Error returned from API: {} - {}", allBillsResp.getStatus(), allBillsResp.getBody());
        }
    }

}
