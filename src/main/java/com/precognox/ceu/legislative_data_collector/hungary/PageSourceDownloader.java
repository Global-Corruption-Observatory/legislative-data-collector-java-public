package com.precognox.ceu.legislative_data_collector.hungary;

import com.jauntium.Browser;
import com.jauntium.Document;
import com.jauntium.NotFound;
import com.precognox.ceu.legislative_data_collector.entities.BillUrl;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.BillUrlRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.selenium.SeleniumUtils;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverWaitExtend;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Downloads and stores all bill pages from the Hungarian parliament website.
 */
@Slf4j
@Service
public class PageSourceDownloader {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BillUrlRepository billUrlRepository;

    @Autowired
    private PageSourceRepository pageSourceRepository;

    /**
     * Used for testing or to reproduce errors.
     *
     * @param url The bill page URL.
     */
    public void downloadSingleBill(String url) {
        processUrl(url);
    }

    /**
     * Works with the links stored in the previous step. Stores the raw sources of bill pages in the 'page_source'
     * table.
     */
    public void downloadPages() {
        log.info("Processing links...");
        List<BillUrl> newLinks = billUrlRepository.findUnprocessedUrls(Country.HUNGARY);

        log.info("Found {} links to process", newLinks.size());

        newLinks.forEach(url ->  {
            try {
                processUrl(url.getUrl());
            } catch (Exception e) {
                log.error("Failed to process URL: " + url.getUrl(), e);
            }
        });

        log.info("Finished downloading pages");
    }

    private void processUrl(String billUrl) {
        if (existsByPeriodAndBillIdParam(getPeriodAndBillIdParam(billUrl).get())) {
            log.info("Skipping URL based on existing period and bill ID: {}", billUrl);
        } else {
            log.debug("Visiting bill page: {}", billUrl);
            Browser browser = new Browser(SeleniumUtils.getBrowserWithRandomProxy());

            try {
                Document billPage = browser.visit(billUrl);

                if (SeleniumUtils.isCaptchaOrError(browser.driver)) {
                    log.error("Captcha encountered");
                    System.exit(1);
                }

                waitForElement(browser, "irom-cim");

                WebElement keresesBeallitasiButton = billPage.findFirst("<button class=\"btn-primary\">");
                keresesBeallitasiButton.click();

                try {
                    billPage
                            .findFirst("<input class=\"p_bizjelen\">")
                            .setAttribute("checked", "true");
                } catch (NotFound e) {
                    //normal
                    log.warn("Bizottsági jelentések checkbox not found on page {}", billUrl);
                }

                billPage.getForm(2).submit();
                waitForElement(browser, "irom-cim");

                PageSource storedSource = new PageSource();
                storedSource.setPageUrl(billUrl);
                storedSource.setCleanUrl(Utils.cleanUrl(billUrl));
                storedSource.setCollectionDate(LocalDate.now());
                storedSource.setCountry(Country.HUNGARY);
                storedSource.setPageType(PageType.BILL.name());
                storedSource.setRawSource(browser.getSource());
                storedSource.setSize(browser.getSource().length());

                pageSourceRepository.save(storedSource);
                log.info("Stored page: {}", billUrl);
            } catch (NotFound e) {
                log.error("Expected element not found", e);
            } finally {
                browser.quit();
            }
        }
    }

    private void waitForElement(Browser browser, String className) {
        new WebDriverWaitExtend(browser.driver, 45).until(
                ExpectedConditions.presenceOfElementLocated(By.className(className))
        );
    }

    private Optional<String> getPeriodAndBillIdParam(String url) {
        return Pattern.compile("p_ckl%3D\\d+%26p_izon%3D\\d+")
                .matcher(url)
                .results()
                .map(MatchResult::group)
                .findFirst();
    }

    private boolean existsByPeriodAndBillIdParam(String billIdParam) {
        String q = "SELECT COUNT(s) > 0 FROM PageSource s WHERE s.pageType = ''BILL'' AND s.pageUrl LIKE ''%{0}''";
        return entityManager.createQuery(MessageFormat.format(q, billIdParam), Boolean.class).getSingleResult();
    }

}
