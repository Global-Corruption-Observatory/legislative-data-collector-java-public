package com.precognox.ceu.legislative_data_collector.common;

import com.jauntium.Browser;
import com.jauntium.Document;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.hungary.Utils;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.selenium.SeleniumUtils;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverWaitExtend;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads stored pages from the DB, or opens a browser to fetch a page if it was not stored before.
 */
@Slf4j
@Service
public class PageSourceLoader {

    private final BrowserPool browserPool;
    private final PageSourceRepository pageSourceRepository;
    private final TransactionTemplate transactionTemplate;

    private static final int TIMEOUT = 45;

    //for tracking statistics of the effectiveness of caching in DB
    private int totalPageLoads = 0;
    private int loadsFromNetwork = 0;

    @Autowired
    public PageSourceLoader(
            BrowserPool browserPool,
            PageSourceRepository pageSourceRepository,
            TransactionTemplate transactionTemplate) {
        this.browserPool = browserPool;
        this.pageSourceRepository = pageSourceRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public Document fetchParsedDocument(Browser browser, String pageType, String url) {
        return fetchParsedDocument(browser, pageType, url, null);
    }

    public Document fetchParsedDocument(
            Browser browser, String pageType, String url, String waitForElementClassName) {
        PageSource stored = loadFromDbOrFetchWithBrowser(pageType, url, waitForElementClassName);
        return loadCode(browser, stored.getRawSource());
    }

    public Optional<PageSource> loadFromDbOrFetchWithHttpGet(Country country, String pageType, String url) {
        totalPageLoads++;
        printStatistics();

        List<PageSource> stored = pageSourceRepository.findAllByPageTypeAndPageUrl(pageType, url);

        if (!stored.isEmpty()) {
            return Optional.ofNullable(stored.get(0));
        }

        try {
            HttpResponse<String> resp = Unirest.get(url).asString();

            if (resp.isSuccess()) {
                PageSource pageSource = new PageSource();
                pageSource.setPageUrl(url);
                pageSource.setPageType(pageType);
                pageSource.setRawSource(resp.getBody());
                pageSource.setSize(resp.getBody().length());
                pageSource.setCountry(country);

                log.debug("Storing page in DB: {}", url);

                return Optional.ofNullable(
                        transactionTemplate.execute(status -> pageSourceRepository.save(pageSource))
                );
            } else {
                log.error("{} HTTP response received for URL: {}", resp.getStatus(), url);
            }
        } catch (UnirestException e) {
            log.error("Failed to fetch page: " + url, e);
        }

        return Optional.empty();
    }

    /**
     * @param pageType A value from the country-specific page type enums.
     * @param url The page URL.
     * @param waitForElementClassName Waits for the given element to load on the page, to avoid partial loads.
     *
     * @return PageSource entity.
     */
    public PageSource loadFromDbOrFetchWithBrowser(
            String pageType, String url, @Nullable String waitForElementClassName) {
        totalPageLoads++;
        printStatistics();

        Optional<PageSource> stored;

        if (url.contains("parlament.hu")) {
            stored = pageSourceRepository.findFirstByCleanUrl(Utils.cleanUrl(url));
        } else {
            stored = pageSourceRepository.findByPageTypeAndPageUrl(pageType.toUpperCase(), url);
        }

        if (stored.isPresent()) {
            return stored.get();
        }

        ChromeDriver browser = SeleniumUtils.getBrowserWithRandomProxy();

        try {
            browser.get(url);
            loadsFromNetwork++;

            if (SeleniumUtils.isCaptchaOrError(browser)) {
                //retry without proxy
                browser.quit();
                browser = new ChromeDriver();
                browser.get(url);

                if (SeleniumUtils.isCaptchaOrError(browser)) {
                    log.error("Captcha or error page encountered");
                    System.exit(1);
                }
            }

            if (waitForElementClassName != null) {
                waitForElement(browser, waitForElementClassName);
            }

            PageSource pageSource = new PageSource();
            pageSource.setCountry(getCountryFromUrl(url));
            pageSource.setRawSource(browser.getPageSource());
            pageSource.setCollectionDate(LocalDate.now());
            pageSource.setPageType(pageType.toUpperCase());
            pageSource.setPageUrl(url);
            pageSource.setCleanUrl(Utils.cleanUrl(url));

            return pageSourceRepository.save(pageSource);
        } finally {
            browser.quit();
        }
    }

    private Country getCountryFromUrl(String url) {
        //add others when needed
        Map<String, Country> countryMapping = Map.of(
                ".hu", Country.HUNGARY,
                ".se", Country.SWEDEN,
                ".bg", Country.BULGARIA,
                ".cl", Country.CHILE,
                ".uk", Country.UK
        );

        for (String key : countryMapping.keySet()) {
            if (url.contains(key)) {
                return countryMapping.get(key);
            }
        }

        throw new IllegalArgumentException("Can not determine country from URL: " + url);
    }

    private void printStatistics() {
        if (totalPageLoads % 50 == 0) {
            log.info("Statistics for PageSourceLoader: ");
            log.info(
                    "Total page loads: {}, from web: {} ({}%)",
                    totalPageLoads, loadsFromNetwork, (loadsFromNetwork / (double) totalPageLoads) * 100
            );
        }
    }

    private void waitForElement(ChromeDriver browser, String className) {
        new WebDriverWaitExtend(browser, TIMEOUT).until(
                ExpectedConditions.presenceOfElementLocated(By.className(className))
        );
    }

    public Document loadCode(Browser browser, String source) {
        String encoded = Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8));

        return browser.visit("data:text/html;base64," + encoded);
    }

    public void loadInBrowser(ChromeDriver browser, String url) {
        pageSourceRepository.findByPageUrl(url).ifPresentOrElse(
                stored -> loadCode(browser, stored.getRawSource()),
                () -> fetchAndStore(browser, url)
        );
    }

    private void loadCode(ChromeDriver browser, String source) {
        String encoded = Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8));
        browser.get("data:text/html;base64," + encoded);
    }

    private void fetchAndStore(ChromeDriver browser, String url) {
        browser.get(url);
        SeleniumUtils.checkCaptcha(browser);

        PageSource storedSource = new PageSource();
        storedSource.setRawSource(browser.getPageSource());
        storedSource.setPageUrl(url);

        pageSourceRepository.save(storedSource);
    }

}
