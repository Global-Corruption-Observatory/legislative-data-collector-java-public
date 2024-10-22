package com.precognox.ceu.legislative_data_collector.repositories;

import com.precognox.ceu.legislative_data_collector.entities.DownloadedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DownloadedFileRepository extends JpaRepository<DownloadedFile, Integer> {

    boolean existsByUrl(String url);

    DownloadedFile findByUrl(String url);

    Optional<DownloadedFile> findByFilename(String fileName);

}
