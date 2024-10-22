package com.precognox.ceu.legislative_data_collector.utils.selenium;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

@Slf4j
public class SeElement {

    private WebDriver webDriver;
    private By by;

    public SeElement(WebDriver webDriver, String xpath) {
        this(webDriver, By.xpath(xpath));
    }
    public SeElement(WebDriver webDriver, By by) {
        this.webDriver = webDriver;
        this.by = by;
    }

    public boolean isVisible() {
//        waitForVisible();
        return !webDriver.findElements(by).isEmpty();
    }

    public SeElement waitForVisible() {
        try {
            new WebDriverWaitExtend(webDriver, Duration.ofSeconds(20))
                    .until(webDriver ->
                    ExpectedConditions.visibilityOfElementLocated(by).apply(webDriver));
        } catch (TimeoutException timeoutException) {
            log.trace(String.format("Timeout in: %s;", by));
        } catch (NoSuchElementException ex) {
            log.warn(String.format("Not found: %s;", by), ex);
        }
        return this;
    }

    public void click() {
        waitForVisible();

        WebElement button = webDriver.findElement(by);
        button.click();
    }

    public String getText() {
        List<WebElement> elements = webDriver.findElements(by);
        for (WebElement element : elements) {
            return element.getText().trim();
        }
        return "";
    }

//    public String getText() {
//        try {
//            waitForVisible();
//            WebElement webElement = webDriver.findElement(by);
//            if (webElement != null) {
//                String trim = webElement.getText().trim();
//                return trim;
//            } else {
//                return "";
//            }
//        } catch (NoSuchElementException ex) {
//            log.warn(String.format("Not found: %s;", by), ex);
//            return "";
//        }
//    }

    public String getAttribute(String attribute) {
        try {
//            waitForVisible();
            if (!isVisible()) {
                return "";
            }
            WebElement webElement = webDriver.findElement(by);
            if (webElement != null) {
                String trim = webElement.getAttribute(attribute);
                return trim;
            } else {
                return "";
            }
        } catch (NoSuchElementException ex) {
//            log.warn(String.format("Not found: %s;", by), ex);
            return "";
        }
    }
}
