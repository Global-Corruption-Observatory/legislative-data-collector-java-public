package com.precognox.ceu.legislative_data_collector.repositories;

import com.precognox.ceu.legislative_data_collector.entities.BillUrl;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Stream;

@Repository
public interface BillUrlRepository extends JpaRepository<BillUrl, Integer> {

    boolean existsByUrl(String url);

    boolean existsByCleanUrl(String cleanUrl);

    @Query("SELECT url FROM BillUrl url " +
            "LEFT JOIN PageSource ps " +
            "ON ps.cleanUrl = url.cleanUrl " +
            "WHERE url.country = :country " +
            "AND ps IS NULL")
    List<BillUrl> findUnprocessedUrls(@Param("country") Country country);

    @Query("SELECT url FROM BillUrl url " +
            "LEFT JOIN PageSource ps " +
            "ON ps.cleanUrl = url.cleanUrl " +
            "WHERE url.country = :country " +
            "AND ps IS NULL")
    Stream<BillUrl> streamUnprocessedUrls(@Param("country") Country country);

}
