package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.repositories.AmendmentRepository;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.transaction.Transactional;

@Slf4j
@Service
public class AmendmentTextCollector {

    private final AmendmentRepository amendmentRepository;
    private final PdfParser pdfParser;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public AmendmentTextCollector(
            AmendmentRepository amendmentRepository, PdfParser pdfParser,
            TransactionTemplate transactionTemplate) {
        this.amendmentRepository = amendmentRepository;
        this.pdfParser = pdfParser;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public void processAllRecords() {
        log.info("Found {} amendments to process", amendmentRepository.countAllWithTextSourceUrl());
        amendmentRepository.streamAllWithTextSourceUrl().forEach(this::processAmendment);
        log.info("Finished processing");
    }

    private void processAmendment(Amendment amendment) {
        pdfParser.tryPdfTextExtraction(amendment.getTextSourceUrl()).ifPresent(text -> {
            amendment.setAmendmentText(text);
            transactionTemplate.execute(status -> amendmentRepository.save(amendment));
        });
    }

}
