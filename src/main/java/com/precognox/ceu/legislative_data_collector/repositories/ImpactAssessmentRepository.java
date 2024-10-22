package com.precognox.ceu.legislative_data_collector.repositories;

import com.precognox.ceu.legislative_data_collector.entities.ImpactAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import javax.persistence.QueryHint;

import java.util.stream.Stream;

import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;

@Repository
public interface ImpactAssessmentRepository extends JpaRepository<ImpactAssessment, Integer> {

    @Query("SELECT ia FROM ImpactAssessment ia WHERE ia.originalUrl IS NOT NULL")
    @QueryHints(value = @QueryHint(name = HINT_FETCH_SIZE, value = "1"))
    Stream<ImpactAssessment> streamAllWithOriginalUrl();
}
