package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
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
 * Downloads the bill pages from the Camara website, for all records that have a Camara page URL. Stores results in the page_source table. Skips pages that are already downloaded, so this class can be run multiple times.
 */
@Slf4j
@Service
public class CamaraPageDownloader {

    private final LegislativeDataRepository recordRepository;
    private final PageSourceRepository pageSourceRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public CamaraPageDownloader(
            LegislativeDataRepository recordRepository,
            PageSourceRepository pageSourceRepository,
            TransactionTemplate transactionTemplate) {
        this.recordRepository = recordRepository;
        this.pageSourceRepository = pageSourceRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public void downloadAll() {
        recordRepository.streamAllWithCamaraPageUrl()
                .map(this::downloadCamaraPage)
                .filter(Objects::nonNull)
                .peek(pageSource -> log.info(
                        "Downloaded Camara page with size: {} from URL: {}",
                        pageSource.getSize(),
                        pageSource.getPageUrl()
                ))
                .forEach(entity ->
                        transactionTemplate.executeWithoutResult(status -> pageSourceRepository.save(entity)));
    }

    private PageSource downloadCamaraPage(LegislativeDataRecord record) {
        String url = record.getBrazilCountrySpecificVariables().getCamaraPageUrl();

        if (!pageSourceRepository.existsByPageUrl(url)) {
            HttpResponse<String> resp = Unirest.get(url).asString();

            if (resp.isSuccess()) {
                PageSource source = new PageSource(
                        Country.BRAZIL,
                        PageType.CAMARA_BILL_DETAILS.name(),
                        url,
                        resp.getBody()
                );

                source.setCleanUrl(url);

                return source;
            }

            log.error("Failed to download Camara page: {}, response: {}", url, resp.getStatus());
        } else {
            log.info("Camara page already downloaded: {}", url);
        }

        return null;
    }

}
