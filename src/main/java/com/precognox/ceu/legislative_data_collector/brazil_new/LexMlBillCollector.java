package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.entities.BillUrl;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.BillUrlRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.transaction.Transactional;
import java.util.Objects;

/**
 * Downloads bill pages from the LexML website. The bill links were collected in a previous step by {@link LexMlBillListCollector}. Therefore, this class only reads the bill_links table and stores the downloaded pages in the page_source table.
 */
@Slf4j
@Service
public class LexMlBillCollector {

    private final BillUrlRepository billLinkRepository;
    private final PageSourceRepository pageSourceRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public LexMlBillCollector(
            BillUrlRepository billLinkRepository,
            PageSourceRepository pageSourceRepository,
            TransactionTemplate transactionTemplate) {
        this.billLinkRepository = billLinkRepository;
        this.pageSourceRepository = pageSourceRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public void downloadAll() {
        //iterate on stored bill links, download pages
        billLinkRepository.streamUnprocessedUrls(Country.BRAZIL)
                .map(this::downloadBill)
                .filter(Objects::nonNull)
                .peek(pageSource -> log.info("Downloaded bill page with size: {} from URL: {}",
                        pageSource.getSize(),
                        pageSource.getPageUrl()
                ))
                .forEach(page -> transactionTemplate.executeWithoutResult(status -> pageSourceRepository.save(page)));
    }

    private PageSource downloadBill(BillUrl billUrl) {
        HttpResponse<String> response = Unirest.get(billUrl.getUrl()).asString();

        if (response.isSuccess()) {
            PageSource source = new PageSource(Country.BRAZIL,
                    PageType.LEXML_BILL_DETAILS.name(),
                    billUrl.getUrl(),
                    response.getBody()
            );

            //duplication filtering works by cleanUrl
            source.setCleanUrl(billUrl.getUrl());

            return source;
        } else {
            log.error("Failed to download bill: {}, response: {}", billUrl.getUrl(), response.getStatus());
        }

        return null;
    }

}
