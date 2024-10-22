package com.precognox.ceu.legislative_data_collector.colombia.affecting;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sets affecting laws_count and affecting_first_date
 */
@Service
public class AffectingLawCalculatorDummy {

    private final EntityManager entityManager;
    private final LegislativeDataRepository legislativeDataRepository;

    @Autowired
    public AffectingLawCalculatorDummy(EntityManager entityManager, LegislativeDataRepository legislativeDataRepository) {
        this.entityManager = entityManager;
        this.legislativeDataRepository = legislativeDataRepository;
    }

    @Transactional
    public void processAffectingLaws() {
        String affectingLawsCountQuery = "SELECT COUNT(al.record_id) AS cnt, al.modified_law_id" +
                " FROM {h-schema}affected_laws as al" +
                " JOIN {h-schema}bill_main_table as bmt" +
                " ON al.record_id = bmt.id" +
                " WHERE bmt.record_id LIKE 'CO%'" +
                " GROUP BY al.modified_law_id ORDER BY cnt DESC";

        List<Object[]> resultList = entityManager.createNativeQuery(affectingLawsCountQuery).getResultList();

        resultList.forEach(record -> {
            BigInteger count = (BigInteger) record[0];
            String affectedLawId = (String) record[1];

            //split into year and law number, then search
            if (affectedLawId.contains("/")) {

                Optional<LegislativeDataRecord> affectedLaw =
                        legislativeDataRepository.findByLawId(affectedLawId);

                affectedLaw.ifPresent(law -> {
                    law.setAffectingLawsCount(count.intValue());

                    String affectingLawsQuery = "select record_id from {h-schema}affected_laws where modified_law_id = :law_id";

                    List<Integer> affectingLaws = entityManager.createNativeQuery(affectingLawsQuery)
                            .setParameter("law_id", law.getLawId())
                            .getResultList();

                    Optional<LocalDate> earliestDate = affectingLaws.stream()
                            .map(id -> legislativeDataRepository.findById(id.longValue()))
                            .filter(Optional::isPresent)
                            .map(entity -> entity.get().getDatePassing())
                            .filter(Objects::nonNull)
                            .min(LocalDate::compareTo);

                    earliestDate.ifPresent(law::setAffectingLawsFirstDate);
                });
            }
        });
    }
}
