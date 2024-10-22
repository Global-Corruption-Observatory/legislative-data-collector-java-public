package com.precognox.ceu.legislative_data_collector.utils;

import com.precognox.ceu.legislative_data_collector.entities.DownloadedFile;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.apache.tika.metadata.HttpHeaders.CONTENT_ENCODING;

@Slf4j
@Service
public class DocUtils {

    private static final int MAXIMUM_FILE_SIZE_MB = 50;
    private static final int MB_MULTIPLIER = 1024 * 1024;

    private final FileDownloader fileDownloader;

    public DocUtils(FileDownloader fileDownloader) {
        this.fileDownloader = fileDownloader;
    }

    public static synchronized Optional<String> getTextFromDoc(String url) {
        HttpResponse<byte[]> response = Unirest.get(url.trim()).asBytes();

        if (response.isSuccess()) {
            try {
                return getTextFromDoc(response.getBody());
            } catch (IOException | SAXException | TikaException e) {
                log.error("Failed to get text from .doc at URL: " + url, e);
            }
        } else {
            log.error("Failed to get text from .doc at URL: {}, response code is {}", url, response.getStatus());
        }
        return Optional.empty();
    }

    public static synchronized Optional<String> getTextFromDoc(byte[] docFile)
            throws TikaException, IOException, SAXException {
        InputStream tikaInput = TikaInputStream.get(docFile);

        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(MAXIMUM_FILE_SIZE_MB * MB_MULTIPLIER);
        Metadata metadata = new Metadata();
        metadata.add(CONTENT_ENCODING, StandardCharsets.UTF_8.toString());
        parser.parse(tikaInput, handler, metadata);

        String text = handler.toString();

        return StringUtils.isNotBlank(text) ? Optional.of(text.trim()) : Optional.empty();
    }

    // Among polish impact assessment documents there were 27 somehow encrypted pieces, the default detector has failed
    // to parse the texts from byte-streams. Detector-override have solved the problem.
    private static Optional<String> getTextFromDoc(byte[] docFile, String errorMsg, String url)
            throws TikaException, IOException, SAXException {
        InputStream tikaInput = TikaInputStream.get(docFile);
        Detector detector = (input, metadata) -> {
            if (url.endsWith(".rtf")) {
                return MediaType.parse("application/rtf");
            } else if (url.endsWith(".docx")) {
                return MediaType.parse("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            } else if (url.endsWith(".doc")) {
                return MediaType.parse("application/msword");
            }
            return null;
        };

        AutoDetectParser parser = new AutoDetectParser(detector);
        BodyContentHandler handler = new BodyContentHandler(MAXIMUM_FILE_SIZE_MB * MB_MULTIPLIER);
        Metadata metadata = new Metadata();
        metadata.add(CONTENT_ENCODING, StandardCharsets.UTF_8.toString());
        parser.setDetector(detector);
        parser.parse(tikaInput, handler, metadata);

        String text = handler.toString();

        return StringUtils.isNotBlank(text) ? Optional.of(text.trim()) : Optional.empty();
    }

    public Optional<String> downloadDocText(String fileUrl) throws IOException, TikaException, SAXException {
        log.info("Processing file: {}", fileUrl);
        Optional<DownloadedFile> file = fileDownloader.getFromDbOrDownload(fileUrl);

        if (file.isPresent()) {
            Optional<String> text =
                    getTextFromDoc(file.get().getContent(), "Failed to get text from downloaded file: " + file, fileUrl);
            return StringUtils.isNotBlank(text.get()) ? Optional.of(text.get().trim()) : Optional.empty();
        } else {
            log.error("Failed to download file: " + file);
        }
        return Optional.empty();
    }
}
