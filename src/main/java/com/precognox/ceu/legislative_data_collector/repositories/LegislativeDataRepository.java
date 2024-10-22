package com.precognox.ceu.legislative_data_collector.repositories;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.usa.LawType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.QueryHint;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;

@Repository
public interface LegislativeDataRepository extends JpaRepository<LegislativeDataRecord, Long> {

    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.country = :country ORDER BY r.dateIntroduction")
    List<LegislativeDataRecord> findAllSortedByDateIntro(@Param("country") Country country);

    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.country = :country ORDER BY r.recordId")
    List<LegislativeDataRecord> findAllSortedByRecordId(@Param("country") Country country);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "1"))
    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.country = :country")
    Stream<LegislativeDataRecord> streamAll(@Param("country") Country country);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "1"))
    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.country = :country and r.billText IS NULL")
    Stream<LegislativeDataRecord> streamAllWithMissingBillTexts(@Param("country") Country country);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "1"))
    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.originators IS EMPTY AND r.country = :country")
    Stream<LegislativeDataRecord> streamAllWithoutOriginators(@Param("country") Country country);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "1"))
    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.country = :country AND r.dateProcessed IS NULL")
    Stream<LegislativeDataRecord> streamAllWithoutDateProcessed(@Param("country") Country country);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.country = :country AND r.lawText LIKE '<%'")
    Stream<LegislativeDataRecord> streamAllWithLawTextError(@Param("country") Country country);

    boolean existsByBillPageUrl(String billPageUrl);

    @Query("SELECT COUNT(d) > 0 FROM LegislativeDataRecord d"
            + " WHERE d.billId = :billId"
            + " AND d.country = :country")
    boolean existsByBillIdAndCountry(
            @Param("billId") String billId, @Param("country") Country country);

    @Query("SELECT COUNT(d) > 0 FROM LegislativeDataRecord d"
            + " WHERE d.billId = :billId"
            + " AND YEAR(d.dateIntroduction) = :year")
    boolean existsByBillIdAndYear(@Param("billId") String billId, @Param("year") int year);

    @Query("SELECT COUNT(d) > 0 FROM LegislativeDataRecord d"
            + " WHERE d.rawPageSource.url = :rawSourceUrl")
    boolean existsByRawSourceUrl(@Param("rawSourceUrl") String rawSourceUrl);

    @Query("SELECT COUNT(d) FROM LegislativeDataRecord d"
            + " WHERE d.lawId = :lawId"
            + " AND d.country = :country")
    int countByLawIdAndCountry(@Param("lawId") String lawId, @Param("country") Country country);

    @Query("SELECT d FROM LegislativeDataRecord d WHERE d.lawId = :lawId AND d.country = :country ORDER BY d.dateIntroduction DESC")
    List<LegislativeDataRecord> findAllByLawIdAndCountryOrdered(@Param("lawId") String lawId,
                                                                @Param("country") Country country);

    Optional<LegislativeDataRecord> findByLawId(String lawId);

    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.lawId = :lawId and r.lawType='" + LawType.Public + "'")
    List<LegislativeDataRecord> findPublicByLawId(@Param("lawId") String lawId);

    @Query(value = "SELECT record_id FROM bill_main_table WHERE law_id = :lawId", nativeQuery = true)
    Optional<String> getRecordIdByLawId(@Param("lawId") String lawId);

    Optional<LegislativeDataRecord> findByCountryAndBillId(Country country, String billId);

    List<LegislativeDataRecord> findByCountryAndBillTitle(Country country, String billTitle);

    List<LegislativeDataRecord> findByCountryAndBillTitleStartsWith(Country country, String billTitle);

    @Query("SELECT r FROM LegislativeDataRecord r" +
            " WHERE r.country = :country" +
            " AND lower(r.billTitle) LIKE %:billNamePart%")
    List<LegislativeDataRecord> findByCountryAndBillNamePart(Country country, String billNamePart);

    boolean existsByCountryAndBillIdAndBillTitle(Country country, String billId, String billTitle);

    Optional<LegislativeDataRecord> findByBillId(String billId);

    Optional<LegislativeDataRecord> findByBillPageUrl(String url);

    Optional<LegislativeDataRecord> findByLawTextUrl(String url);

    Optional<LegislativeDataRecord> findByBillTextUrl(String url);

    @Query("SELECT d FROM LegislativeDataRecord d WHERE d.country = :country")
    List<LegislativeDataRecord> findByCountry(@Param("country") Country country);

    @Query("SELECT d FROM LegislativeDataRecord d WHERE d.country = :country AND d.billStatus = 'PASS'")
    List<LegislativeDataRecord> findPassedBillsByCountry(@Param("country") Country country);

    @Query("SELECT d FROM LegislativeDataRecord d WHERE d.country = :country AND d.lawId IS NOT NULL AND d.billStatus = 'PASS'")
    List<LegislativeDataRecord> findLawsByCountry(@Param("country") Country country);

    Page<LegislativeDataRecord> findByCountry(Country country, Pageable paging);

    Page<LegislativeDataRecord> findByCountryAndLawIdIsNotNull(Country country, Pageable pageable);

    Optional<LegislativeDataRecord> findByLawIdAndCountry(String lawId, Country country);

    @Query(value = "SELECT * FROM {h-schema}bill_main_table r INNER JOIN {h-schema}affected_laws a ON r.record_id = a.record_id WHERE a.modified_law_id = :id",
            nativeQuery = true)
    List<LegislativeDataRecord> findByModifiedLawId(String id);

    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.lawId = :lawId AND YEAR(r.datePassing) = :year")
    List<LegislativeDataRecord> findByDatePassingYearAndLawId(@Param("year") int year, @Param("lawId") String lawId);

    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.amendmentCount IS NULL")
    Stream<LegislativeDataRecord> findRecordsWithoutAmendments();

    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.country = :country AND r.amendmentCount > 0")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> findRecordsWithAmendments(@Param("country") Country country);

    @Query("SELECT COUNT(r) FROM LegislativeDataRecord r WHERE r.amendmentCount IS NULL")
    int countRecordsWithoutAmendments();

    @Query("SELECT COUNT(r) FROM LegislativeDataRecord r WHERE r.plenarySize IS NULL")
    int countRecordsWithoutPlenarySize();

    @Query("SELECT r FROM LegislativeDataRecord r LEFT JOIN FETCH r.stages WHERE r.plenarySize IS NULL")
    Stream<LegislativeDataRecord> findRecordsWithoutPlenarySize();

    @Query("SELECT r.lawId FROM LegislativeDataRecord r WHERE r.country = :country")
    List<String> findLawIds(@Param("country") Country country);

    @Query(value = "select count(a.record_id) > 0 from bill_main_table b inner join amendments a on b.record_id = a.record_id where a.record_id = :recordId and a.amendment_stage_name = :stageName",
            nativeQuery = true)
    boolean existAmendmentForRecord(@Param("recordId") String recordId, @Param("stageName") String stageName);

    Optional<LegislativeDataRecord> findByRawPageSourceUrl(String url);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "1"))
    @Query("SELECT s FROM LegislativeDataRecord s WHERE s.country = :country AND s.rawPageSource.rawSource IS NOT NULL")
    Stream<LegislativeDataRecord> streamAllWithRawData(Country country);

    @Query("SELECT s FROM LegislativeDataRecord s WHERE s.country = :country AND s.rawPageSource.rawSource IS NOT NULL")
    Page<LegislativeDataRecord> findAllWithRawData(Pageable pageable, Country country);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "1"))
    @Query("SELECT s FROM LegislativeDataRecord s WHERE s.country = :country AND s.rawPageSource.rawSource IS NULL")
    Stream<LegislativeDataRecord> streamAllWithoutRawData(Country country);

    @Query("SELECT s FROM LegislativeDataRecord s WHERE s.country = :country AND s.rawPageSource.rawSource IS NULL")
    List<LegislativeDataRecord> findAllWithoutRawData(Country country);

    @Query("SELECT d FROM LegislativeDataRecord d"
            + " WHERE d.recordId = :recordId")
    Optional<LegislativeDataRecord> findByRecordId(@Param("recordId") String recordId);

    @Query("SELECT d FROM LegislativeDataRecord d"
            + " WHERE d.billPageUrl = :billUrl")
    LegislativeDataRecord getRecordByBillPageUrl(@Param("billUrl") String billUrl);

    @Query("SELECT d.billText FROM LegislativeDataRecord d"
            + " WHERE d.billPageUrl = :billUrl")
    Optional<String> getBillTextByBillPageUrl(@Param("billUrl") String billUrl);

    @Query("SELECT r FROM LegislativeDataRecord r " +
            "WHERE r.country = :country AND r.originType IS NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamUnprocessedOriginators(@Param("country") Country country);

    @Query("SELECT r FROM LegislativeDataRecord r " +
            "WHERE r.country = :country AND r.billStatus IS NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamUnprocessedLaws(@Param("country") Country country);

    @Query("SELECT r FROM LegislativeDataRecord r " +
            "WHERE r.country = :country AND r.committeeCount IS NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamUnprocessedCommittees(@Param("country") Country country);

    @Query("SELECT r FROM LegislativeDataRecord r " +
            "WHERE r.country = :country AND r.impactAssessmentDone IS NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamUnprocessedImpactAssessments(@Param("country") Country country);

    @Query("SELECT r FROM LegislativeDataRecord r " +
            "WHERE r.country = :country AND r.amendmentCount IS NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamUnprocessedAmendments(@Param("country") Country country);

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM legislative_data_south_africa.amendments a"
            + " WHERE a.record_id = :recordId", nativeQuery = true)
    void deleteConnectingAmendments(@Param("recordId") Long recordId);

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM legislative_data_south_africa.impact_assessments a"
            + " WHERE a.record_id = :recordId", nativeQuery = true)
    void deleteConnectingImpactAssessments(@Param("recordId") Long recordId);

    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.lawId = :lawId and r.lawType='public'")
    Optional<LegislativeDataRecord> findByPublicLawId(String lawId);

    @Transactional
    @Modifying
    @Query("UPDATE LegislativeDataRecord d"
            + " SET d.affectingLawsCount = :affectingLawsCount"
            + " WHERE d.lawId = :lawId")
    void updateAffectingLawsCountColumn(
            @Param("lawId") String lawId, @Param("affectingLawsCount") Integer affectingLawsCount);

    @Transactional
    @Modifying
    @Query("UPDATE LegislativeDataRecord d"
            + " SET d.affectingLawsFirstDate = :affectingLawsFirstDate"
            + " WHERE d.lawId = :lawId")
    void updateAffectingLawsFirstDateColumn(
            @Param("lawId") String lawId, @Param("affectingLawsFirstDate") LocalDate affectingLawsFirstDate);

    boolean existsByBillIdAndDatePassing(String billId, LocalDate datePassing);

    boolean existsByBillTextGeneralJustification(String billTextGeneralJustification);

    @Query("SELECT r FROM LegislativeDataRecord r" +
            " WHERE r.country = :country" +
            " AND r.billText IS NULL" +
            " AND r.billTextUrl IS NOT NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamAllWithUnprocessedBillTextUrl(@Param("country") Country country);

    @Query("SELECT COUNT(r) FROM LegislativeDataRecord r" +
            " WHERE r.country = :country" +
            " AND r.billText IS NULL" +
            " AND r.billTextUrl IS NOT NULL")
    int countAllWithUnprocessedBillTextUrl(@Param("country") Country country);

    @Query("SELECT r FROM LegislativeDataRecord r" +
            " WHERE r.country = :country" +
            " AND r.lawText IS NULL" +
            " AND r.lawTextUrl IS NOT NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamAllWithUnprocessedLawTextUrl(@Param("country") Country country);

    @Query("SELECT r FROM LegislativeDataRecord r" +
            " WHERE r.country = :country" +
            " AND ((r.lawText IS NULL AND r.lawTextUrl IS NOT NULL)" +
            " OR (r.billText IS NULL AND r.billTextUrl IS NOT NULL))")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamAllForBillAndLawTextDownloader(@Param("country") Country country);

    @Query("SELECT COUNT(r) FROM LegislativeDataRecord r" +
            " WHERE r.country = :country" +
            " AND r.lawText IS NULL" +
            " AND r.lawTextUrl IS NOT NULL")
    int countAllWithUnprocessedLawTextUrl(@Param("country") Country country);

    @Query("SELECT r FROM LegislativeDataRecord r " +
            "WHERE r.swedenCountrySpecificVariables.forslagspunkterPageUrl IS NOT NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamAllWithSuggestionsPageUrl();

    @Query("SELECT r FROM LegislativeDataRecord r" +
            " WHERE r.swedenCountrySpecificVariables.affectingLawsPageUrl IS NOT NULL" +
            " AND r.affectingLawsCount IS NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamAllWithUnprocessedAffectingLawsPage();

    @Query("SELECT r FROM LegislativeDataRecord r " +
            "WHERE r.lawTextUrl IS NOT NULL AND r.lawText IS NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamAllWithUnprocessedLawTextUrl();

    @Query("SELECT r FROM LegislativeDataRecord r WHERE r.billText IS NOT NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamAllWithBillText();

    @Query("SELECT r FROM LegislativeDataRecord r" +
            " WHERE r.billText IS NOT NULL" +
            " AND r.dateIntroduction IS NULL" +
            " AND r.dateEnteringIntoForce IS NULL" +
            " AND r.originalLaw IS NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamAllForBillTextParser();

    @Query("SELECT r FROM LegislativeDataRecord r" +
            " WHERE r.country = :country" +
            " AND r.billTextUrl IS NOT NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<LegislativeDataRecord> streamAllWithBillTextUrl(@Param("country") Country country);

    @Query("SELECT r FROM LegislativeDataRecord r " +
            "WHERE r.billStatus='PASS'" +
            "and r.lawText is null")
    Page<LegislativeDataRecord> findUnprocessLawsForAu(@Param("country") Country country, Pageable page);

    @Query("SELECT r FROM LegislativeDataRecord r" +
            " WHERE r.brazilCountrySpecificVariables.camaraPageUrl IS NOT NULL")
    Stream<LegislativeDataRecord> streamAllWithCamaraPageUrl();

    @Query("SELECT r FROM LegislativeDataRecord r" +
            " WHERE r.brazilCountrySpecificVariables.senadoPageUrl IS NOT NULL")
    Stream<LegislativeDataRecord> streamAllWithSenadoPageUrl();

    @Query(value = "SELECT nextval('{h-schema}uk_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForUk();

    @Query(value = "SELECT nextval('{h-schema}hu_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForHungary();

    @Query(value = "SELECT nextval('{h-schema}co_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForColombia();

    @Query(value = "SELECT nextval('{h-schema}br_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForBrazil();

    @Query(value = "SELECT nextval('{h-schema}jo_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForJordan();

    @Query(value = "SELECT nextval('{h-schema}in_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForIndia();

    @Query(value = "SELECT nextval('{h-schema}bg_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForBulgaria();

    @Query(value = "SELECT nextval('{h-schema}ch_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForChile();

    @Query(value = "SELECT nextval('{h-schema}usa_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForUsa();

    @Query(value = "SELECT nextval('{h-schema}ru_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForRussia();

    @Query(value = "SELECT nextval('{h-schema}ge_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForGeorgia();

    @Query(value = "SELECT nextval('{h-schema}au_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForAustralia();

    @Query(value = "SELECT nextval('{h-schema}sw_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForSweden();

    @Query(value = "SELECT nextval('{h-schema}pl_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForPoland();

    @Query(value = "SELECT nextval('{h-schema}sa_generic_id_seq')", nativeQuery = true)
    int getNextUniqueIdForSouthAfrica();
}
