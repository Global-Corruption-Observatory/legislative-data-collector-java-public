package com.precognox.ceu.legislative_data_collector.utils.selenium;

import com.precognox.ceu.legislative_data_collector.common.Constants;
import com.precognox.ceu.legislative_data_collector.common.exception.CaptchaException;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.exceptions.PageResponseException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.precognox.ceu.legislative_data_collector.common.Constants.PROXY_LIST;

@Slf4j
@Service
public class SeleniumUtils {

    public static final String DEFAULT_USER_HOME = System.getProperty("user.home");
    public static final String DOWNLOAD_FOR_COLOMBIA = "DownloadForColombia";

    public static String getText(WebElement element) {
        return element.getAttribute("textContent").trim();
    }

    public static void checkCaptcha(ChromeDriver browser) {
        List<WebElement> captchaHeaders = browser.findElements(By.cssSelector("h2#challenge-running"));

        if (!captchaHeaders.isEmpty()) {
            log.error("Captcha encountered on page: {}", browser.getCurrentUrl());
            throw new CaptchaException();
        }
    }

    public static ChromeDriver getChromeBrowserForColombia() {
            Path tempDirPath = Path.of(DEFAULT_USER_HOME, DOWNLOAD_FOR_COLOMBIA);
            Map<String, Object> prefs = Map.of("download.default_directory", tempDirPath.toString());
            ChromeOptions chromeOptions = new ChromeOptions();
            chromeOptions.setExperimentalOption("prefs", prefs);
            chromeOptions.setBinary(Constants.CHROME_LOCATION);
            chromeOptions.addArguments("--unsafely-treat-insecure-origin-as-secure=http://svrpubindc.imprenta.gov.co/senado/");
            return new ChromeDriver(chromeOptions);
    }

    public static ChromeDriver getBrowserWithRandomProxy() {
        ChromeOptions options = new ChromeOptions();
        options.setImplicitWaitTimeout(Duration.ofSeconds(5));
        options.addArguments("--remote-allow-origins=*");
        options.setBinary(Constants.CHROME_LOCATION);

        if (!PROXY_LIST.isEmpty()) {
            String randomProxy = PROXY_LIST.get(Double.valueOf(Math.random() * PROXY_LIST.size()).intValue());

            Proxy proxy = new Proxy();
            proxy.setHttpProxy(randomProxy);
            options.setProxy(proxy);

            log.info("Set random proxy to: {}", randomProxy);
        }

        return new ChromeDriver(options);
    }

    public static boolean isCaptchaOrError(WebDriver browser) {
        if (browser.getTitle().toLowerCase().contains("error")) {
            return true;
        }

        List<WebElement> errorDiv = browser.findElements(By.cssSelector("div#main-message"));
        List<WebElement> captchas = browser.findElements(By.cssSelector("img[src='/captcha.gif']"));

        return !errorDiv.isEmpty() || !captchas.isEmpty();
    }

    //from chile branch
    private static final List<WebDriverWrapper> IN_USE_BROWSERS = new ArrayList<>();
    private static final int RETRY_LIMIT = 3;
    @Setter
    private static int RETRY_WAIT = 30; //in seconds
    private static final ObjectPool<WebDriverWrapper> browserPool;

    static {
        GenericObjectPoolConfig<WebDriverWrapper> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(8);
        config.setMaxIdle(8);
        browserPool = new GenericObjectPool<>(new WebDriverFactory(), config);
    }

    private static void navigateAndPageResponseCheck(
            WebDriver browser, String url, DevToolsExtend devTools) throws PageResponseException {
        retryTemplate().execute(context -> {
            try {
                if (context.getRetryCount() > 0) {
                    log.error("Page not responded as expected. Retrying!");
                }
                devTools.startRecording();
                browser.get(url);
                if (devTools.getRecordedData().size() == 0) {
                    throw new PageResponseException("No HTML document received");
                }
                int responseStatus = devTools.getRecordedData().get(0).getResponse().getStatus();
                if (responseStatus != 200) {
                    throw new PageResponseException(String.format("Wrong response status %d", responseStatus));
                }
            } catch (TimeoutException ex) {
                throw new PageResponseException("Page not responding");
            }
            return null;
        });
    }

    private static RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(RETRY_WAIT * 1000);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(PageResponseException.class, true);
        retryableExceptions.put(WebDriverException.class, true);
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(RETRY_LIMIT, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);


        return retryTemplate;
    }

    public static WebDriverWrapper safeNavigate(
            WebDriverWrapper browser, String url) throws PageResponseException {
        try {
            navigateAndPageResponseCheck(browser.getWebDriver(), url, browser.getDevTools());
            return browser;
        } catch (PageResponseException ex) {
            log.error("Page unreachable, caused by: {}", ex.getMessage());
            returnBrowser(browser);
            throw new PageResponseException(ex.getMessage());
        } catch (NoSuchWindowException ex) {
            try {
                removeFromInUseBrowsers(browser);
                browserPool.invalidateObject(browser);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            log.error("WebDriver Exception [{}]", ex.getMessage());
            browser = getBrowser();
            //It happens sometimes if the program has been running for a while. It supposed to be thrown when switching tabs, but all browsers only use one tab.
            //So currently to deal with it the browser gets delete and a new browser continues where it has left of.
            log.error("NoSuchWindowException (?probably chrome memory is full?). Created new instance of ChromeDriver");
            return safeNavigate(browser, url);
        } catch (WebDriverException ex) {
            log.error("WebDriver Exception [{}]", ex.getMessage());
            returnBrowser(browser);
            throw new PageResponseException("Connection timed out");
        }
    }

    public static WebDriverWrapper getBrowser() {
        try {
            WebDriverWrapper browser = browserPool.borrowObject();
            addToInUseBrowsers(browser);
            return browser;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void returnBrowser(WebDriverWrapper browser) {
        if (Objects.nonNull(browser)) {
            try {
                browserPool.returnObject(browser);
                removeFromInUseBrowsers(browser);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static synchronized void closeAllBrowser() {
        try {
            browserPool.clear();
            log.info("BROWSERS IN USE " + IN_USE_BROWSERS.size());
            log.info("BROWSERS IN USE BEFORE RETURN " + browserPool.getNumActive());
            for (WebDriverWrapper browser : IN_USE_BROWSERS) {
                browserPool.returnObject(browser);
            }
        } catch (Exception ex) {
            log.error("Error while closing browsers: " + ex);
        } finally {
            log.info("BROWSERS IN USE BEFORE CLOSE " + browserPool.getNumActive());
            try {
                browserPool.close();
            } catch (Exception ex) {
                log.error("Error while closing browsers: {}", ex.toString());
            }
        }
    }

    public static PageSource downloadColombianPageSource(WebDriver browser, String type) {
        PageSource source = new PageSource();
        source.setCountry(Country.COLOMBIA);
        source.setPageType(type);
        source.setCollectionDate(LocalDate.now());
        source.setPageUrl(browser.getCurrentUrl());
        source.setRawSource(browser.getPageSource());

        return source;
    }

    private static synchronized void addToInUseBrowsers(WebDriverWrapper browser) {
        IN_USE_BROWSERS.add(browser);
    }

    private static synchronized void removeFromInUseBrowsers(WebDriverWrapper browser) {
        IN_USE_BROWSERS.remove(browser);
    }
}
