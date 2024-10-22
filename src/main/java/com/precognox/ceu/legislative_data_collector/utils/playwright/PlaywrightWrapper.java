package com.precognox.ceu.legislative_data_collector.utils.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import lombok.Getter;
import static com.precognox.ceu.legislative_data_collector.utils.BaseUtils.readParam;

public class PlaywrightWrapper implements AutoCloseable {

    public static final boolean HEADLESS = Boolean.valueOf(readParam("HEADLESS_PLAYWRIGHT", Boolean.TRUE.toString())).booleanValue();
    private final Playwright playwright;
    private final Browser browser;
    @Getter
    private final Page page;

    public PlaywrightWrapper() {
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS).setSlowMo(50));
        this.page = browser.newPage();
    }

    public PlaywrightWrapper(Playwright playwright, Browser browser, Page page) {
        this.playwright = playwright;
        this.browser = browser;
        this.page = page;
    }

    public Response navigate(String url) {
        return this.page.navigate(url);
    }

    public Response get(String url) {
        return navigate(url);
    }

    public Locator locator(String selector) {
        return this.page.locator(selector);
    }

    public Locator find(String selector) {
        return locator(selector).first();
    }

    public String getPageSource() {
        return this.page.content();
    }

    @Override
    public void close() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }


    public String getPageUrl() {
        return this.page.url();
    }
}
