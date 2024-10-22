package com.precognox.ceu.legislative_data_collector.uk;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.ImpactAssessment;
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
public class ImpactAssessmentTextDownloader {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DocumentDownloader pdfDownloader;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public void downloadIaTexts() {
        String qlString = "SELECT r FROM LegislativeDataRecord r" +
                " INNER JOIN FETCH r.impactAssessments ia" +
                " WHERE r.country = :country" +
                " AND r.impactAssessments IS NOT EMPTY";

        List<LegislativeDataRecord> resultList = entityManager.createQuery(qlString, LegislativeDataRecord.class)
                .setParameter("country", Country.UK)
                .getResultList();

        for (LegislativeDataRecord record : resultList) {
            record.getImpactAssessments().stream()
                    .filter(ia -> ia.getText() == null)
                    .filter(ia -> ia.getOriginalUrl() != null)
                    .forEach(this::downloadIaText);
        }
    }

    private void downloadIaText(ImpactAssessment impactAssessment) {
        pdfDownloader.processWithBrowser(impactAssessment.getOriginalUrl()).ifPresent(text -> {
            transactionTemplate.execute(status -> {
                impactAssessment.setText(text);
                impactAssessment.setSize(TextUtils.getLengthWithoutWhitespace(text));
                entityManager.merge(impactAssessment);
                log.info("Downloaded IA text: {}", impactAssessment.getOriginalUrl());

                return null;
            });
        });
    }

}
