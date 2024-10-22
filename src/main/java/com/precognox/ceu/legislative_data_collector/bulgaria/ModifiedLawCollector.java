package com.precognox.ceu.legislative_data_collector.bulgaria;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import lombok.extern.slf4j.Slf4j;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ModifiedLawCollector {

    @Autowired
    private LegislativeDataRepository billRepository;

    @Autowired
    private EntityManager entityManager;

    private static final String GAZETTE_NUMBER_PATTERN = "ДВ, бр. (\\d{1,3}) от (\\d\\d\\d\\d) г.";

    private static final String MODIFYING_LAWS_QUERY = "SELECT r FROM LegislativeDataRecord r" +
            " WHERE r.country = :country" +
            " AND r.billStatus = :status" +
            " AND r.originalLaw = FALSE" +
            " AND r.lawText IS NOT NULL";

    private static final String FIND_BY_GAZETTE_NUM_QUERY = "SELECT r FROM LegislativeDataRecord r" +
            " WHERE r.country = :country" +
            " AND r.originalLaw = TRUE" +
            " AND r.bgSpecificVariables.gazetteNumber = :gNum" +
            " AND r.billStatus = :status";

    @Transactional
    public void collectModifiedLaws() {
        log.info("Collecting modified laws...");

        List<LegislativeDataRecord> modifyingLaws =
                entityManager.createQuery(MODIFYING_LAWS_QUERY, LegislativeDataRecord.class)
                .setParameter("country", Country.BULGARIA)
                .setParameter("status", LegislativeDataRecord.BillStatus.PASS)
                .getResultList();

        log.info("Found {} laws to check", modifyingLaws.size());

        for (int i = 0; i < modifyingLaws.size(); i++) {
            log.info("Processing law {}/{}", i+1, modifyingLaws.size());
            findModifiedLawReference(modifyingLaws.get(i));
        }

        log.info("Finished collecting modified laws");
    }

    private void findModifiedLawReference(LegislativeDataRecord currentBill) {
        //check law texts and look for patterns of modified law title + gazette number
        //  example: за изменение и допълнение на Закона за енергийната ефективност (ДВ, бр. 35 от 2015 г.)
        //  url: https://www.parliament.bg/bg/desision/ID/66472
        String textToCheck = StringUtils.truncate(currentBill.getLawText(), 1000);

        Optional<MatchResult> affectedLawExpr = Constants.MODIFYING_LAW_PATTERNS
                .stream()
                .map(pattern -> pattern + "(.+?)" + GAZETTE_NUMBER_PATTERN)
                .map(Pattern::compile)
                .map(pattern -> pattern.matcher(textToCheck))
                .flatMap(Matcher::results)
                .findFirst();

        if (affectedLawExpr.isPresent()) {
            MatchResult matchResult = affectedLawExpr.get();
            String modifiedLawTitle = matchResult.group(1).contains("(")
                    ? matchResult.group(1).substring(0, matchResult.group(1).indexOf("(")).trim()
                    : matchResult.group(1);

            String gazetteNum = matchResult.group(2);
            String gazetteYear = matchResult.group(3);

            //search in DB for matching title and gazette number +filter for passed laws
            List<LegislativeDataRecord> billsByGazetteNum =
                    entityManager.createQuery(FIND_BY_GAZETTE_NUM_QUERY, LegislativeDataRecord.class)
                            .setParameter("country", Country.BULGARIA)
                            .setParameter("gNum", gazetteNum + "/" + gazetteYear)
                            .setParameter("status", LegislativeDataRecord.BillStatus.PASS)
                            .getResultList();

            //calculate fuzzy match score...
            if (!billsByGazetteNum.isEmpty()) {
                Optional<LegislativeDataRecord> mostMatching = billsByGazetteNum.stream()
                        .max(Comparator.comparing(ldr -> FuzzySearch.ratio(ldr.getBillTitle(), modifiedLawTitle)));

                mostMatching.ifPresent(modifiedBill -> {
                    int ratio = FuzzySearch.ratio(mostMatching.get().getBillTitle(), modifiedLawTitle);

                    if (ratio > 50) {
                        currentBill.getModifiedLaws().add(modifiedBill.getBillId());
                        currentBill.setModifiedLawsCount(currentBill.getModifiedLaws().size());

                        int currentCount = modifiedBill.getAffectingLawsCount() != null
                                ? modifiedBill.getAffectingLawsCount()
                                : 0;

                        modifiedBill.setAffectingLawsCount(currentCount + 1);

                        if (modifiedBill.getAffectingLawsFirstDate() == null) {
                            modifiedBill.setAffectingLawsFirstDate(currentBill.getDatePassing());
                        } else {
                            LocalDate currentAffLawsFirstDate = modifiedBill.getAffectingLawsFirstDate();

                            if (currentBill.getDatePassing() != null
                                    && currentAffLawsFirstDate.isAfter(currentBill.getDatePassing())) {
                                modifiedBill.setAffectingLawsFirstDate(currentBill.getDatePassing());
                            }
                        }

                        log.info("Found modified law for bill: {}", currentBill.getBillId());
                    }
                });
            }
        }
    }

}
