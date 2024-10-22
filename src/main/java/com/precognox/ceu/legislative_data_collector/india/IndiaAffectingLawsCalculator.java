package com.precognox.ceu.legislative_data_collector.india;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
public class IndiaAffectingLawsCalculator {

    private final EntityManager entityManager;

    private static final List<String> AMENDMENT_LABELS = List.of(
            "(repeal) bill",
            "repeal bill",
            "(amendment) bill",
            "amendment bill",
            "(amending) bill",
            "amending bill",
            "repealing bill"
    );

    @Autowired
    public IndiaAffectingLawsCalculator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Iterates on all bills and finds the modified and affecting laws for each of them. Works with the bill titles only, with the method described in the variable guide.
     */
    @Transactional
    public void collectAllAffectingLaws() {
        String query = "SELECT b FROM LegislativeDataRecord b " +
                "WHERE b.billStatus = :status " +
                "AND (LOWER(b.billTitle) LIKE '%(repeal) bill%' " +
                "OR LOWER(b.billTitle) LIKE '%repeal bill%' " +
                "OR LOWER(b.billTitle) LIKE '%(amendment) bill%' " +
                "OR LOWER(b.billTitle) LIKE '%amendment bill%' " +
                "OR LOWER(b.billTitle) LIKE '%(amending) bill%' " +
                "OR LOWER(b.billTitle) LIKE '%amending bill%' " +
                "OR LOWER(b.billTitle) LIKE '%repealing bill%')";

        Stream<LegislativeDataRecord> amendingBills = entityManager.createQuery(query, LegislativeDataRecord.class)
                .setParameter("status", LegislativeDataRecord.BillStatus.PASS)
                .getResultStream();

        amendingBills
                .peek(amendingBill -> log.info("Updating bill {}", amendingBill.getRecordId()))
                .forEach(this::handleModifications);

        log.info("Done calculating modified laws");
    }

    private void handleModifications(LegislativeDataRecord amendingBill) {
        amendingBill.setOriginalLaw(false);
        amendingBill.setModifiedLawsCount(1);

        Optional<LegislativeDataRecord> originalLaw = findOriginalLaw(amendingBill);

        originalLaw.ifPresentOrElse(origLaw -> {
            amendingBill.getModifiedLaws().add(origLaw.getBillId());

            if (origLaw.getAffectingLawsCount() == null) {
                origLaw.setAffectingLawsCount(1);
            } else {
                origLaw.setAffectingLawsCount(origLaw.getAffectingLawsCount() + 1);
            }

            if (amendingBill.getDatePassing() != null
                    && (origLaw.getAffectingLawsFirstDate() == null || amendingBill.getDatePassing().isBefore(origLaw.getAffectingLawsFirstDate()))) {
                origLaw.setAffectingLawsFirstDate(amendingBill.getDatePassing());
            }

            log.info("Updating bill: {}", origLaw.getRecordId());
        }, () -> {
            log.info(
                    "Could not find original law for amending bill: {} {}",
                    amendingBill.getRecordId(),
                    amendingBill.getBillTitle()
            );
        });
    }

    /**
     * Finds the original law for the given amending bill, based on bill titles (tries to recreate the title of the original law from the title of the amending law, by removing "amendment" and other similar keywords. Then it checks the database for the matching original law).
     *
     * @param amendingBill The bill which has "amendment" in the title.
     *
     * @return The matching original law, if found.
     */
    private Optional<LegislativeDataRecord> findOriginalLaw(LegislativeDataRecord amendingBill) {
        String cleanTitle = amendingBill.getBillTitle().toLowerCase();

        Optional<String> originalBillName = AMENDMENT_LABELS.stream()
                .filter(cleanTitle::contains)
                .map(cleanTitle::indexOf)
                .map(index -> cleanTitle.substring(0, index))
                .map(titlePart -> titlePart + "bill")
                .findFirst();

        if (originalBillName.isPresent()) {
            String q = "SELECT r FROM LegislativeDataRecord r" +
                    " WHERE r.billStatus = :status" +
                    " AND r.originalLaw = true" +
                    " AND r.datePassing < :amendingLawDatePassing" +
                    " AND LOWER(r.billTitle) LIKE :titlePart";

            List<LegislativeDataRecord> matchingBills = entityManager.createQuery(q, LegislativeDataRecord.class)
                    .setParameter("status", LegislativeDataRecord.BillStatus.PASS)
                    .setParameter("titlePart", "%" + originalBillName.get() + "%")
                    .setParameter("amendingLawDatePassing", amendingBill.getDatePassing())
                    .getResultList();

            if (matchingBills.isEmpty()) {
                log.info("Matching law not found for title: {}", originalBillName.get());
            } else if (matchingBills.size() == 1) {
                return Optional.of(matchingBills.get(0));
            } else {
                log.info("Multiple matching bills found for title: {}", originalBillName.get());
            }
        }

        return Optional.empty();
    }

}
