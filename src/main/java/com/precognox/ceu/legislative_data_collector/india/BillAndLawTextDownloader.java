package com.precognox.ceu.legislative_data_collector.india;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.DocUtils;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.transaction.Transactional;
import java.util.Optional;

@Slf4j
@Service
public class BillAndLawTextDownloader {

    @Autowired
    private PrimaryKeyGeneratingRepository billRepository;
    @Autowired
    private PdfParser pdfParser;
    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    private TransactionTemplate transactionTemplate;

    @Transactional
    public void downloadBillAndLawTexts() {
        transactionTemplate = new TransactionTemplate(platformTransactionManager);

        log.info("Processing bill and law text links...");

        billRepository.streamAllForBillAndLawTextDownloader(Country.INDIA).forEach(record -> {
            downloadBillText(record);
            downloadLawText(record);
        });
    }

    public void downloadBillText(LegislativeDataRecord record) {
        if (record.getBillText() != null) {
            log.info("Bill text already downloaded for bill {}, skipping", record.getBillId());
        } else {
            if (record.getBillTextUrl() != null) {
                Optional<String> text = record.getBillTextUrl().endsWith(".doc")
                        ? DocUtils.getTextFromDoc(record.getBillTextUrl())
                        : pdfParser.tryPdfTextExtraction(record.getBillTextUrl());

                if (text.isPresent()) {
                    transactionTemplate.execute(status -> {
                        record.setBillText(text.get());
                        billRepository.merge(record);

                        log.info("Collected bill text for bill {}", record.getBillId());

                        return record;
                    });
                }
            }
        }
    }

    public void downloadLawText(LegislativeDataRecord record) {
        if (record.getLawText() != null) {
            log.info("Law text already downloaded for bill {}, skipping", record.getBillId());
        } else {
            if (record.getLawTextUrl() != null) {
                Optional<String> text = record.getLawTextUrl().endsWith(".doc")
                        ? DocUtils.getTextFromDoc(record.getLawTextUrl())
                        : pdfParser.tryPdfTextExtraction(record.getLawTextUrl());

                if (text.isPresent()) {
                    transactionTemplate.execute(status -> {
                        record.setLawText(text.get());
                        billRepository.merge(record);

                        log.info("Collected law text for bill {}", record.getBillId());

                        return record;
                    });
                }
            }
        }
    }

}
