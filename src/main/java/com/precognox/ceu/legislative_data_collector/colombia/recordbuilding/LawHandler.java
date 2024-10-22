package com.precognox.ceu.legislative_data_collector.colombia.recordbuilding;

import com.precognox.ceu.legislative_data_collector.colombia.constants.PageType;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.exceptions.PageResponseException;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.ReadDatabaseService;
import com.precognox.ceu.legislative_data_collector.utils.selenium.SeleniumUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.NotFoundException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import javax.persistence.NonUniqueResultException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.MONTHS_TRANSLATIONS;
import static com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.GazetteWebpageHandler.ARTICLE_START_TEXT_REGEX;
import static com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.GazetteWebpageHandler.START_TEXT_REGEX;

/**
 * This class handles the gathering of information from the senate page
 * Except: the modification information, which are processed in AffectedLawParser (but pages still collected here)
 */
@Slf4j
public class LawHandler {
    public static final Pattern LAW_ID_REGEX = Pattern.compile("(\\d+)/(\\d+)");
    public static final Pattern ORIGINAL_LAW_ID_REGEX = Pattern.compile("^(\\D+)\\s+(\\d+)\\s*[dD][eE]\\s*(\\d+)");
    private static final String SENATE_WEBPAGE_BASE = "http://www.secretariasenado.gov.co/senado/basedoc/%s";
    private static final String SENATE_WEBPAGE_NORMAL = "http://www.secretariasenado.gov.co/senado/basedoc/ley_%04d_%d.html";
    private static final String SENATE_WEBPAGE_LEGISLATIVE =
            "http://www.secretariasenado.gov.co/senado/basedoc/acto_legislativo_%02d_%d.html";
    private static final Pattern DATE_OF_ENTERING_INTO_FORCE_REGEX =
            Pattern.compile("^<Rige a partir\\D*(\\d+)\\s*de\\s*(\\w+)\\s*de\\s*(\\d+)[^>]*>$");
    private static final Pattern PUBLICATION_DATE_REGEX =
            Pattern.compile("Diario\\D+[\\d.]+.*?(?:de|\\s)\\s*(\\d+)[o.]*\\sde\\s([a-zA-z]+)\\D*(\\d+)");
    private static final Pattern INVERTED_MONTH_DAY_PUBLICATION_DATE_REGEX =
            Pattern.compile("Diario\\D+[\\d.,]+\\s*de\\s*(\\w+)\\s*(\\d+)\\D*(\\d+)");
    private final String lawId;
    private final String billType;
    private final ReadDatabaseService readService;
    @Getter
    private Optional<PageSource> pageSource;
    @Getter
    private List<PageSource> textPages;

    public LawHandler(String lawId, String billType, ReadDatabaseService readService) throws DataCollectionException {
        if (LAW_ID_REGEX.matcher(lawId).find()) {
            this.lawId = lawId;
            this.pageSource = Optional.empty();
            this.billType = billType;
            this.readService = readService;
            this.textPages = new ArrayList<>();
        } else {
            throw new DataCollectionException("Invalid law id");
        }
    }

    public static String createUniformLawId(String lawId) throws DataCollectionException {
        Matcher lawIdMatcher = ORIGINAL_LAW_ID_REGEX.matcher(lawId);
        if (lawIdMatcher.find()) {
            return String.format("%s/%s", lawIdMatcher.group(3), lawIdMatcher.group(2));
        } else {
            throw new DataCollectionException("Incorrect law id, cannot make it uniform");
        }
    }

    public LocalDate getDateOfEnteringIntoForce() throws DataCollectionException {
        Document page = getSenatePage();
        List<String> paragraphs = page.select("#aj_data > #aj_data > p")
                .stream()
                .map(paragraph -> paragraph.text().trim())
                .filter(text -> !text.isBlank())
                .toList();

        Optional<LocalDate> dateOfEnteringForce = tryReadingDateOfEnteringForceWithRegex(paragraphs, DATE_OF_ENTERING_INTO_FORCE_REGEX);
        if (dateOfEnteringForce.isPresent()) {
            return dateOfEnteringForce.get();

        } else {
            dateOfEnteringForce = tryReadingDateOfEnteringForceWithRegex(paragraphs, PUBLICATION_DATE_REGEX);
            if (dateOfEnteringForce.isPresent()) {
                return dateOfEnteringForce.get();

            } else {
                dateOfEnteringForce = tryReadingDateOfEnteringForceWithRegex(paragraphs, INVERTED_MONTH_DAY_PUBLICATION_DATE_REGEX);
                if (dateOfEnteringForce.isPresent()) {
                    return dateOfEnteringForce.get();
                } else {
                    throw new DataCollectionException("No date of entering into force found");
                }
            }
        }
    }

