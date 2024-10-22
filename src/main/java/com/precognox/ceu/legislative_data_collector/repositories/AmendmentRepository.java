package com.precognox.ceu.legislative_data_collector.repositories;

import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import javax.persistence.QueryHint;
import java.util.stream.Stream;

import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;

@Repository
public interface AmendmentRepository extends JpaRepository<Amendment, Integer> {

    @Query("SELECT a FROM Amendment a WHERE a.textSourceUrl IS NOT NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "1"))
    Stream<Amendment> streamAllWithTextSourceUrl();

    @Query("SELECT COUNT(a) FROM Amendment a WHERE a.textSourceUrl IS NOT NULL")
    int countAllWithTextSourceUrl();

}
