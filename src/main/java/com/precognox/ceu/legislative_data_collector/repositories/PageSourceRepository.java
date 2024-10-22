package com.precognox.ceu.legislative_data_collector.repositories;

import com.precognox.ceu.legislative_data_collector.common.ChangeDetector;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.QueryHint;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;

@Repository
public interface PageSourceRepository extends JpaRepository<PageSource, Long> {

    @Query("SELECT s FROM PageSource s WHERE s.country = :country")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "1"))
    Stream<PageSource> streamAll(@Param("country") Country country);

    List<ChangeDetector.PageUrlAndSize> findAllByCountry(Country country);

    @Query("SELECT s FROM PageSource s WHERE s.country = :country and s.pageType = :pageType")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "1"))
    Stream<PageSource> streamAllByPageType(@Param("country") Country country, @Param("pageType") String pageType);

    @Query("SELECT COUNT(ps) > 0 FROM PageSource ps"
            + " WHERE ps.pageType = :pageType"
            + " AND ps.pageUrl = :pageUrl")
    boolean existByPageTypeAndPageUrl(
            @Param("pageType") String pageType, @Param("pageUrl") String pageUrl);

    Optional<PageSource> findByPageUrl(String pageUrl);

    Optional<PageSource> findFirstByPageUrl(String pageUrl);

    Optional<PageSource> findByPageUrlAndMetadata(String pageUrl, String metadata);

    boolean existsByPageTypeAndMetadata(String pageType, String metadata);

    List<PageSource> findByCountryAndPageType(Country country, String pageType);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "4"))
    @Query("SELECT s FROM PageSource s WHERE s.country = :country AND s.pageType = :pageType")
    Stream<PageSource> streamByCountryAndPageType(@Param("country") Country country,
                                                  @Param("pageType") String pageType);

    @Query("SELECT ps FROM PageSource ps" +
            " WHERE ps.pageType = :pageType" +
            " AND ps.country = :country")
    Page<PageSource> findPagesByPageTypeAndCountry(
            @Param("pageType") String pageType,
            @Param("country") Country country,
            Pageable pageable);

    @Query("SELECT ps FROM PageSource ps"
            + " WHERE ps.pageType = :pageType"
            + " AND ps.pageUrl = :pageUrl")
    Optional<PageSource> findByPageTypeAndPageUrl(
            @Param("pageType") String pageType, @Param("pageUrl") String pageUrl);

    @Query("SELECT ps FROM PageSource ps"
            + " WHERE ps.pageType = :pageType"
            + " AND ps.pageUrl = :pageUrl")
    List<PageSource> findAllByPageTypeAndPageUrl(
            @Param("pageType") String pageType, @Param("pageUrl") String pageUrl);

    Optional<PageSource> findFirstByCleanUrl(String cleanUrl);

    @Query("SELECT ps FROM PageSource ps"
            + " WHERE ps.pageType = :pageType"
            + " and ps.country =:country")
    List<PageSource> getPageSourcesByPageTypeAndCountry(
            @Param("pageType") String pageType, @Param("country") Country country);

    List<PageSource> findPagesByPageTypeAndCountry(String pageType, Country country);

    @Query("SELECT ps FROM PageSource ps" +
            " WHERE ps.country = :country" +
            " AND ps.pageType = :pageType" +
            " AND ps.pageUrl LIKE %:fragment%")
    Page<PageSource> findByUrlFragment(
            @Param("pageType") String pageType,
            @Param("country") Country country,
            @Param("fragment") String fragment,
            Pageable pageable);

    @Query(value = "SELECT ps FROM PageSource ps" +
            " WHERE ps.pageType = :pageType" +
            " AND ps.metadata = :lawId")
    Optional<PageSource> findPageSourceByLawId(
            @Param("pageType") String pageType,
            @Param("lawId") String lawId);

    @Query("SELECT ps FROM PageSource ps" +
            " LEFT JOIN LegislativeDataRecord r" +
            " ON ps.cleanUrl = r.billPageUrl" +
            " WHERE ps.country = :country" +
            " AND ps.pageType = 'BILL'" +
            " AND r IS NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<PageSource> findUnprocessedBillPages(@Param("country") Country country);

    @Query("SELECT COUNT(ps) > 0 FROM PageSource ps WHERE ps.pageUrl = :pageUrl")
    boolean existsByPageUrl(@Param("pageUrl") String pageUrl);

    @Query("SELECT ps FROM PageSource ps"
            + " WHERE ps.pageUrl = :pageUrl")
    PageSource getByPageUrl(@Param("pageUrl") String pageUrl);

    @Query("SELECT ps FROM PageSource ps"
            + " WHERE ps.id = :id")
    PageSource getById(@Param("id") long id);

    @Query("SELECT COUNT(ps) = 0 FROM PageSource ps WHERE ps.pageUrl = :pageUrl")
    boolean notExistByPageUrl(@Param("pageUrl") String pageUrl);

    @Query("SELECT COUNT(ps) = 0 FROM PageSource ps WHERE ps.cleanUrl = :pageUrl")
    boolean notExistByCleanUrl(@Param("pageUrl") String pageUrl);

    @Query("SELECT COUNT(ps) = 0 FROM PageSource ps WHERE ps.pageUrl = :pageUrl AND ps.pageType = :type")
    boolean notExistByPageUrlAndType(@Param("pageUrl") String pageUrl, @Param("type") String type);

    @Query("SELECT ps FROM PageSource ps" +
            " WHERE ps.country = :country" +
            " AND ps.pageType = :pageType" +
            " AND ps.metadata = :metadata")
    Optional<PageSource> findByUrlByMetadata(
            @Param("pageType") String pageType,
            @Param("country") Country country,
            @Param("metadata") String metadata
    );

    @Query("SELECT ps.id FROM PageSource ps WHERE ps.pageUrl = :pageUrl")
    long geIdByPageUrl(@Param("pageUrl") String pageUrl);

    Slice<PageSource> findByCountry(Country country, Pageable pageable);

    List<PageSource> findByPageTypeAndCountry(String pageType, Country country);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    @Query("SELECT s FROM PageSource s WHERE s.country = :country AND s.pageType = :page_type " +
            "AND s.collectionDate IS NULL")
    Stream<PageSource> findAllByPageTypeAndCountryNotUpdated(@Param("page_type") String pageType,
                                                             @Param("country") Country country);

    Page<PageSource> findByPageTypeAndCountry(String pageType, Country country, Pageable pageable);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "20"))
    @Query("SELECT s FROM PageSource s WHERE s.country = :country AND s.metadata IS NOT NULL and s.pageType LIKE :page_type")
    Page<PageSource> findByCountryWithMetadataAndLikePageType(@Param("country") Country country,
                                                              @Param("page_type") String pageType, Pageable pageable);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "20"))
    @Query("SELECT s FROM PageSource s WHERE s.country = :country AND s.metadata = :metadata")
    Page<PageSource> findByCountryAndMetadata(@Param("country") Country country, @Param("metadata") String metadata,
                                              Pageable pageable);

    @Query(value = "SELECT s FROM PageSource s WHERE s.metadata like %:metadata%")
    Optional<PageSource> findByMetadata(@Param("metadata") String metadata);

    List<PageSource> findAllByPageUrl(String url);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "8"))
    @Query("SELECT s FROM PageSource s WHERE s.country = :country AND s.rawSource IS NULL")
    Stream<PageSource> streamAllWithoutRawData(Country country);

    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "1"))
    @Query("SELECT s FROM PageSource s WHERE s.country = :country AND s.rawSource IS NOT NULL")
    Stream<PageSource> streamAllWithRawData(Country country);

    @Query("SELECT s FROM PageSource s WHERE s.country = :country AND s.pageType = :pageType")
    Page<PageSource> findAllByCountryAndPageType(Pageable pageable, Country country, String pageType);

    @Query("SELECT s FROM PageSource s WHERE s.country = :country AND s.pageType = :pageType")
    List<PageSource> findAllByCountryAndPageType(@Param("country") Country country, @Param("pageType") String pageType);

    @Query("SELECT s FROM PageSource s WHERE s.country = :country AND s.rawSource IS NULL")
    Page<PageSource> findAllWithoutRawData(Pageable pageable, Country country);

    @Query("SELECT s FROM PageSource s WHERE s.country = :country AND s.rawSource IS NOT NULL")
    Page<PageSource> findAllWithRawData(Pageable pageable, Country country);

    @Query("SELECT s FROM PageSource s WHERE s.rawSource LIKE :likeText")
    List<PageSource> findPageSourceByLike(String likeText);

    Optional<PageSource> findByCountryAndPageUrl(Country country, String url);

    @Query("SELECT s.pageUrl FROM PageSource s " +
            "LEFT JOIN LegislativeDataRecord r " +
            "ON s.pageUrl = r.billPageUrl " +
            "WHERE s.country = :country AND s.pageType = 'BILL' AND r IS NULL")
    List<String> findUnprocessedBills(@Param("country") Country country);

    @Query("SELECT s FROM PageSource s " +
            "LEFT JOIN LegislativeDataRecord r " +
            "ON s.pageUrl = r.billPageUrl " +
            "WHERE s.country = :country AND s.pageType = 'BILL' AND r IS NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<PageSource> streamUnprocessedBillPages(@Param("country") Country country);

    @Query("SELECT s FROM PageSource s " +
            "LEFT JOIN LegislativeDataRecord r " +
            "ON s.pageUrl = r.billPageUrl " +
            "WHERE s.pageUrl like %:fragment%" +
            " AND s.country = :country AND s.pageType = 'BILL'")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<PageSource> streamUnprocessedBillPagesByUrlFragment(@Param("country") Country country,
                                                               @Param("fragment") String fragment);

    @Query("SELECT s FROM PageSource s " +
            "LEFT JOIN LegislativeDataRecord r " +
            "ON s.pageUrl = r.billPageUrl " +
            "WHERE s.country = :country AND s.pageType = :pageType AND r IS NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<PageSource> streamUnprocessedPages(@Param("country") Country country, @Param("pageType") String pageType);

    @Query("SELECT ps FROM PageSource ps " +
            "LEFT JOIN LegislativeDataRecord dr " +
            "ON ps.pageUrl = dr.altBillPageUrl " +
            "WHERE ps.country = :country AND ps.pageType = 'BILL_JSON' AND dr IS NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "5"))
    Stream<PageSource> streamUnprocessedBillsOnAltPageUrl(@Param("country") Country country);

    @Query("SELECT s FROM PageSource s " +
            "LEFT JOIN LegislativeDataRecord r " +
            "ON s.pageUrl = r.billPageUrl " +
            "WHERE s.country = :country AND s.pageType = 'BILL' AND r IS NULL")
    Page<PageSource> findUnprocessedBills(Pageable page, @Param("country") Country country);

    @Query("SELECT s FROM PageSource s " +
            "LEFT JOIN LegislativeDataRecord r " +
            "ON s.pageUrl = r.billPageUrl " +
            "WHERE s.country = :country AND s.pageType = 'bill' AND r IS NULL " +
            "ORDER BY s.id ASC")
    Page<PageSource> findUnprocessedBillsColombia(Pageable page, @Param("country") Country country);

    //    The reason for the native query is to implement limit
    @Query(value = "SELECT s.raw_source FROM page_source s " +
            "WHERE s.page_type = :pageType " +
            "ORDER BY similarity(s.metadata, :originatorName) DESC " +
            "LIMIT 1",
            nativeQuery = true)
    Optional<String> findOriginatorUrlWithFuzzyMatching(@Param("pageType") String pageType,
                                                        @Param("originatorName") String originatorName);

    @Modifying
    @Query(value = "UPDATE {h-schema}page_source SET metadata = :metadata WHERE page_url = :pageUrl", nativeQuery = true)
    @Transactional
    void updateMetadata(@Param("pageUrl") String pageUrl, @Param("metadata") String metadata);
}
