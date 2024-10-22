package com.precognox.ceu.legislative_data_collector.utils;

import com.precognox.ceu.legislative_data_collector.entities.*;
import com.precognox.ceu.legislative_data_collector.repositories.DownloadedFileRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.repositories.TextSourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class ReadDatabaseService {
    private final PageSourceRepository pageRepository;
    private final PrimaryKeyGeneratingRepository dataRepository;
    private final TextSourceRepository textRepository;
    private final DownloadedFileRepository fileRepository;
    private final DocumentDownloader pdfDownloader;

    @Autowired
    public ReadDatabaseService(
            PageSourceRepository pageRepository,
            PrimaryKeyGeneratingRepository dataRepository,
            TextSourceRepository textRepository,
            DownloadedFileRepository fileRepository,
            DocumentDownloader pdfDownloader) {
        this.pageRepository = pageRepository;
        this.dataRepository = dataRepository;
        this.textRepository = textRepository;
        this.fileRepository = fileRepository;
        this.pdfDownloader = pdfDownloader;
    }

    @Transactional
    public Optional<PageSource> findByPageTypeAndPageUrl(String type, String url) {
        return pageRepository.findByPageTypeAndPageUrl(type, url);
    }

    @Transactional
    public Optional<LegislativeDataRecord> findRecordByLawId(String lawId) {
        return dataRepository.findByLawId(lawId);
    }

    @Transactional
    public List<PageSource> findAllByPageUrl(String url) {
        return pageRepository.findAllByPageUrl(url);
    }

    @Transactional
    public Optional<LegislativeDataRecord> findByLawTextUrl(String url) {
        return dataRepository.findByLawTextUrl(url);
    }

    @Transactional
    public Optional<TextSource> findByTextTypeAndIdentifierAndCountry(String type, String identifier, Country country) {
        return textRepository.findByTextTypeAndIdentifierAndCountry(type, identifier, country);
    }

    @Transactional
    public boolean existAmendmentForRecord(String recordId, String stageName) {
        return dataRepository.existAmendmentForRecord(recordId, stageName);
    }

    @Transactional
    public Optional<LegislativeDataRecord> findByBillPageUrl(String url) {
        return dataRepository.findByBillPageUrl(url);
    }

    public Optional<DownloadedFile> readFileThenDelete(Path downloadPath, String downloadUrl, String saveName) throws IOException {
        return pdfDownloader.readFileToDbEntityThenDelete(downloadPath, downloadUrl, saveName);
    }

    public Optional<String> getPdfTextContent(Optional<DownloadedFile> file) {
        return pdfDownloader.getPdfTextContent(file);
    }

    @Transactional
    public Optional<DownloadedFile> findByFileName(String fileName) {
        return fileRepository.findByFilename(fileName);
    }

}
