package com.precognox.ceu.legislative_data_collector.hungary;

import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.sql.Date;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

/**
 * The purpose of this class is to speed up dataset updates, by copying older records from and old database schema to the current schema. The steps for using this class are the following:
 *  - Collect and process the newest records normally (see the steps in {@link HungaryController}, with setting a limit on the number of years we go back during the scraping - we only want to collect the newest bills (which were not present in the last dataset), and existing bills where updates or status changes are suspected (bills introduced 2-3 years ago, or at any point in the current legislative period).
 *  - Use this class to copy the remaining records from the old schema to the new schema (where no suspected updates are present - like bills from previous legislative periods).
 *  - Manually run the HU_merge_old_records.sql script from the sql_utils folder (this will copy the amendments for the migrated records - this was problematic with JPA so an SQL script is used). After this, the dataset will be complete with all new and old bills.
 */
@Slf4j
@Service
public class HungaryDatasetMerger {

    private final EntityManager entityManager;

    private final PrimaryKeyGeneratingRepository recordRepository;

    private final TransactionTemplate transactionTemplate;

    private static final String OLD_SCHEMA = "hungary";
    private static final String NEW_SCHEMA = "hungary_data_update";

    private static final String QUERY = """
        select old_bmt.id
        from {0}.bill_main_table old_bmt
             left join {1}.bill_main_table new_bmt
                   on old_bmt.bill_id = new_bmt.bill_id
        where new_bmt.bill_id is null
        order by old_bmt.id;
        """;

    @Autowired
    public HungaryDatasetMerger(
            EntityManager entityManager,
            PrimaryKeyGeneratingRepository recordRepository,
            TransactionTemplate transactionTemplate) {
        this.entityManager = entityManager;
        this.recordRepository = recordRepository;
        this.transactionTemplate = transactionTemplate;
    }

    //this method starts the process
    public void mergeRecords() {
        String resolvedQuery = MessageFormat.format(QUERY, OLD_SCHEMA, NEW_SCHEMA);
        List<Integer> resultList = entityManager.createNativeQuery(resolvedQuery).getResultList();
        log.info("Found {} records to merge", resultList.size());

        for (Integer id : resultList) {
            try {
                transactionTemplate.execute(status -> {
                    LegislativeDataRecord newRecord = mergeRecord(id);
                    log.info("Saved record: {}", newRecord.getRecordId());

                    return status;
                });
            } catch (Exception e) {
                log.error("Error while merging record with ID: {}", id, e);
            }
        }
    }

    /**
     * Copies a record from the old schema to the new schema. Also copies the embedded entities. Amendments and impact assessments are NOT copied (SQL scripts are used for this).
     *
     * @param oldRecordId The ID of the record in the old schema
     *
     * @return The new record after persisting it.
     */
    @NotNull
    private LegislativeDataRecord mergeRecord(Integer oldRecordId) {
        String bmtQuery = "select * from hungary.bill_main_table where id = :id";

        Query query = entityManager.createNativeQuery(bmtQuery, LegislativeDataRecord.class);
        LegislativeDataRecord oldRecord =
                (LegislativeDataRecord) query.setParameter("id", oldRecordId.longValue()).getSingleResult();

        LegislativeDataRecord newRecord = cloneRecord(oldRecord);
        newRecord.setStages(findStagesForRecord(oldRecord.getId()));
        newRecord.setOriginators(findOriginatorsForRecord(oldRecord.getId()));
        newRecord.setCommittees(findCommitteesForRecord(oldRecord.getId()));

        recordRepository.save(newRecord);

        return newRecord;
    }

    @NotNull
    private List<LegislativeStage> findStagesForRecord(Long recId) {
        String query = "select * from hungary.legislative_stages where record_id = :record_id";
        List<Object[]> results = entityManager.createNativeQuery(query)
                .setParameter("record_id", recId)
                .getResultList();

        return results.stream().map(this::parseStageFromArray).toList();
    }

    @NotNull
    private List<Originator> findOriginatorsForRecord(Long recId) {
        String query = "select * from hungary.originators where record_id = :record_id";
        List<Object[]> results = entityManager.createNativeQuery(query)
                .setParameter("record_id", recId)
                .getResultList();

        return results.stream().map(this::parseOriginatorFromArray).toList();
    }

