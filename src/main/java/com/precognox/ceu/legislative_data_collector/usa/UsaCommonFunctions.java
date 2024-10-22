package com.precognox.ceu.legislative_data_collector.usa;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.CaptchaException;
import com.precognox.ceu.legislative_data_collector.utils.JsoupUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.precognox.ceu.legislative_data_collector.usa.Constants.SITE_BASE_URL;
import static java.text.MessageFormat.format;

/**
 * Service class providing common functions for processing the USA legislation data.
 * This class offers a set of utility methods to interact with and process legislative data
 * from the USA websites. It handles tasks such as extracting periods from URLs, retrieving action URLs
 * from bill pages, removing query parameters from URLs, and managing page sources.
 */
@Slf4j
@Service
public class UsaCommonFunctions {
    private final PageSourceRepository pageSourceRepository;
    private final JsoupUtils jsoupUtils;
    private TransactionTemplate transactionTemplate;
    public WebDriver driver;

    //    The first period to collect for the USA is 103 (1993-1994)
    private Integer MIN_PERIOD_NUM = 103;
    private  Integer MAX_PERIOD_NUM;

    public UsaCommonFunctions(PageSourceRepository pageSourceRepository,
                              JsoupUtils jsoupUtils,
                              TransactionTemplate transactionTemplate) {
        this.pageSourceRepository = pageSourceRepository;
        this.jsoupUtils = jsoupUtils;
        this.transactionTemplate = transactionTemplate;
    }

    public IntStream getPeriodsToCollect(WebDriver driver) {
        this.driver = driver;
        MAX_PERIOD_NUM = findMaximumPeriodNumber(driver);
        return IntStream.rangeClosed(MIN_PERIOD_NUM, MAX_PERIOD_NUM);
    }

    public int findMaximumPeriodNumber(WebDriver driver) {
        String url = format(Constants.TERM_LIST_PAGE_TEMPLATE, MIN_PERIOD_NUM, 1);
        Optional<String> periodString;

        try {
            Document document = jsoupUtils.getPage(driver, url);

            periodString = Optional.of(document.getElementById("innerbox_congress")
                    .getElementsByClass("facetbox-shownrow")
                    .first()
                    .text());

        } catch (CaptchaException e) {
            throw new RuntimeException(e);
        }

        return periodString.map(PageDownloader::extractPeriodNumber)
                .orElseThrow(() -> new RuntimeException("Unable to find period number"));
    }

    public String getCurrentPeriod(String pageUrl) {
        return Constants.PERIOD_REGEX.matcher(pageUrl).results()
                .map(matchResult -> matchResult.group(1)).findFirst().get();
    }

    public Optional<String> getActionsUrl(Document billPage) {
        return Optional.ofNullable(billPage.body().getElementsByClass("tabs_container").first())
                .map(a -> a.getElementsByTag("a").get(2))
                .map(link -> link.attr("href"))
                .map(url -> SITE_BASE_URL + url);
    }

    public String removeQueryParametersFromUrl(String url) {
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getProtocol() + "://" + parsedUrl.getHost() + parsedUrl.getPath();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public Document getPageFromDbOrDownload(String pageType, String pageUrl) throws PageNotFoundException {
        return getPageFromDbOrDownload(pageType, pageUrl, null);
    }

    public Document getPageFromDbOrDownload(String pageType, String pageUrl, String metadata)
            throws PageNotFoundException {
        try {
            if (pageSourceRepository.notExistByPageUrl(pageUrl)) {
                Document page = jsoupUtils.getPage(driver, pageUrl);

                if (page != null) {
                    PageSource pageSource = new PageSource();
                    pageSource.setCountry(Country.USA);
                    pageSource.setPageType(pageType);
                    pageSource.setPageUrl(pageUrl);
                    pageSource.setMetadata(metadata);
                    pageSource.setRawSource(page.toString());

                    saveInNewTransaction(pageSource);

                    return page;
                } else {
                    throw new PageNotFoundException(String.format("Failed to download page: %s;", pageUrl));
                }
            } else {
                PageSource pageSource = pageSourceRepository.findByPageUrl(pageUrl).get();
                if (metadata != null && pageSource.getMetadata() == null) {
                    pageSource.setMetadata(metadata);
                    saveInNewTransaction(pageSource);
                }

                String source = pageSource.getRawSource();

                if (!source.isEmpty()) {
                    return Jsoup.parse(source);
                }
            }

            throw new PageNotFoundException(String.format("Page not found: %s;", pageUrl));
        } catch (DataIntegrityViolationException e) {
            log.error("Unable to save record, because some of the properties violates not-null constraint");
            throw new PageNotFoundException();
        } catch (CaptchaException e) {
            log.error("Banned from page: " + e);
            throw new RuntimeException(e);
        }
    }

    private void saveInNewTransaction(PageSource data) {
        transactionTemplate.execute(status -> pageSourceRepository.save(data));
    }
}
