package com.precognox.ceu.legislative_data_collector.hungary;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import lombok.extern.slf4j.Slf4j;
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
 * Calculates the affecting_laws_count and affecting_laws_first_date variables from the modified law counts. Run after
 * {@link ModifiedLawParser} is finished.
 */
@Slf4j
@Service
public class AffectingLawsCalculator {

    private EntityManager entityManager;
    private LegislativeDataRepository legislativeDataRepository;

    @Autowired
    public AffectingLawsCalculator(EntityManager entityManager, LegislativeDataRepository legislativeDataRepository) {
        this.entityManager = entityManager;
        this.legislativeDataRepository = legislativeDataRepository;
    }

    @Transactional
    public void fillAffectingLaws() {
        log.info("Calculating affecting laws...");

        String affectingLawsCountQuery = "SELECT COUNT(al.record_id) AS cnt, al.modified_law_id" +
                " FROM {h-schema}affected_laws as al" +
                " JOIN {h-schema}bill_main_table as bmt" +
                " ON al.record_id = bmt.id" +
                " WHERE bmt.record_id LIKE 'HU%'" +
                " GROUP BY al.modified_law_id ORDER BY cnt DESC";

        List<Object[]> resultList = entityManager.createNativeQuery(affectingLawsCountQuery).getResultList();

        resultList.forEach(record -> {
            BigInteger count = (BigInteger) record[0];
            String affectedLawId = (String) record[1];

            //split into year and law number, then search
            if (affectedLawId.contains("/")) {
                String[] parts = affectedLawId.split("/");
                int year = Integer.parseInt(parts[0]);
                String lawId = parts[1];

                List<LegislativeDataRecord> affectedLaws =
                        legislativeDataRepository.findByDatePassingYearAndLawId(year, lawId);

                if (affectedLaws.size() == 1) {
                    LegislativeDataRecord law = affectedLaws.get(0);
                    law.setAffectingLawsCount(count.intValue());
                    log.info("Set affecting laws for record: {}", law.getRecordId());

                    String affectingLawsQuery = "select record_id from affected_laws where modified_law_id = :law_id";

                    String lawIdInQuery = law.getDatePassing().getYear() + "/" + law.getLawId();
                    List<Integer> affectingLaws = entityManager.createNativeQuery(affectingLawsQuery)
                            .setParameter("law_id", lawIdInQuery)
                            .getResultList();

                    Optional<LocalDate> earliestDate = affectingLaws.stream()
                            .map(id -> legislativeDataRepository.findById(id.longValue()))
                            .filter(Optional::isPresent)
                            .map(entity -> entity.get().getDatePassing())
                            .filter(Objects::nonNull)
                            .min(LocalDate::compareTo);

                    earliestDate.ifPresent(law::setAffectingLawsFirstDate);
                }
            }
        });

        log.info("Done calculating affecting laws");
    }

}