    public Document getSenatePage() throws DataCollectionException {
        if (pageSource.isPresent()) {
            return Jsoup.parse(pageSource.get().getRawSource());
        } else {
            String url = createSenatePageUrl();
            try {
                pageSource = Optional.ofNullable(getSenatePageSource(url));
                if (pageSource.isPresent()) {
                    return Jsoup.parse(pageSource.get().getRawSource());
                }
                throw new DataCollectionException("Cannot find senate page");
            } catch (PageResponseException ex) {
                throw new DataCollectionException(String.format("Senate page was unreachable [%s]", ex.getMessage()));
            }
        }
    }

    public String createSenatePageUrl() {
        Matcher lawIdMatcher = LAW_ID_REGEX.matcher(lawId);
        lawIdMatcher.find();
        int lawNumber = Integer.parseInt(lawIdMatcher.group(2));
        int lawYear = Integer.parseInt(lawIdMatcher.group(1));
        if (Objects.nonNull(billType)) {
            String webpageBase =
                    (billType.toLowerCase().contains("acto")) ? SENATE_WEBPAGE_LEGISLATIVE : SENATE_WEBPAGE_NORMAL;
            return String.format(webpageBase, lawNumber, lawYear);
        }
        return String.format(SENATE_WEBPAGE_NORMAL, lawNumber, lawYear);
    }

    private PageSource getSenatePageSource(String url) throws PageResponseException {
        Optional<PageSource> optPageSource;
        try {
            optPageSource = readService.findByPageTypeAndPageUrl(PageType.LAW.label, url);
        } catch (IncorrectResultSizeDataAccessException | NonUniqueResultException ex) {
            log.warn("Law page url exist multiple times in the database! {}", url);
            optPageSource = Optional.ofNullable(readService.findAllByPageUrl(url).get(0));
        }
        return optPageSource.orElseGet(() -> downloadSenatePageSource(url));
    }

    private PageSource downloadSenatePageSource(String url) {
        final WebDriver browser = SeleniumUtils.getChromeBrowserForColombia();
        try {
            browser.get(url);
            Optional<WebElement> modifiedByLink =
                    browser.findElements(By.cssSelector("#aj_data #aj_data div a")).stream()
                            .filter(link -> link.getText().equals("Resumen de Notas de Vigencia"))
                            .findFirst();
            if (modifiedByLink.isPresent()) {
                modifiedByLink.get().click();
            } else {
                log.info("No modification data found on page: {}", url);
            }
            return SeleniumUtils.downloadColombianPageSource(browser, PageType.LAW.label);
        } catch (RuntimeException ex) {
            throw new NotFoundException(String.format("Page %s unreachable. Moving on!", url));
        } finally {
            browser.quit();
        }
    }

    private Optional<LocalDate> tryReadingDateOfEnteringForceWithRegex(List<String> paragraphs, Pattern regex) {
        return paragraphs.stream()
                .map(regex::matcher)
                .filter(Matcher::find)
                .map(this::getDateOfEnteringIntoForceFromText)
                .findFirst();
    }

    private LocalDate getDateOfEnteringIntoForceFromText(Matcher dateMatcher) {
        int day;
        int month;
        int year;
        if (dateMatcher.pattern().equals(INVERTED_MONTH_DAY_PUBLICATION_DATE_REGEX)) {
            day = Integer.parseInt(dateMatcher.group(2));
            month = MONTHS_TRANSLATIONS.get(dateMatcher.group(1).toLowerCase());
        } else {
            day = Integer.parseInt(dateMatcher.group(1));
            month = MONTHS_TRANSLATIONS.get(dateMatcher.group(2).toLowerCase());
        }
        year = Integer.parseInt(dateMatcher.group(3));
        return DateUtils.parseColombiaLawTextDate(String.format("%d/%d/%d", day, month, year));
    }

