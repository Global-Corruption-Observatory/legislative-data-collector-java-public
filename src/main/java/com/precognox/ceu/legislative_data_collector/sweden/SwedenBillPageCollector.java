package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.BillUrl;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.BillUrlRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.transaction.Transactional;
import java.time.LocalDate;

@Slf4j
@Service
public class SwedenBillPageCollector {

    private final BillUrlRepository billUrlRepository;
    private final PageSourceRepository pageSourceRepository;
    private final TransactionTemplate transactionTemplate;

    public SwedenBillPageCollector(
            BillUrlRepository billUrlRepository,
            PageSourceRepository pageSourceRepository,
            TransactionTemplate transactionTemplate) {
        this.billUrlRepository = billUrlRepository;
        this.pageSourceRepository = pageSourceRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public void collectAllPages() {
        billUrlRepository.streamUnprocessedUrls(Country.SWEDEN).forEach(this::downloadPage);
    }

    private void downloadPage(BillUrl billUrl) {
        HttpResponse<String> resp = Unirest.get(billUrl.getUrl()).asString();

        if (resp.isSuccess()) {
            PageSource stored = new PageSource();
            stored.setCountry(Country.SWEDEN);
            stored.setPageType(PageType.BILL.name());
            stored.setPageUrl(billUrl.getUrl());
            stored.setCleanUrl(billUrl.getUrl());
            stored.setSize(resp.getBody().length());
            stored.setRawSource(resp.getBody());
            stored.setCollectionDate(LocalDate.now());

            log.info("Storing page: {}", billUrl.getUrl());
            transactionTemplate.execute(status -> pageSourceRepository.save(stored));
        } else {
            log.error("{} error response for page: {}", resp.getStatus(), billUrl.getUrl());
        }
    }

}
