package com.precognox.ceu.legislative_data_collector.utils;

import com.precognox.ceu.legislative_data_collector.exceptions.PageResponseException;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.HWPFOldDocument;
import org.apache.poi.hwpf.OldWordFileFormatException;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.retry.support.RetryTemplate;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

@Slf4j
public class UnirestUtils {
    private final static int MAXIMUM_FILE_SIZE_MB = 10;
    private final static int MB_MULTIPLIER = 1024 * 1024;
    private static PdfParser pdfParser = new PdfParser();

    public static String tikaReadText(String url, Charset encoding) throws IOException, PageResponseException {
        HttpResponse<byte[]> response = retryGetByteResponseFrom(url.trim());
        return pdfParser.extractText(response.getBody());
    }

    public static String tikaReadText(byte[] input, Charset encoding) throws IOException, TikaException, SAXException {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(MAXIMUM_FILE_SIZE_MB * MB_MULTIPLIER);
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_ENCODING, encoding.toString());
        InputStream tikaInput = TikaInputStream.get(input);
        parser.parse(tikaInput, handler, metadata);
        return handler.toString();
    }

    public static HttpResponse<byte[]> retryGetByteResponseFrom(String url) throws PageResponseException, UnirestException {
        return retryGetByteResponseFrom(url, 15, 3);
    }

    public static HttpResponse<byte[]> retryGetByteResponseFrom(String url, int waitSeconds, int limit) throws PageResponseException {
        return getRetryTemplate(waitSeconds, limit).execute(context -> {
            if (context.getRetryCount() > 0) {
                log.info("Retrying: {}", url);
            }
            try {
                HttpResponse<byte[]> response = Unirest.get(url).asBytes();
                int statusCode = response.getStatus();
                if (statusCode >= 200 && statusCode < 300) {
                    if (response.getParsingError().isPresent()) {
                        throw new UnirestException(String.format("Parsing error: %s", response.getParsingError().get().getMessage()));
                    }
                    return response;
                }
                log.warn("{} responded with {}. Waiting {}s then retrying", url, statusCode, waitSeconds);
                throw new PageResponseException(String.format("Page not responded as expected [HTTP CODE: %d]", statusCode));
            } catch (UnirestException ex) {
                log.warn("{} not responded as expected. Waiting {}s then retrying", url, waitSeconds);
                log.warn(String.valueOf(ex));
                throw ex;
            }
        });
    }

    private static RetryTemplate getRetryTemplate(int wait, int limit) {
        return RetryUtils.getRetryTemplate(wait*1000, limit, List.of(new PageResponseException("Filler"), new UnirestException("Filler")));
    }

    //--------------------------------Functions using Apache POI to get Text content of Word documents---Using Apache Tika instead

    @Deprecated
    private static String getDocAsString(String url) throws IOException, PageResponseException {
        HttpResponse<byte[]> response = retryGetByteResponseFrom(url.trim());
        return getDocAsString(response.getBody());
    }

    @Deprecated
    private static String getDocAsString(byte[] byteArray) throws IOException, PageResponseException {
        ByteArrayInputStream rawInput = new ByteArrayInputStream(byteArray);
        int hwpfDocRequiredByteArraySize = 512;
        ByteArrayInputStream input = readInputStreamIntoFixChunkSizedByteArrayInputStream(rawInput, hwpfDocRequiredByteArraySize);
        StringBuilder builder = new StringBuilder();

        String text;
        try {
            HWPFDocument doc = new HWPFDocument(input);
            WordExtractor extractor = new WordExtractor(doc);
            for (String paragraph : extractor.getParagraphText()) {
                builder.append(paragraph);
                builder.append("\n");
            }
            text = builder.toString();
        } catch (OldWordFileFormatException ex) {
            input.reset();
            HWPFOldDocument doc = new HWPFOldDocument(new POIFSFileSystem(input));
            text = doc.getText().toString();
        } catch (IllegalArgumentException ex) {
            throw new PageResponseException(String.format("Text from doc file couldn't be parsed. [%s]", ex.getMessage()));
        }

        rawInput.close();
        input.close();
        return text;
    }

    @Deprecated
    private static ByteArrayInputStream readInputStreamIntoFixChunkSizedByteArrayInputStream(InputStream input, int chunkSize) throws IOException {
        ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
        byte[] buffer = new byte[chunkSize];
        int count = input.read(buffer);
        while (count != -1) {
            byteOS.writeBytes(buffer);
            count = input.read(buffer);
        }
        byteOS.close();
        byte[] allBytes = byteOS.toByteArray();
        return new ByteArrayInputStream(allBytes);
    }

    @Deprecated
    private static String getDocxAsString(String url) throws IOException, PageResponseException {
        HttpResponse<byte[]> response = retryGetByteResponseFrom(url.trim());
        return getDocxAsString(response.getBody());
    }

    @Deprecated
    private static String getDocxAsString(byte[] byteArray) throws IOException {
        StringBuilder textBuilder = new StringBuilder();
        ByteArrayInputStream rawInput = new ByteArrayInputStream(byteArray);

        XWPFDocument doc = new XWPFDocument(rawInput);
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        for (XWPFParagraph paragraph : paragraphs) {
            textBuilder.append(paragraph.getText());
            textBuilder.append("\n");
        }
        rawInput.close();
        return textBuilder.toString();
    }
}
