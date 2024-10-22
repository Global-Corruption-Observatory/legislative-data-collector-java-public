package com.precognox.ceu.legislative_data_collector.australia;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.chile.AffectingLawDetailed;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.DocumentDownloader;
import kong.unirest.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class AustraliaWeb2Parser {
    public AustraliaWeb2Parser(DocumentDownloader documentDownloader, EntityManager entityManager,
                               PageSourceRepository pageSourceRepository,
                               LegislativeDataRepository legislativeDataRepository,
                               PrimaryKeyGeneratingRepository keyGeneratingRepository,
                               TransactionTemplate transactionTemplate,
                               AustraliaCommonFunctions australiaCommonFunctions) {
        this.documentDownloader = documentDownloader;
        this.entityManager = entityManager;
        this.pageSourceRepository = pageSourceRepository;
        this.legislativeDataRepository = legislativeDataRepository;
        this.keyGeneratingRepository = keyGeneratingRepository;
        this.transactionTemplate = transactionTemplate;
        this.australiaCommonFunctions = australiaCommonFunctions;
    }

    private PrimaryKeyGeneratingRepository keyGeneratingRepository;

    private LegislativeDataRepository legislativeDataRepository;

    private PageSourceRepository pageSourceRepository;

    private final EntityManager entityManager;

    private final DocumentDownloader documentDownloader;

    private final TransactionTemplate transactionTemplate;

    private final AustraliaCommonFunctions australiaCommonFunctions;

    private WebDriver driver;

    private WebDriverWait wait;

    private static final String AFFECTING_LAWS_BUTTON_LOCATOR = "ngb-nav-7";
    private static final String MODIFIED_LAWS_BUTTON_LOCATOR = "ngb-nav-8";

    public void parseAustraliaWeb2Pages() {
        try {
            driver = new ChromeDriver();
            wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            int currentBatch = 0;
            int batchSize = 5;
            int maxBatch;

            do {
                Page<LegislativeDataRecord> records = keyGeneratingRepository.findUnprocessLawsForAu(Country.AUSTRALIA,
                        PageRequest.of(currentBatch, batchSize));

                records.forEach(this::parsePage);

                currentBatch++;
                maxBatch = records.getTotalPages();
            } while (currentBatch <= maxBatch);
        } finally {
            driver.quit();
        }
    }

    private void parsePage(LegislativeDataRecord record) {
        Optional<PageSource> pageSourceOptional = getAustraliaWeb2PageSource(record);

        if (pageSourceOptional.isPresent()) {
            log.info("Parsing record from website2: {} - {}", record.getRecordId(),
                    pageSourceOptional.get().getPageUrl());

            PageSource pageSource = pageSourceOptional.get();

            String pageUrl = pageSource.getPageUrl();
            String interactionsPageUrl = pageUrl + "/interactions";

            australiaCommonFunctions.getLawId(pageUrl).ifPresent(record::setLawId);

            record.setLawTextUrl(pageUrl);
            parseLawText(pageUrl, record.getDatePassing().toString()).ifPresent(record::setLawText);

            List<AffectingLawDetailed> affectingLawDetailed = parseAffectingLaws(interactionsPageUrl, record);
            record.setAffectingLawsDetailed(affectingLawDetailed);
            record.setAffectingLawsCount(affectingLawDetailed.size());
            getAffectingLawsFirstDate(affectingLawDetailed).ifPresent(record::setAffectingLawsFirstDate);
            log.info("Found {} affecting laws, affecting laws first date: {}", affectingLawDetailed.size(),
                    record.getAffectingLawsFirstDate());

            Set<String> modifiedLaws = parseModifiedLaws(interactionsPageUrl);
            record.setModifiedLaws(modifiedLaws);
            record.setModifiedLawsCount(modifiedLaws.size());
            log.info("Found {} modified laws", modifiedLaws.size());

            updateRecord(record);
        } else {
            log.warn("Website2 page source not found for record: {}", record.getRecordId());
        }
    }

    private void updateRecord(LegislativeDataRecord record) {
        transactionTemplate.execute(status -> {
            keyGeneratingRepository.merge(record);
            log.info("Record updated: {}", record.getRecordId());
            return record;
        });
    }

    private Optional<PageSource> getAustraliaWeb2PageSource(LegislativeDataRecord record) {
        JSONObject metaDataJson = new JSONObject();
        metaDataJson.put("actNumber", record.getAuCountrySpecificVariables().getActNumber());

        return pageSourceRepository.findByMetadata(metaDataJson.toString().replace("{", ""));
    }

    private Optional<String> parseLawText(String url, String datePassing) {
        driver.get(url);

        Optional<String> lawTextUrl = Optional.empty();

        if (url.contains("asmade")) {
            lawTextUrl = Optional.of(url + "/" + datePassing + "/text/original/pdf");
        } else if (url.contains("latest")) {
            lawTextUrl =
                    Optional.of(url.replace("/latest", "/" + datePassing + "/" + datePassing + "/text/original/pdf"));
        }

        if (lawTextUrl.isPresent()) {
            log.info("Downloading law text from: {}", lawTextUrl.get());
            return documentDownloader.processWithBrowser(lawTextUrl.get());
        } else {
            log.error("Law text URL not found for: {}", url);
            return Optional.empty();
        }
    }

//    Modified and affecting laws are parsed from the interactions page of the law.
//    To access them, we need to click on the specific button on the page. In case of modified laws, only the law ID is parsed.
    private Set<String> parseModifiedLaws(String interactionsPageUrl) {
        By modifiedLawsButton = By.id(MODIFIED_LAWS_BUTTON_LOCATOR);
        return parseInteractionsPage(interactionsPageUrl, modifiedLawsButton);
    }

//    In case of affecting laws, the law ID and the date of passing are parsed.
    private List<AffectingLawDetailed> parseAffectingLaws(String interactionsPageUrl, LegislativeDataRecord record) {
        List<AffectingLawDetailed> affectingLaws = new ArrayList<>();
        By affectingLawsButton = By.id(AFFECTING_LAWS_BUTTON_LOCATOR);

        parseInteractionsPage(interactionsPageUrl, affectingLawsButton).forEach(law -> {
            String[] lawParts = law.split(";");
            String lawId = lawParts[0];
            LocalDate datePassing = lawParts.length > 1 ? LocalDate.parse(lawParts[1]) : null;

            AffectingLawDetailed affectingLaw = new AffectingLawDetailed();
            affectingLaw.setDataRecord(record);
            affectingLaw.setAffectingLawId(lawId);
            affectingLaw.setAffectingRecord(findAffectingRecord(law));
            affectingLaw.setAffectingDate(datePassing);
            affectingLaws.add(affectingLaw);
        });

        return affectingLaws;
    }

    private LegislativeDataRecord findAffectingRecord(String law) {
        String query = "SELECT r " +
                "FROM LegislativeDataRecord r " +
                "WHERE r.lawId = :lawId";

        List<LegislativeDataRecord> records = entityManager.createQuery(query, LegislativeDataRecord.class)
                .setParameter("lawId", law)
                .getResultList();

        return records.isEmpty() ? null : records.get(0);
    }

    private Set<String> parseInteractionsPage(String interactionsPageUrl, By viewSelectorButton) {
        List<String> laws = new ArrayList<>();

        driver.get(interactionsPageUrl);

        WebElement modifiedLawsButton = wait.until(ExpectedConditions.presenceOfElementLocated(viewSelectorButton));
        clickOnElement(modifiedLawsButton);

        waitUntilPageFullyLoaded();

        Optional<Integer> modifiedLawsNum = getModifiedLawsNum();

        boolean hasNextPage = true;
        try {
            while (modifiedLawsNum.isPresent() && hasNextPage) {
                waitUntilPageFullyLoaded();

                wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("[role='table']"),
                        "Page size"));

                if (viewSelectorButton.toString().contains(AFFECTING_LAWS_BUTTON_LOCATOR)) {
                    laws.addAll(getAffectingLaws());
                } else if (viewSelectorButton.toString().contains(MODIFIED_LAWS_BUTTON_LOCATOR)) {
                    laws.addAll(getModifiedLaws());
                }

                hasNextPage = navigateToNextPage();
            }
        } catch (StaleElementReferenceException e) {
            log.error("Error while parsing modified laws: {}", e.getMessage());
        }

        return new HashSet<>(laws);
    }

    private Optional<Integer> getModifiedLawsNum() {
        Optional<Integer> modifiedLawsNum = Optional.empty();

        Optional<WebElement> pageCountElementOptional = Optional.ofNullable(
                wait.until(ExpectedConditions.presenceOfElementLocated(By.className("page-count"))));
        if (pageCountElementOptional.isPresent()) {
            WebElement pageCountElement = driver.findElement(By.className("page-count"));

            String pageCountText = pageCountElement.getText().split(" ")[0];

            modifiedLawsNum =
                    pageCountText.matches("\\d+") ? Optional.of(Integer.parseInt(pageCountText)) : Optional.empty();

            if (modifiedLawsNum.isPresent()) {
                setPageSizeToMaxiumum();
                waitUntilPageFullyLoaded();
            }
        }

        return modifiedLawsNum;
    }

    private void waitUntilPageFullyLoaded() {
        wait.until(driver -> ((JavascriptExecutor) driver)
                .executeScript("return document.readyState").equals("complete"));

        wait.until(ExpectedConditions.invisibilityOf(driver.findElement(By.className("loading-zone-page"))));

        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.tagName("datatable-progress")));


