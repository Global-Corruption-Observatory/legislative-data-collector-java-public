package com.precognox.ceu.legislative_data_collector.utils.selenium;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.io.File;
import java.util.List;

@Slf4j
public class WebDriverWrapper implements AutoCloseable {

    @Getter
    private WebDriver webDriver;
    @Getter
    private File downloadDir;

    @Getter
    @Setter
    private DevToolsExtend devTools;

    public WebDriverWrapper(WebDriver webDriver) {
        this.webDriver = webDriver;
    }

    public WebDriverWrapper(WebDriver webDriver, File downloadDir) {
        this.webDriver = webDriver;
        this.downloadDir = downloadDir;
    }

    public void get(String url) {
        this.webDriver.get(url);
    }

    public WebElement findElement(String xpath) {
        return this.webDriver.findElement(By.xpath(xpath));
    }

    public WebElement findElement(By locator) { return this.webDriver.findElement(locator);}

    public List<WebElement> findElements(String xpath) {
        return this.webDriver.findElements(By.xpath(xpath));
    }

    public List<WebElement> findElements(By locator) {
        return this.webDriver.findElements(locator);
    }

    public SeElement find(String xpath) {
        return find(By.xpath(xpath));
    }

    public SeElement find(By by) {
        return new SeElement(this.webDriver, by);
    }

    @Override
    public void close() {
        this.webDriver.close();
        this.webDriver.quit();
    }

    public String getText(String xpath) {
        return find(xpath).getText();
    }

    public String getText(SearchContext searchContext, String xpath) {
        try {
            return searchContext.findElement(By.xpath(xpath)).getText().trim();
        } catch (NoSuchElementException ex) {
            log.warn(String.format("Not found: %s;", xpath), ex);
            return "";
        }
    }

    public String getPageSource() {
        return this.webDriver.getPageSource();
    }
}
