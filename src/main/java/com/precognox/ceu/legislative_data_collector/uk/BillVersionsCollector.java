package com.precognox.ceu.legislative_data_collector.uk;

import com.precognox.ceu.legislative_data_collector.entities.BillVersion;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.utils.DocumentDownloader;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import java.util.List;

@Slf4j
@Service
public class BillVersionsCollector {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DocumentDownloader pdfDownloader;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public void collectBillVersionTexts() {
        log.info("Collecting text of bill versions...");

        String qlString = "SELECT r FROM LegislativeDataRecord r" +
                " INNER JOIN FETCH r.billVersions bv" +
                " WHERE r.country = :country" +
                " AND r.billVersions IS NOT EMPTY";

        List<LegislativeDataRecord> resultList = entityManager.createQuery(qlString, LegislativeDataRecord.class)
                .setParameter("country", Country.UK)
                .getResultList();

        for (LegislativeDataRecord record : resultList) {
            record.getBillVersions().stream()
                    .filter(billVersion -> billVersion.getText() == null)
                    .filter(billVersion -> billVersion.getTextSourceUrl() != null)
                    .forEach(billVersion -> downloadBillText(record, billVersion));
        }
    }

    private void downloadBillText(LegislativeDataRecord record, BillVersion billVersion) {
        pdfDownloader.processWithBrowser(billVersion.getTextSourceUrl()).ifPresent(text -> {
            transactionTemplate.execute(status -> {
                billVersion.setText(text);
                billVersion.setTextSizeChars(TextUtils.getLengthWithoutWhitespace(text));
                entityManager.merge(record);
                log.info("Downloaded bill version text: {}", billVersion.getTextSourceUrl());

                return null;
            });
        });
    }

}
