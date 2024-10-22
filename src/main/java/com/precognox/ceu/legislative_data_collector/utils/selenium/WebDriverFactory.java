package com.precognox.ceu.legislative_data_collector.utils.selenium;


import com.precognox.ceu.legislative_data_collector.utils.RetryUtils;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.springframework.retry.support.RetryTemplate;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@NoArgsConstructor
public class WebDriverFactory extends BasePooledObjectFactory<WebDriverWrapper> {

    private static int count = 0;

    public static final String DOWNLOAD_DIR = "downloads";

    private static final Object LOCK = new Object();


    @Override
    public WebDriverWrapper create() {
        String browserDownloadDir;
        synchronized (LOCK) {
            count++;
            browserDownloadDir = String.format("browser%d", count);
        }
        String downloadPath = String.format("%s\\%s\\%s", System.getProperty("user.dir"), DOWNLOAD_DIR, browserDownloadDir);
        File downloadDirectory = new File(downloadPath);
        try {
            waitForDownloadDirectoryToBeCreated(downloadDirectory);
        } catch (TimeoutException ex) {
            throw new RuntimeException("Unable to create download directory for browser. Shutting down!");
        }
        downloadDirectory.deleteOnExit();
        AtomicReference<WebDriver> driver = new AtomicReference<>();
        retryTemplate().execute(context -> {
            if (context.getRetryCount() > 1) {
                log.error("Session not created for chrome. Retrying!");
            }
            driver.set(new WebDriverUtil().createChromeWebDriver(downloadPath));
            return null;
        });
        WebDriverWrapper browser = new WebDriverWrapper(driver.get(), downloadDirectory);
        DevToolsExtend devTools = new DevToolsExtend(browser);
        devTools.setRecordingDataFunction(DevToolsExtend.HEADER_ONLY_RECORDING_FUNCTION);
        devTools.setRecordingCondition(DevToolsExtend.DOCUMENT_RECORDING_PREDICATE);
        browser.setDevTools(devTools);
        log.info("Driver created successfully");
        return browser;
    }

    @Override
    public PooledObject<WebDriverWrapper> wrap(WebDriverWrapper obj) {
        return new DefaultPooledObject<>(obj);
    }

    @Override
    public void destroyObject(PooledObject<WebDriverWrapper> obj) {
        obj.getObject().getWebDriver().quit();
    }

    private void waitForDownloadDirectoryToBeCreated(File downloadDir) {
        Wait<File> wait = new FluentWait<>(downloadDir)
                .withTimeout(Duration.ofSeconds(30))
                .pollingEvery(Duration.ofSeconds(1));

        wait.until(downloadDirectory -> {
            try {
                log.info("Trying to create download directory {}", downloadDirectory.getName());

                if (downloadDirectory.exists()) {
                    return true;
                }

                if (downloadDirectory.mkdirs()) {
                    return true;
                }

                log.warn("Unable to create download directory. Retrying...");
            } catch (Exception ex) {
            }

            return false;
        });
    }

    private static RetryTemplate retryTemplate() {
        return RetryUtils.getRetryTemplate(2000, 3, List.of(new SessionNotCreatedException("Filler")));
    }

    public static void flushDownloadDir() {
        String downloadPath = String.format("%s\\%s", System.getProperty("user.dir"), DOWNLOAD_DIR);
        File downloadDir = new File(downloadPath);
        try {
            FileUtils.deleteDirectory(downloadDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}