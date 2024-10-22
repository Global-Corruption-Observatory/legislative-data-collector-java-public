package com.precognox.ceu.legislative_data_collector.australia;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.hungary.Utils;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class AustraliaWeb2PageCollector {

    private PageSourceRepository pageSourceRepository;
    private AustraliaCommonFunctions australiaCommonFunctions;
    private TransactionTemplate transactionTemplate;

    private final String austarliaWebsite2BaseUrl = "https://www.legislation.gov.au";

    public void collectWeb2PageSources(String url) {
        WebDriver driver = new ChromeDriver();

        try {
            driver.get(austarliaWebsite2BaseUrl + url);

            boolean hasNextPage = true;
            int currentPageIndex = 1;

            while (hasNextPage) {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                WebElement nextPageElement = wait.until(
                        ExpectedConditions.presenceOfElementLocated(By.cssSelector("a[aria-label='go to next page']")));

                // Check if there is a next page
                hasNextPage = nextPageElement != null &&
                        !nextPageElement.findElement(By.xpath("..")).getAttribute("class").contains("disabled");

                if (hasNextPage) {
                    Document document = Jsoup.parse(driver.getPageSource());
                    // Process the current page
                    List<String> urls = collectUrls(document, currentPageIndex);

                    urls.forEach(pageUrl -> savePageSource(pageUrl,
                            AustraliaPageType.ACT.toString()));

                    // Re-locate the element to avoid StaleElementReferenceException
                    nextPageElement = wait.until(
                            ExpectedConditions.elementToBeClickable(By.cssSelector("a[aria-label='go to next page']")));
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", nextPageElement);

                    currentPageIndex++;
                }
            }
        } finally {
            driver.quit();
        }
    }

    private List<String> collectUrls(Document document, int currentPageIndex) {
        List<String> urls = new ArrayList<>();

        Element datatableScroll = document.selectFirst(".datatable-scroll");
        if (datatableScroll != null) {
            datatableScroll.select("a[href]").stream()
                    .map(a -> a.attr("href"))
                    .filter(url -> !url.contains("downloads"))
                    .map(url -> austarliaWebsite2BaseUrl + url)
                    .filter(url -> !pageSourceRepository.existsByPageUrl(url))
                    .forEach(urls::add);
        }

        log.info("Collected {} urls from page {}", urls.size(), currentPageIndex);
        return urls;
    }


    public PageSource savePageSource(String url, String metadata) {
        HttpResponse<String> response = Unirest.get(url).asString();
        if (response.isSuccess()) {
            PageSource pageSource = new PageSource();
            String responseBody = response.getBody();

            pageSource.setPageUrl(url);
            pageSource.setCleanUrl(Utils.cleanUrl(url));
            pageSource.setPageType(metadata);
            pageSource.setRawSource(responseBody);
            pageSource.setCountry(Country.AUSTRALIA);
            pageSource.setCollectionDate(LocalDate.now());

            if (metadata.equals(AustraliaPageType.ACT.toString())) {
                pageSource.setMetadata(collectMetadata(responseBody, url));
            }

            log.info("Storing page: {}", url);
            transactionTemplate.execute(status -> pageSourceRepository.save(pageSource));
            return pageSource;
        } else {
            log.error("Failed to load page: " + url);
        }
        return null;
    }

    private String collectMetadata(String responseBody, String url) {
        Document document = Jsoup.parse(responseBody);

        String lawTitle = document.body().getElementsByClass("legislation-title-header").text();
        String datePassing = document.selectFirst("span.date-effective-start").text();

        JSONObject metadataJson = new JSONObject();
        metadataJson.put("lawTitle", lawTitle);
        metadataJson.put("datePassing", DateUtils.parseAustraliaDate(datePassing));
        getActNumber(document, url).ifPresent(number -> metadataJson.put("actNumber", number));

        return metadataJson.toString();
    }

    private Optional<String> getActNumber(Document document, String url) {
        String lawId = australiaCommonFunctions.getLawId(url).get();
        Optional<String> actNumber = Optional.empty();

        Optional<Element> jsonElement = Optional.ofNullable(document.getElementById("ng-state"));

        String objectName = "https://api.prod.legislation.gov.au/v1/titles('" + lawId +
                "')?$expand=textApplies,administeringDepartments";


        if (jsonElement.isPresent()) {
            String jsonContent = jsonElement.get().data();
            JSONObject jsonObject = new JSONObject(jsonContent);

            JSONObject targetData = new JSONObject(jsonObject.get(objectName)).getJSONObject("element");

            actNumber = Optional.of(targetData.getString("number") + " of " + targetData.getString("year"));

            log.info("Act number found: {}", actNumber.get());
        }

        return actNumber;
    }
}
