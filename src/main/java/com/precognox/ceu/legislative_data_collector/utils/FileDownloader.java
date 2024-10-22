package com.precognox.ceu.legislative_data_collector.utils;

import com.precognox.ceu.legislative_data_collector.entities.DownloadedFile;
import com.precognox.ceu.legislative_data_collector.repositories.DownloadedFileRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Optional;

@Slf4j
@Service
public class FileDownloader {

    @Autowired
    private DownloadedFileRepository downloadedFileRepository;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<DownloadedFile> getFromDbOrDownload(String fileUrl) {
        if (downloadedFileRepository.existsByUrl(fileUrl)) {
            return Optional.of(downloadedFileRepository.findByUrl(fileUrl));
        }

        log.info("Downloading file: {}", fileUrl);

        HttpResponse<byte[]> fileResp = Unirest.get(fileUrl).asBytes();

        if (fileResp.isSuccess()) {
            DownloadedFile downloadedFile = new DownloadedFile();
            downloadedFile.setContent(fileResp.getBody());
            downloadedFile.setSize(fileResp.getBody().length);
            downloadedFile.setUrl(fileUrl);
            downloadedFile.setFilename(getFileNameFromUrl(fileUrl));
            downloadedFile.setContentType(fileResp.getHeaders().getFirst("Content-Type"));

            return Optional.of(downloadedFileRepository.save(downloadedFile));
        }

        log.error("Error response when downloading file: {}, {}, URL: {}",
                fileResp.getStatus(),
                fileResp.getStatusText(),
                fileUrl);

        return Optional.empty();
    }

    private String getFileNameFromUrl(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }

}
