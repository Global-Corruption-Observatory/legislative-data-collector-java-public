package com.precognox.ceu.legislative_data_collector.repositories;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.TextSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TextSourceRepository extends JpaRepository<TextSource, Long> {

    @Query("SELECT ts FROM TextSource ts"
            + " WHERE ts.textType = :type"
            + " AND ts.textIdentifier = :identifier"
            + " AND ts.country = :country")
    Optional<TextSource> findByTextTypeAndIdentifierAndCountry(@Param("type") String textType, @Param("identifier") String textIdentifier, @Param("country")Country country);
}
