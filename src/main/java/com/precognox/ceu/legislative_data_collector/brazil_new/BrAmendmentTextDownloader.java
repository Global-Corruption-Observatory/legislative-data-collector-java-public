package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import java.util.Optional;

/**
 * Fetches PDFs where the amendment_text_url is available. {@link BrAmendmentCollector} and {@link SenadoPageParser} must be run before this (for creating the amendment records).
 */
@Slf4j
@Service
public class BrAmendmentTextDownloader {

    private final PdfParser pdfParser;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public BrAmendmentTextDownloader(
            PdfParser pdfParser,
            EntityManager entityManager,
            TransactionTemplate transactionTemplate) {
        this.pdfParser = pdfParser;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Processes the amendment PDFs only (download and extract text).
     */
    void processAmendmentTexts() {
        log.info("Downloading amendment texts...");

        String query = "SELECT a FROM Amendment a WHERE a.amendmentText IS NULL AND a.textSourceUrl IS NOT NULL";

        entityManager.createQuery(query, Amendment.class)
                .getResultStream()
                .peek(amendment -> log.info("Downloading PDF for amendment {}", amendment.getAmendmentId()))
                .forEach(amendment -> {
                    Optional<String> text = pdfParser.tryPdfTextExtraction(amendment.getTextSourceUrl());

                    text.ifPresent(amendmentText -> {
                        amendment.setAmendmentText(amendmentText);
                        log.info("Saving amendment text for amendment {}", amendment.getTextSourceUrl());
                        transactionTemplate.executeWithoutResult(status -> entityManager.merge(amendment));
                    });
                });

        log.info("Finished downloading amendment PDFs");
    }

}
