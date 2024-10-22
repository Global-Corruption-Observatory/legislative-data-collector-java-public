package com.precognox.ceu.legislative_data_collector.common;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.sweden.PageType;
import com.precognox.ceu.legislative_data_collector.utils.DocumentDownloader;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import kong.unirest.ContentType;
import kong.unirest.HeaderNames;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.utils.PdfParser.ERROR_LABEL;
import static com.precognox.ceu.legislative_data_collector.utils.PdfParser.SCANNED_LABEL;

/**
 * Collects the bill and law texts for the stored bills. The URLs of the texts are stored in a
 * previous step (in country-specific classes). This class can be used for multiple countries.
 */
@Slf4j
@Service
public class BillAndLawTextCollector {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private LegislativeDataRepository recordRepository;

    @Autowired
    private PdfParser pdfParser;

    @Autowired
    private DocumentDownloader documentDownloader;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PageSourceLoader pageSourceLoader;

    @Setter
    private PdfCollectionMode collectionMode = PdfCollectionMode.HTTP_GET;
    private static final List<String> SWEDEN_HTML_SUFFIXES = List.of("/html", "/html/");

    public enum PdfCollectionMode {
        BROWSER,
        HTTP_GET
    }

    @Transactional
    public void collectTexts(Country country) {
        collectBillTexts(country);
        collectLawTexts(country);
    }

    @Transactional
    public void collectBillTexts(Country country) {
        log.info("Collecting bill texts...");
        log.info("Found {} records to process", recordRepository.countAllWithUnprocessedBillTextUrl(country));

        recordRepository.streamAllWithUnprocessedBillTextUrl(country).forEach(this::downloadBillText);

        log.info("Done collecting all bill texts");
    }

    @Transactional
    public void collectLawTexts(Country country) {
        log.info("Collecting law texts...");
        log.info("Found {} records to process", recordRepository.countAllWithUnprocessedLawTextUrl(country));

        recordRepository.streamAllWithUnprocessedLawTextUrl(country).forEach(this::downloadLawText);
    }

    public void downloadBillText(LegislativeDataRecord bill) {
        Optional<String> billText = Optional.empty();

        if (Country.SWEDEN.equals(bill.getCountry())
                && SWEDEN_HTML_SUFFIXES.stream().anyMatch(bill.getBillTextUrl()::endsWith)) {
            billText = parseFromHtml(bill.getBillTextUrl());
        } else {
            try {
                HttpResponse head = Unirest.head(bill.getBillTextUrl()).asEmpty();
                String cType = head.getHeaders().getFirst(HeaderNames.CONTENT_TYPE);

                if (Country.SWEDEN.equals(bill.getCountry()) && cType.contains(ContentType.TEXT_HTML.getMimeType())) {
                    billText = parseFromHtml(bill.getBillTextUrl());
                } else {
                    billText = collectionMode == PdfCollectionMode.HTTP_GET
                            ? pdfParser.tryPdfTextExtraction(bill.getBillTextUrl())
                            : documentDownloader.processWithBrowser(bill.getBillTextUrl());
                }
            } catch (Exception e) {
                log.error("Error while downloading bill text from {}", bill.getBillTextUrl(), e);
            }
        }

        billText.ifPresent(text -> {
            transactionTemplate.execute(status -> {
                bill.setBillText(text);

                if (!isErrorValue(text)) {
                    bill.setBillSize(TextUtils.getLengthWithoutWhitespace(text));
                }

                entityManager.merge(bill);
                log.info("Set bill text from {} with size: {}", bill.getBillTextUrl(), bill.getBillSize());

                return status;
            });
        });
    }

    /**
     * Used for Sweden only, where the bill text is in HTML format.
     * @param url the URL of the bill text
     * @return the extracted text
     */
    private Optional<String> parseFromHtml(String url) {
        Optional<PageSource> page = pageSourceLoader.loadFromDbOrFetchWithHttpGet(
                Country.SWEDEN,
                PageType.BILL_TEXT.name(), url
        );

        if (page.isPresent()) {
            Document parsed = Jsoup.parse(page.get().getRawSource());

            Element textContainer = parsed.body().selectFirst("main#content");

            if (textContainer == null) {
                textContainer = parsed.body().selectFirst("div.content");
            }

            return Optional.ofNullable(textContainer).map(Element::text);
        }

        return Optional.empty();
    }

    private boolean isErrorValue(String text) {
        return ERROR_LABEL.equals(text) || SCANNED_LABEL.equals(text);
    }

    private void downloadLawText(LegislativeDataRecord bill) {
        Optional<String> lawText = collectionMode == PdfCollectionMode.HTTP_GET
                ? pdfParser.tryPdfTextExtraction(bill.getLawTextUrl())
                : documentDownloader.processWithBrowser(bill.getLawTextUrl());

        lawText.ifPresent(text -> {
            transactionTemplate.execute(status -> {
                bill.setLawText(text);
                bill.setLawSize(TextUtils.getLengthWithoutWhitespace(text));
                entityManager.merge(bill);
                log.info("Set law text for {}", bill.getRecordId());

                return status;
            });
        });
    }

    private RetryTemplate getRetryTemplate(int waitMs, int limit, List<Class<? extends Throwable>> exceptions) {
        return RetryTemplate.builder().retryOn(exceptions).fixedBackoff(waitMs).maxAttempts(limit).build();
    }

}
