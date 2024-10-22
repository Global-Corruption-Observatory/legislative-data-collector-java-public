package com.precognox.ceu.legislative_data_collector.utils;

import com.precognox.ceu.legislative_data_collector.common.Constants;
import com.precognox.ceu.legislative_data_collector.common.exception.CaptchaException;
import com.precognox.ceu.legislative_data_collector.entities.DownloadedFile;
import com.precognox.ceu.legislative_data_collector.exceptions.PageResponseException;
import com.precognox.ceu.legislative_data_collector.repositories.DownloadedFileRepository;
import com.precognox.ceu.legislative_data_collector.utils.selenium.SeleniumUtils;
import kong.unirest.HeaderNames;
import kong.unirest.HttpResponse;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.precognox.ceu.legislative_data_collector.common.Constants.PROXY_LIST;

/**
 * Downloads and extracts the text from bill and law text documents. Handles PDF and DOCX files currently.
 */
@Slf4j
@Service
public class DocumentDownloader {

    @Autowired
    private DownloadedFileRepository downloadedFileRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final int DOWNLOAD_TIMEOUT_MINUTES = 3;

    @Retryable(
            value = {CaptchaException.class},
            maxAttempts = 100,
            backoff = @Backoff(random = true, delay = 5 * 1000, maxDelay = 60 * 60 * 1000, multiplier = 2.0)
    )
    public Optional<String> processWithBrowser(String documentUrl) {
        Optional<DownloadedFile> optFile = fetchFromDbOrDownload(documentUrl);

        if (optFile.isPresent()) {
            try {
                DownloadedFile file = optFile.get();

                if (file.getFilename().endsWith(".pdf")) {
                    return Optional.ofNullable(PdfUtils.extractText(file.getContent()));
                } else if (file.getFilename().endsWith(".docx")) {
                    return Optional.ofNullable(UnirestUtils.tikaReadText(file.getContent(), StandardCharsets.UTF_8));
                }
            } catch (IOException | TikaException | SAXException e) {
                log.error("Error while parsing document: " + documentUrl, e);
            }
        }

        return Optional.empty();
    }

    private Optional<DownloadedFile> fetchFromDbOrDownload(String pdfUrl) {
        if (downloadedFileRepository.existsByUrl(pdfUrl)) {
            return Optional.of(downloadedFileRepository.findByUrl(pdfUrl));
        }

        try {
            log.info("Downloading PDF: {}", pdfUrl);

            Path tempDirPath = Path.of(System.getProperty("user.home"), UUID.randomUUID().toString());
            Files.createDirectory(tempDirPath);

            Map<String, Object> prefs = Map.of(
                    "download.default_directory", tempDirPath.toString(),
                    "download.prompt_for_download", "false",
                    "plugins.always_open_pdf_externally", true,
                    "plugins.plugins_disabled", new String[]{"Adobe Flash Player", "Chrome PDF Viewer", "Foxit Reader"}
            );

            ChromeOptions options = new ChromeOptions();

            if (!PROXY_LIST.isEmpty()) {
                String randomProxy = PROXY_LIST.get(Double.valueOf(Math.random() * PROXY_LIST.size()).intValue());
                options.addArguments("--proxy-server=" + randomProxy);
            }

            options.setExperimentalOption("prefs", prefs);
            options.setBinary(Constants.CHROME_LOCATION);
            ChromeDriver br = new ChromeDriver(options);

            try {
                br.get(pdfUrl);

                if (is404Page(br)) {
                    return Optional.empty();
                }

                SeleniumUtils.checkCaptcha(br);

                //additional click is needed on the Brazil website
                if (pdfUrl.contains("pesquisa.in.gov.br")) {
                    try {
                        br.switchTo().frame(1);
                        br.findElement(By.cssSelector("a")).click();
                    } catch (NoSuchFrameException e) {
                        log.error("Expected frame not found on page: {}", pdfUrl);
                    } catch (NoSuchElementException e) {
                        log.error("Download link not found on page: {}", pdfUrl);
                    }
                }

                File pdfFile = null;

                try {
                    pdfFile = waitForDownload(tempDirPath);

                    FileInputStream pdfStream = new FileInputStream(pdfFile);
                    DownloadedFile storedFile = new DownloadedFile();
                    storedFile.setUrl(pdfUrl);
                    storedFile.setFilename(pdfFile.getName());
                    storedFile.setContent(pdfStream.readAllBytes());

                    transactionTemplate.executeWithoutResult(status -> downloadedFileRepository.save(storedFile));

                    return Optional.of(storedFile);
                } finally {
                    if (pdfFile != null) {
                        Files.delete(pdfFile.toPath());
                    }
                }
            } finally {
                br.close();
                Files.delete(tempDirPath);
            }
        } catch (TimeoutException | IOException e) {
            log.error("Failed to process PDF from URL: " + pdfUrl, e);
        }

        return Optional.empty();
    }

