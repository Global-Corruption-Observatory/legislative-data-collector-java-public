package com.precognox.ceu.legislative_data_collector.uk;

import com.precognox.ceu.legislative_data_collector.common.ChromeBrowserPool;
import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import com.precognox.ceu.legislative_data_collector.utils.selenium.SeleniumUtils;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class StageDebateCollector {

    @Autowired
    private ChromeBrowserPool browserPool;

    @Autowired
    private PageSourceLoader pageSourceLoader;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PageSourceRepository pageSourceRepository;

    private static final List<String> COUNTED_DEBATE_STAGES = List.of(
            "Commons 1st reading", "Commons 2nd reading", "Commons 3rd reading", "Commons Report stage",
            "Lords 1st reading", "Lords 2nd reading", "Lords 3rd reading", "Lords Report stage",
            "Commons Consideration of Lords amendments", "Lords Consideration of Commons amendments"
    );

    @Transactional
    public void collectStageDebateSizes() {
        String qlString = "SELECT r FROM LegislativeDataRecord r" +
                " WHERE r.country = :country" +
                " AND r.stagesCount > 0";

        Stream<LegislativeDataRecord> bills = entityManager.createQuery(qlString, LegislativeDataRecord.class)
                .setParameter("country", Country.UK)
                .getResultStream();

        bills.forEach(this::collectStageDebateSizes);
    }

    @Transactional
    public void collectForOneRecord(String recordId) {
        LegislativeDataRecord record = entityManager.find(LegislativeDataRecord.class, recordId);

        if (record != null) {
            collectStageDebateSizes(record);
        } else {
            throw new IllegalArgumentException("Record missing with ID: " + recordId);
        }
    }

    private void collectStageDebateSizes(LegislativeDataRecord record) {
        ChromeDriver browser = SeleniumUtils.getBrowserWithRandomProxy();
        ChromeDriver secondWindow = SeleniumUtils.getBrowserWithRandomProxy();

        String publicationsPageUrl = CommonConstants.BILL_PUBLICATIONS_PAGE_TEMPLATE.formatted(record.getBillId());

        try {
            pageSourceLoader.loadInBrowser(browser, publicationsPageUrl);

            WebElement debatesDiv = browser.findElement(By.cssSelector("div#collapse-publication-debate"));
            List<WebElement> cards = debatesDiv.findElements(By.cssSelector("div.card-group"));

            for (WebElement card : cards) {
                try {
                    WebElement title = card.findElement(By.tagName("h4"));
                    String stageName = SeleniumUtils.getText(title);

                    Optional<LegislativeStage> storedStage = record.getStages()
                            .stream()
                            .filter(stage -> stage.getName().equals(stageName))
                            .filter(stage -> stage.getDebateSize() == null)
                            .findFirst();

                    if (COUNTED_DEBATE_STAGES.contains(stageName) && storedStage.isPresent()) {
                        String link = card.findElement(By.cssSelector("a.dropdown-item")).getAttribute("href");

                        secondWindow.get(link);
                        SeleniumUtils.checkCaptcha(secondWindow);

                        Stream<String> textDivSelectors =
                                Stream.of("div.content", "div#content", "div#maincontent1", "article");

                        Optional<WebElement> textDiv = textDivSelectors
                                .map(selector -> secondWindow.findElements(By.cssSelector(selector)))
                                .filter(webElements -> webElements.size() == 1)
                                .map(webElements -> webElements.get(0))
                                .findFirst();

                        Integer stageDebateLength = null;

                        if (textDiv.isPresent()) {
                            String debateText = SeleniumUtils.getText(textDiv.get());
                            stageDebateLength = TextUtils.getLengthWithoutWhitespace(debateText);
                        } else {
                            if (link.contains("#")) {
                                String anchor = link.split("#")[1];
                                List<WebElement> textDivs =
                                        secondWindow.findElements(By.cssSelector("div#content-small"));

                                if (textDivs.size() == 1) {
                                    String source = textDivs.get(0).getAttribute("innerHTML");
                                    Matcher textStartMatcher =
                                            Pattern.compile("<a class=\"anchor noCont\" name=\"" + anchor + "\">").matcher(source);

                                    if (textStartMatcher.find()) {
                                        int textStartIndex = textStartMatcher.toMatchResult().end();
                                        String substring = source.substring(textStartIndex);
                                        Matcher nextBillHeaderMatcher =
                                                Pattern.compile("<h3", Pattern.MULTILINE).matcher(substring);

                                        Optional<Integer> textEndIndex = nextBillHeaderMatcher.results()
                                                .skip(1)
                                                .findFirst()
                                                .map(MatchResult::start);

                                        String debateTextRegion = textEndIndex.isPresent()
                                                ? substring.substring(0, textEndIndex.get())
                                                : substring;

                                        String htmlRemoved = debateTextRegion.replaceAll("<.+?>", "");
                                        stageDebateLength = TextUtils.getLengthWithoutWhitespace(htmlRemoved);
                                    }
                                }
                            }
                        }

                        if (stageDebateLength != null) {
                            storedStage.get().setDebateSize(stageDebateLength);
                            log.info("Set debate size: {}", stageDebateLength);
                        } else {
                            log.warn("Failed to collect debate text from page: " + link);
                        }
                    }
                } catch (NoSuchElementException e) {
                    //can be normal
                    log.debug("Element not found", e);
                } catch (WebDriverException e) {
                    log.error("Cannot go to debate page", e);
                }
            }

            int plenarySize = record.getStages()
                    .stream()
                    .filter(stg -> stg.getDebateSize() != null)
                    .mapToInt(LegislativeStage::getDebateSize)
                    .sum();

            record.setPlenarySize(plenarySize);

            transactionTemplate.execute(status -> {
                entityManager.merge(record);
                return status;
            });
        } catch (NoSuchElementException e) {
            //normal - no debates
            log.debug("Can not scrape debate length for bill: {}", record.getBillPageUrl());
            record.getErrors().add("Can not get debate size - expected element not found on page");
        } catch (Exception e) {
            log.error("Failed to collect debate size from page: " + publicationsPageUrl, e);
        } finally {
            if (browser != null) { browser.quit(); }
            if (secondWindow != null) { secondWindow.quit(); }
        }
    }

    private Optional<String> findDownloadTextLink(ChromeDriver browser) {
        try {
            return browser.findElement(By.cssSelector("div.hero-banner"))
                    .findElements(By.tagName("a"))
                    .stream()
                    .filter(link -> "Download text".equals(SeleniumUtils.getText(link)))
                    .findFirst()
                    .map(link -> link.getAttribute("href"));
        } catch (NoSuchElementException e) {
            log.error("Can not get debate size - download text link not found on page: {}", browser.getCurrentUrl());
        }

        return Optional.empty();
    }

}
