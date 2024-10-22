package com.precognox.ceu.legislative_data_collector.utils.selenium;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
public class WebDriverWaitExtend extends WebDriverWait {

    private static final String JS_JQUERY_DEFINED = "return typeof jQuery != 'undefined';";
    private static final String JS_JQUERY_ACTIVE = "return jQuery.active == 0;";

    public static Function<WebDriver, Boolean> WAIT_FOR_J_QUERY = webDriver -> {
        List<WebElement> body = webDriver.findElements(By.tagName("body"));
        if (body.isEmpty()) {
            return Boolean.FALSE;
        }
        try {
            boolean jQueryDefined = executeBooleanJavascript(webDriver, JS_JQUERY_DEFINED);
            if (!jQueryDefined) {
                return Boolean.TRUE;
            }
            return executeBooleanJavascript(webDriver, JS_JQUERY_ACTIVE);
        } catch (UnhandledAlertException e) {
            log.debug(String.format("waitForJQuery - normal Exception: %s", e.getClass()));
        } catch (Exception e) {
            log.debug("waitForJQuery Error:", e);
        }
        return Boolean.TRUE;
    };

    private boolean ignoreTimeoutException = true;

    public WebDriverWaitExtend(WebDriver driver, int timeoutInSecond) {
        this(driver, Duration.ofSeconds(timeoutInSecond));
    }

    public WebDriverWaitExtend(WebDriver driver, Duration timeout) {
        super(driver, timeout);
        this.ignoreTimeoutException = true;
    }

    public WebDriverWaitExtend(WebDriver driver, Duration timeout, boolean ignoreTimeoutException) {
        super(driver, timeout);
        this.ignoreTimeoutException = ignoreTimeoutException;
    }

    @Override
    public <V> V until(Function<? super WebDriver, V> isTrue) {
        try {
            return super.until(isTrue);
        } catch (TimeoutException timeoutException) {
            if (!this.ignoreTimeoutException) {
                throw timeoutException;
            } else {
                log.warn("Ignored TimeoutException", timeoutException);
            }
        }
        return null;
    }

    private static boolean executeBooleanJavascript(WebDriver driver, String javascript) {
        if(driver instanceof JavascriptExecutor) {
            return (Boolean) ((JavascriptExecutor) driver).executeScript(javascript);
        }
        throw new UnsupportedOperationException();
    }

    public static void waitSec(int sec) {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException ex) {
            log.warn("WaitSec aborted!", ex);
        }
    }
}
