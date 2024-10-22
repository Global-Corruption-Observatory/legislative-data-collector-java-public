package com.precognox.ceu.legislative_data_collector.uk;

import com.precognox.ceu.legislative_data_collector.common.ChromeBrowserPool;
import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.utils.DocumentDownloader;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import com.precognox.ceu.legislative_data_collector.utils.selenium.SeleniumUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.precognox.ceu.legislative_data_collector.uk.CommonConstants.BILL_PUBLICATIONS_PAGE_TEMPLATE;

@Slf4j
@Service
public class CommitteesDepthCollector {

    @Autowired
    private ChromeBrowserPool browserPool;

    @Autowired
    private DocumentDownloader pdfDownloader;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PageSourceLoader pageSourceLoader;

    @Transactional
    public void collectCommitteesDepth() {
        String qlString = "SELECT r FROM LegislativeDataRecord r" +
                " WHERE r.country = :country" +
                " AND r.committeeDepth IS NULL";

        Stream<LegislativeDataRecord> bills = entityManager.createQuery(qlString, LegislativeDataRecord.class)
                .setParameter("country", Country.UK)
                .getResultStream();

        bills.forEach(this::collectCommitteesDepth);
    }

    public void collectForOneRecord(String recordId) {
        LegislativeDataRecord record = entityManager.find(LegislativeDataRecord.class, recordId);

        if (record != null) {
            collectCommitteesDepth(record);
        } else {
            throw new IllegalArgumentException("Record missing with ID: " + recordId);
        }
    }

    private void collectCommitteesDepth(LegislativeDataRecord record) {
        ChromeDriver browser = null;
        String pageUrl = BILL_PUBLICATIONS_PAGE_TEMPLATE.formatted(record.getBillId());

        try {
            browser = SeleniumUtils.getBrowserWithRandomProxy();
            pageSourceLoader.loadInBrowser(browser, pageUrl);
            WebElement debatesDiv = browser.findElement(By.cssSelector("div#collapse-publication-committee-debate"));
            List<WebElement> cards = debatesDiv.findElements(By.cssSelector("div.card-button"));

            transactionTemplate.execute(status -> {
                record.setCommitteeHearingCount(cards.size());
                return status;
            });

            int sumDebateSize = 0;

            for (WebElement card : cards) {
                List<WebElement> links = card.findElements(By.tagName("a"));

                Optional<Integer> debateSize = links.stream()
                        .filter(l -> SeleniumUtils.getText(l).contains("PDF"))
                        .findFirst()
                        .map(pdfLink -> pdfLink.getAttribute("href"))
                        .flatMap(pdfDownloader::processWithBrowser)
                        .map(TextUtils::getLengthWithoutWhitespace);

                if (debateSize.isPresent()) {
                    sumDebateSize += debateSize.get();
                } else {
                    //pdf link must be missing
                    String htmlLink = links.get(0).getAttribute("href");
                    Document page = Jsoup.connect(htmlLink).get();
                    if (!page.body().text().contains("The content you are trying to access is not available")) {
                        org.jsoup.nodes.Element textDiv = page.body().getElementById("maincontent1");
                        if (textDiv == null) {
                            textDiv = page.body().getElementById("mainTextBlock");
                        }

                        if (textDiv != null) {
                            sumDebateSize += textDiv.text().length();
                        } else {
                            Elements articleTags = page.body().getElementsByTag("article");

                            if (!articleTags.isEmpty()) {
                                sumDebateSize += articleTags.get(0).text().length();
                            }
                        }
                    }
                }
            }

            int committeeDepth = sumDebateSize;

            transactionTemplate.execute(status -> {
                record.setCommitteeDepth(committeeDepth);
                entityManager.merge(record);
                log.info("Set committee depth: {} for record: {}", committeeDepth, record.getId());

                return status;
            });
        } catch (NoSuchElementException e) {
            //can be normal
            log.warn("Expected element not found on page: {} - {}", pageUrl, e.getRawMessage());
        } catch (Exception e) {
            log.error("", e);
        } finally {
            if (browser != null) { browser.close(); }
        }
    }

}
