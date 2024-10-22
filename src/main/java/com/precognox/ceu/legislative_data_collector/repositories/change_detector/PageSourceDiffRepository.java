package com.precognox.ceu.legislative_data_collector.repositories.change_detector;

import com.precognox.ceu.legislative_data_collector.entities.change_detector.PageSourceDiff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PageSourceDiffRepository extends JpaRepository<PageSourceDiff, Long> {

}
