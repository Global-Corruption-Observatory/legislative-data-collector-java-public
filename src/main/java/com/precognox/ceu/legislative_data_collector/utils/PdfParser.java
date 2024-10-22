package com.precognox.ceu.legislative_data_collector.utils;

import com.precognox.ceu.legislative_data_collector.entities.DownloadedFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Slf4j
@Service
public class PdfParser {

    @Autowired
    private FileDownloader fileDownloader;

    public static final boolean SKIP_PDFS = false;
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final PDFTextStripper PDF_TEXT_STRIPPER = new PDFTextStripper();

    public static final String ERROR_LABEL = "<ERROR>";
    public static final String SCANNED_LABEL = "<SCANNED?>";

    public Optional<String> tryPdfTextExtraction(String pdfUrl) {
        if (pdfUrl == null || SKIP_PDFS) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(downloadPdfText(pdfUrl));
        } catch (Exception e) {
            log.error("Failed to extract PDF text from URL " + pdfUrl, e);

            return Optional.of(ERROR_LABEL);
        }
    }

    private String downloadPdfText(String pdfUrl) throws IOException {
        log.info("Processing file: {}", pdfUrl);
        Optional<DownloadedFile> file = fileDownloader.getFromDbOrDownload(pdfUrl);

        if (file.isPresent()) {
            if (PDF_CONTENT_TYPE.equals(file.get().getContentType())) {
                return extractText(file.get().getContent());
            }
        }

        throw new IOException("Failed to download PDF");
    }

    public String extractText(byte[] pdfBytes) throws IOException {
        try (InputStream stream = new ByteArrayInputStream(pdfBytes)) {
            try (PDDocument doc = Loader.loadPDF(stream.readAllBytes())) {
                String text = PDF_TEXT_STRIPPER.getText(doc);
                doc.close();

                if (text.isBlank()) {
                    return SCANNED_LABEL;
                }

                return cleanText(text);
            }
        }
    }

    private String cleanText(String text) {
        //remove null character
        text = text.replaceAll("\\x00", "");
        String trimmed = StringUtils.trimWhitespace(text);

        return !trimmed.isEmpty() ? trimmed : null;
    }

}
