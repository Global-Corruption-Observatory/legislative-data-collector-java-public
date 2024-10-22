package com.precognox.ceu.legislative_data_collector.uk;

import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.utils.DocumentDownloader;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import java.util.stream.Stream;

@Slf4j
@Service
public class AmendmentTextDownloader {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DocumentDownloader pdfDownloader;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Transactional
    public void downloadAmendmentTexts() {
        String qlString = "SELECT r FROM LegislativeDataRecord r" +
                " INNER JOIN FETCH r.amendments a" +
                " WHERE r.country = :country" +
                " AND r.amendments IS NOT EMPTY";

        Stream<LegislativeDataRecord> resultList = entityManager.createQuery(qlString, LegislativeDataRecord.class)
                .setParameter("country", Country.UK)
                .getResultStream();

        resultList.forEach(record -> {
            record.getAmendments().stream()
                    .filter(amendment -> amendment.getAmendmentText() == null)
                    .filter(amendment -> amendment.getTextSourceUrl() != null)
                    .forEach(this::downloadAmendmentText);
        });

        log.info("Finished amendment text collection");
    }

    @SneakyThrows
    private void downloadAmendmentText(Amendment amendment) {
        pdfDownloader.processWithBrowser(amendment.getTextSourceUrl()).ifPresent(text -> {
            transactionTemplate.execute(status -> {
                amendment.setAmendmentText(text);
                entityManager.merge(amendment);
                log.info("Downloaded amendment text: {}", amendment.getTextSourceUrl());

                return null;
            });
        });
    }

}