    private static boolean is404Page(ChromeDriver br) {
        return !br.findElements(By.cssSelector("div#content")).isEmpty()
                || !br.findElements(By.id("main-content")).isEmpty();
    }

    private static File waitForDownload(Path downloadDir) {
        log.info("Waiting for download in directory: {}", downloadDir.toString());

        Wait<Path> wait = new FluentWait<>(downloadDir)
                .withTimeout(Duration.ofMinutes(DOWNLOAD_TIMEOUT_MINUTES))
                .pollingEvery(Duration.ofSeconds(5));

        return wait.until(dirPath -> {
            File[] list = dirPath.toFile().listFiles();

            return list != null && list.length > 0 && !list[0].getName().endsWith(".crdownload") ? list[0] : null;
        });
    }

    public Optional<String> getPdfTextContent(Optional<DownloadedFile> file) {
        return file.map(DownloadedFile::getContent).map(pdfBytes -> {
            try {
                return PdfUtils.extractText(pdfBytes);
            } catch (IOException e) {
                log.error("", e);
                return null;
            }
        });
    }

    private Optional<DownloadedFile> downloadWithUnirest(String pdfUrl, String filename) {
        try {
            log.info("Downloading PDF: {}", pdfUrl);

            HttpResponse<byte[]> response = UnirestUtils.retryGetByteResponseFrom(pdfUrl, 30, 3);
            String contentType = response.getHeaders().getFirst(HeaderNames.CONTENT_TYPE);
            if (contentType.equals("application/pdf")) {
                log.info(pdfUrl, contentType);
                DownloadedFile storedFile = new DownloadedFile();
                storedFile.setUrl(pdfUrl);
                storedFile.setFilename(filename);
                storedFile.setContent(response.getBody());

                downloadedFileRepository.save(storedFile);

                return Optional.of(storedFile);
            } else {
                log.error("Not PDF, cannot process it: " + pdfUrl);
            }
        } catch (PageResponseException | UnirestException e) {
            log.error("Failed to process PDF from URL: " + pdfUrl, e);
        }
        return Optional.empty();
    }

    /**
     * Needed for Colombian parallel downloading
     */
    public Optional<DownloadedFile> readFileToDbEntityThenDelete(Path downloadDirPath, String url, String saveName) throws IOException {
        Wait<Path> wait = new FluentWait<>(downloadDirPath)
                .withTimeout(Duration.ofMinutes(10))
                .pollingEvery(Duration.ofSeconds(5));

        File file;
        try {
            file = wait.until(dirPath -> {
                File[] list = dirPath.toFile().listFiles();

                if(list != null && list.length > 0) {
                    File first = list[0];
                    if(first.getName().endsWith(".pdf")) {
                        return  first;
                    } else if( first.getName().equals("downloads.htm")) {
                        try {
                            Files.delete(first.toPath());
                        } catch (IOException e) {
                            log.error("Wrong file in the download directory which couldn't be deleted");
                        }
                    }
                }
                return null;
            });
        } catch (TimeoutException e) {
            return Optional.empty();
        }
        if (file == null) {
            return Optional.empty();
        }
        String name = Optional.ofNullable(saveName).orElse(file.getName());
        try (FileInputStream pdfStream = new FileInputStream(file)) {
            DownloadedFile storedFile = new DownloadedFile();
            storedFile.setUrl(url);
            storedFile.setFilename(name);
            storedFile.setContent(pdfStream.readAllBytes());
            return Optional.of(storedFile);
        } catch (IOException e) {
            log.error("Could not process file: {}", name);
            return Optional.empty();
        } finally {
            if (file != null) {
                Files.delete(file.toPath());
            }
        }
    }
}