    @NotNull
    private List<Committee> findCommitteesForRecord(Long recId) {
        String query = "select * from hungary.committees where record_id = :record_id";
        List<Object[]> results = entityManager.createNativeQuery(query)
                .setParameter("record_id", recId)
                .getResultList();

        return results.stream().map(this::parseCommitteeFromArray).toList();
    }

    /**
     * Clones record without ID and record id - copies basic fields only (not other entities/lists).
     *
     * @param original
     * @return
     */
    private LegislativeDataRecord cloneRecord(LegislativeDataRecord original) {
        LegislativeDataRecord newRec = new LegislativeDataRecord();
        newRec.setCountry(original.getCountry());
        newRec.setBillType(original.getBillType());
        newRec.setLawType(original.getLawType());
        newRec.setTypeOfLawEng(original.getTypeOfLawEng());
        newRec.setBillId(original.getBillId());
        newRec.setLawId(original.getLawId());
        newRec.setBillTitle(original.getBillTitle());
        newRec.setBillPageUrl(original.getBillPageUrl());
        newRec.setOriginalLaw(original.getOriginalLaw());
        newRec.setOriginType(original.getOriginType());
        newRec.setStagesCount(original.getStagesCount());
        newRec.setBillStatus(original.getBillStatus());
        newRec.setBillSize(original.getBillSize());
        newRec.setBillText(original.getBillText());
        newRec.setBillTextUrl(original.getBillTextUrl());
        newRec.setLawText(original.getLawText());
        newRec.setLawTextUrl(original.getLawTextUrl());
        newRec.setLawSize(original.getLawSize());
        newRec.setDateIntroduction(original.getDateIntroduction());
        newRec.setCommitteeDate(original.getCommitteeDate());
        newRec.setDatePassing(original.getDatePassing());
        newRec.setDateEnteringIntoForce(original.getDateEnteringIntoForce());
        newRec.setCommitteeCount(original.getCommitteeCount());
        newRec.setCommitteeHearingCount(original.getCommitteeHearingCount());
        newRec.setCommitteeDepth(original.getCommitteeDepth());
        newRec.setProcedureTypeStandard(original.getProcedureTypeStandard());
        newRec.setProcedureTypeEng(original.getProcedureTypeEng());
        newRec.setProcedureTypeNational(original.getProcedureTypeNational());
        newRec.setImpactAssessmentDone(original.getImpactAssessmentDone());
        newRec.setAmendmentCount(original.getAmendmentCount());
        newRec.setModifiedLaws(new HashSet<>(original.getModifiedLaws()));
        newRec.setModifiedLawsCount(original.getModifiedLawsCount());
        newRec.setAffectingLawsCount(original.getAffectingLawsCount());
        newRec.setAffectingLawsFirstDate(original.getAffectingLawsFirstDate());
        newRec.setPlenarySize(original.getPlenarySize());
        newRec.setFinalVoteFor(original.getFinalVoteFor());
        newRec.setFinalVoteAgainst(original.getFinalVoteAgainst());
        newRec.setFinalVoteAbst(original.getFinalVoteAbst());
        newRec.setDateProcessed(original.getDateProcessed());
        newRec.setBillTextGeneralJustification(original.getBillTextGeneralJustification());

        return newRec;
    }

    private LegislativeStage parseStageFromArray(Object[] stage) {
        LegislativeStage newStage = new LegislativeStage();
        newStage.setStageNumber((Integer) stage[1]);
        newStage.setDate(convertToLocalDate((Date) stage[2]));
        newStage.setName((String) stage[3]);
        newStage.setDebateSize((Integer) stage[4]);

        return newStage;
    }

    private LocalDate convertToLocalDate(Date date) {
        return date != null ? date.toLocalDate() : null;
    }

    private Originator parseOriginatorFromArray(Object[] orig) {
        Originator newOrig = new Originator();
        newOrig.setName((String) orig[1]);
        newOrig.setAffiliation((String) orig[2]);

        return newOrig;
    }

    private Committee parseCommitteeFromArray(Object[] comm) {
        Committee newComm = new Committee();
        newComm.setName((String) comm[1]);
        newComm.setRole((String) comm[2]);

        return newComm;
    }

}