    public String getLawTextFromSenatePage() throws DataCollectionException {
        Document rootPage;
        try {
            rootPage = getSenatePage();
        } catch (DataCollectionException ex) {
            throw new DataCollectionException(String.format("Senate page unreachable while collecting law text [%s]", ex.getMessage()));
        }
        Optional<String> nextPageLink = getNextPageLink(rootPage);
        if (nextPageLink.isPresent()) {
            StringBuilder builder = new StringBuilder();
            builder.append(getTextFromPage(rootPage));
            String rawText = getLawTextFromMultiplePages(builder, nextPageLink);
            Matcher startMatcher = START_TEXT_REGEX.matcher(rawText);
            if (startMatcher.find()) {
                return removeNotRequiredParts(rawText.substring(startMatcher.end()).replaceAll("^\\W*", "").trim());
            }
            startMatcher = ARTICLE_START_TEXT_REGEX.matcher(rawText);
            if (startMatcher.find()) {
                return removeNotRequiredParts(rawText.substring(startMatcher.start()).trim());
            }
            return rawText;
        } else {
            return removeNotRequiredParts(getLawTextFromOnePage(rootPage));
        }
    }

    private Optional<String> getNextPageLink(Document page) {
        return page.select("#aj_data #aj_data .antsig")
                .stream()
                .filter(element -> element.text().trim().equalsIgnoreCase("Siguiente"))
                .map(element -> element.attr("href"))
                .findFirst();
    }

    private String getLawTextFromMultiplePages(StringBuilder builder, Optional<String> pageLink)
            throws DataCollectionException {
        while (pageLink.isPresent()) {
            Document page = getLawTextPage(String.format(SENATE_WEBPAGE_BASE, pageLink.get()));
            builder.append(getTextFromPage(page));
            pageLink = getNextPageLink(page);
        }
        return builder.toString();
    }

    private Document getLawTextPage(String url) throws DataCollectionException {
        Optional<PageSource> pageSource;
        try {
            pageSource = readService.findByPageTypeAndPageUrl(PageType.LAW_TEXT.label, url);
        } catch (IncorrectResultSizeDataAccessException | NonUniqueResultException ex) {
            log.warn("Law text page url exist multiple times in the database! {}", url);
            pageSource = Optional.ofNullable(readService.findAllByPageUrl(url).get(0));
        }
        if (pageSource.isPresent()) {
            textPages.add(pageSource.get());
            return Jsoup.parse(pageSource.get().getRawSource());
        }
        try {
            PageSource source = collectLawTextPageSource(url);
            textPages.add(source);
            return Jsoup.parse(source.getRawSource());
        } catch (PageResponseException ex) {
            throw new DataCollectionException(
                    String.format("Page %s couldn't be found in the database or reached, law text not collected", url));
        }
    }

    private PageSource collectLawTextPageSource(String url) throws PageResponseException {
        ChromeDriver browser = SeleniumUtils.getChromeBrowserForColombia();
        try {
            browser.get(url);
            return SeleniumUtils.downloadColombianPageSource(browser, PageType.LAW_TEXT.label);
        } catch (RuntimeException ex) {
            throw new NotFoundException(String.format("Page %s unreachable. Moving on!", url));
        } finally {
            browser.quit();
        }


    }

    private String getTextFromPage(Document page) {
        Elements paragraphs = page.select("#aj_data > #aj_data > p");
        StringBuilder builder = new StringBuilder();
        boolean build = false;

        for (Element paragraph : paragraphs) {
            Elements pagingLinks = paragraph.select(".antsig");
            if (!pagingLinks.isEmpty()) {
                build = !build;
                if (build) {
                    continue; // So the link text not included
                } else {
                    break;
                }
            }
            if (build) {
                String text = paragraph.text();
                builder.append(text);
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private String getLawTextFromOnePage(Document page) {
        Elements paragraphs = page.select("#aj_data > #aj_data > p");
        StringBuilder builder = new StringBuilder();
        boolean build = false;

        for (Element paragraph : paragraphs) {
            String text = paragraph.text();
            if (build) {
                builder.append(text);
                builder.append('\n');
            } else {
                if (START_TEXT_REGEX.matcher(text).find()) {
                    build = true;
                } else if (ARTICLE_START_TEXT_REGEX.matcher(text).find()) {
                    builder.append(text);
                    builder.append('\n');
                    build = true;
                }
            }
        }
        return builder.toString();
    }

    private String removeNotRequiredParts(String text) {
        return text.replaceAll("<[^>]*?[Nn]otas\\s*[Dd]el\\s*[Ee]ditor.*?>", "")
                .replaceAll("<[^>]*?[Jj]urisprudencia\\s*[Vv]igencia.*?>", "")
                .trim();
    }
}
