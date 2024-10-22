package com.precognox.ceu.legislative_data_collector.utils;

import kong.unirest.HeaderNames;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Optional;

@Slf4j
public class PdfUtils {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    public static final boolean SKIP_PDFS = false;

    public static Optional<String> tryPdfTextExtraction(String pdfUrl) {
        if (pdfUrl == null || SKIP_PDFS) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(downloadPdfText(pdfUrl));
        } catch (IOException | UnirestException e) {
            log.error("Failed to extract PDF text from URL " + pdfUrl);
            log.error(e.toString());
        }

        return Optional.empty();
    }

    public static String getTextFromPDFFile(File f) throws IOException {
        try (InputStream pdfBytes = new ByteArrayInputStream(FileUtils.readFileToByteArray(f))) {
            PDDocument doc = Loader.loadPDF(pdfBytes.readAllBytes());
            PDFTextStripper textStripper = new PDFTextStripper();
            StringWriter outputStream = new StringWriter();
            textStripper.writeText(doc, outputStream);
            String text = outputStream.toString();
            doc.close();

            return cleanText(text);
        } catch (IOException e) {
            log.error("Error response when extracting text from PDF: {}", f.getAbsolutePath());

            throw new IOException("Failed to download PDF");
        }
    }

    public static String downloadPdfText(String pdfUrl) throws IOException {
        Unirest.config().verifySsl(false);
        HttpResponse<byte[]> pdfResp = Unirest.get(pdfUrl).asBytes();
        String contentType = pdfResp.getHeaders().getFirst(HeaderNames.CONTENT_TYPE);

        if (pdfResp.isSuccess() && PDF_CONTENT_TYPE.equals(contentType)) {
            return extractText(pdfResp.getBody());
        } else {
            log.error("Error response when downloading PDF: {}, {}, URL: {}",
                    pdfResp.getStatus(),
                    pdfResp.getStatusText(),
                    pdfUrl);

            throw new IOException("Failed to download PDF");
        }
    }

    public static String extractText(byte[] pdfBytes) throws IOException {
        try (InputStream stream = new ByteArrayInputStream(pdfBytes)) {
            PDDocument doc = Loader.loadPDF(stream.readAllBytes());
            String text = new PDFTextStripper().getText(doc);
            doc.close();

            return cleanText(text);
        }
    }

    private static String cleanText(String text) {
        //remove null character
        text = text.replaceAll("\\x00", "");
        String trimmed = StringUtils.trimWhitespace(text);

        return !trimmed.isEmpty() ? trimmed : null;
    }

   public static String getDownloadedPDFFileContent(String downloadDir ) {
        Wait<String> waitForDownload = new FluentWait<>(downloadDir)
                .withTimeout(Duration.ofSeconds(580))
                .pollingEvery(Duration.ofSeconds(2));

        try {

            File downloadFile = waitForDownload.until( downloadPath -> {
                assert downloadPath.equals(downloadDir);
                File folder = new File(downloadPath);

                if (!folder.exists()) {
                    // folder does not exist yet -> wait
                    log.info("Folder does not exist yet. Waiting.");
                    return null;
                }

                for (File file : folder.listFiles()) {
                    if (!file.getName().contains(".crdownload")) {
                        if (file.getName().toLowerCase().contains(".pdf")) {
                            log.info("File downloaded: " + file.getName());
                            return file;
                        } else {
                            //Not PDF file
                            return null;
                        }
                    }
                }

                // folder does not contain the PDF file yet -> wait
                log.trace("Folder does not contain the PDF file yet. Waiting.");
                return null;
            });
            String text = getTextFromPDFFile(downloadFile);
            downloadFile.delete();
            return text;
        } catch (TimeoutException timeoutException) {

            File folder = new File(downloadDir);
            if(!folder.exists()){
                log.info("Folder not created");
                throw timeoutException;
            }
            log.info("File not downloaded! Current downloadDir: {}",downloadDir);
            for (File file : folder.listFiles()) {
                log.info("File {} in downloadDir deleted",file.getName());
                file.delete();
            }
            throw timeoutException;
        } catch (IOException e) {
           log.error("Unable to read file in download directory: {}",downloadDir);
           return "Error while reading";
        }
   }

}
