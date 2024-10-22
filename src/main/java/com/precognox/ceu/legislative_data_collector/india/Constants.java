package com.precognox.ceu.legislative_data_collector.india;

import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;

public class Constants {

    public static final Duration TIMEOUT = Duration.ofSeconds(5);
    public static final ChromeOptions CHROME_OPTS = new ChromeOptions();
    public static final String START_PAGE = "http://164.100.47.194/Loksabha/Legislation/NewAdvsearch.aspx";
    public static final String USER_HOME_DIR = System.getProperty("user.home");

    static {
        CHROME_OPTS.setImplicitWaitTimeout(TIMEOUT);
    }
}