//        The usage of Thread.sleep() is a temporary, itt will be replaced with a more reliable solution.
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void setPageSizeToMaxiumum() {
        WebElement dropdownToggleButton = wait.until(
                ExpectedConditions.elementToBeClickable(By.cssSelector("button[id*='PageSize']")));
        clickOnElement(dropdownToggleButton);

        WebElement maxPageSizeButton = wait.until(
                        ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("[class*='dropdown-item small']")))
                .get(4);
        clickOnElement(maxPageSizeButton);

        waitUntilPageFullyLoaded();
    }

    private List<String> getAffectingLaws() {
//        When parsing affecting laws, the date of passing is also parsed. It is returned in the format "lawId;datePassing".

        List<String> laws = new ArrayList<>();
        List<WebElement> lawIds =
                wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.className("title-id")));

        lawIds.forEach(id -> {
            String lawId = id.getText();
            String lawIdWithDatePassing = lawId;

            Optional<LocalDate> datePassing = getDatePassingForLaw(lawId);

            if (datePassing.isPresent()) {
                lawIdWithDatePassing += ";" + datePassing.get();
            }

            log.info("Law id with date passing: {}", lawIdWithDatePassing);

            laws.add(lawIdWithDatePassing);

            driver.navigate().back();
        });

        return laws;
    }

    private List<String> getModifiedLaws() {
        List<String> laws = new ArrayList<>();
        List<WebElement> lawIds =
                wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.className("title-id")));

        lawIds.forEach(id -> laws.add(id.getText()));

        return laws;
    }

    //  Selenium's click() method does not work in some cases, so we use JavascriptExecutor
    private void clickOnElement(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }

    private boolean navigateToNextPage() {
        // Check if there is a next page
        WebElement nextPageElement = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("a[aria-label='go to next page']")));
        boolean hasNextPage = nextPageElement != null &&
                !nextPageElement.findElement(By.xpath("..")).getAttribute("class").contains("disabled");

        if (hasNextPage) {
            // Re-locate the element to avoid StaleElementReferenceException
            nextPageElement = wait.until(
                    ExpectedConditions.elementToBeClickable(By.cssSelector("a[aria-label='go to next page']")));
            clickOnElement(nextPageElement);
        }

        return hasNextPage;
    }

    private Optional<LocalDate> getDatePassingForLaw(String lawId) {

        Optional<String> lawPageUrl = Optional.ofNullable(
                        wait.until(ExpectedConditions.presenceOfElementLocated(
                                By.xpath("//div[@class='title-name']/a[contains(@href, '" + lawId + "')]"))))
                .map(a -> a.getAttribute("href"));

        return lawPageUrl.map(this::parseDatePassing);
    }

    public LocalDate parseDatePassing(String lawPageUrl) {
        driver.get(lawPageUrl);
        WebElement datePassingElement =
                wait.until(ExpectedConditions.presenceOfElementLocated(By.className("date-effective-start")));

        return DateUtils.parseAustraliaDate(datePassingElement.getText());
    }

    public Optional<LocalDate> getAffectingLawsFirstDate(List<AffectingLawDetailed> affectingLawDetailed) {
        return affectingLawDetailed.stream()
                .map(AffectingLawDetailed::getAffectingDate) // Assuming getAffectingDate() returns a LocalDate
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder());
    }

}