package com.precognox.ceu.legislative_data_collector.utils;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class JsoupUtils {

    public static final int MAX_ATTEMPTS = 50;
    public static final int MIN_BACKOFF_MINUTES = 3;
    public static final int MAX_BACKOFF_MINUTES = 30;
    private static final RetryTemplate RETRY_TEMPLATE = buildRetryTemplate();

    private static RetryTemplate buildRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialBackOffPolicy backoffPolicy = new ExponentialBackOffPolicy();
        backoffPolicy.setInitialInterval(MIN_BACKOFF_MINUTES * 60 * 1000);
        backoffPolicy.setMaxInterval(MAX_BACKOFF_MINUTES * 60 * 1000);
        retryTemplate.setBackOffPolicy(backoffPolicy);

        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(HttpStatusException.class, true);
        retryableExceptions.put(SocketTimeoutException.class, true);
        retryableExceptions.put(TimeoutException.class, true);
        retryableExceptions.put(NoSuchElementException.class, true);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(MAX_ATTEMPTS, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }

    public Document getPage(WebDriver driver, String pageUrl) throws CaptchaException {
        try {
            driver.get(pageUrl);

//            Checking USA page type by URL
            if (pageUrl.contains("www.congress.gov") ||
                    pageUrl.contains("www.senate.gov") ||
                    pageUrl.contains("clerk.house.gov")) {
                checkCaptcha(driver, pageUrl);
                RETRY_TEMPLATE.execute(context -> {
                    waitForUsaPageToLoadCorrectly(driver, pageUrl);
                    return null;
                });
            }

            return RETRY_TEMPLATE.execute(context -> Jsoup.parse(driver.getPageSource()));
        } catch (TimeoutException e) {
            log.error("Error loading page: " + pageUrl, e);
            throw new RuntimeException();
        }
    }

    private static void waitForUsaPageToLoadCorrectly(WebDriver driver, String pageUrl) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.of(30, TimeUnit.SECONDS.toChronoUnit()));

        if (pageUrl.contains("www.congress.gov")) {
            WebElement container = driver.findElement(By.id("container"));
            wait.until(ExpectedConditions.visibilityOf(container));
        } else if (pageUrl.contains("www.senate.gov")) {
            wait.until(ExpectedConditions.presenceOfElementLocated(new By.ById("legislative_header")));
        } else if (pageUrl.contains("clerk.house.gov")) {
            wait.until(ExpectedConditions.presenceOfElementLocated(new By.ByClassName("pageDetail")));
        }
    }

    public void checkCaptcha(WebDriver driver, String pageUrl) {
        if (driver.getTitle().equalsIgnoreCase("Just a moment...") ||
                driver.getTitle().equalsIgnoreCase("Misbehaving Content Scraper") ||
                driver.getTitle().contains(" Connection timed out") ||
                driver.getTitle().contains("SSL handshake failed")) {

            log.error("Captcha detected");
            driver.manage().deleteAllCookies();
            driver.navigate().refresh();
            driver.get(pageUrl);
        }
    }
}
