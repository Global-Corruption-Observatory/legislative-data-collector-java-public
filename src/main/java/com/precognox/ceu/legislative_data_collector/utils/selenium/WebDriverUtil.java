package com.precognox.ceu.legislative_data_collector.utils.selenium;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ch.qos.logback.core.util.EnvUtil.isWindows;

@Slf4j
public class WebDriverUtil {

    // https://googlechromelabs.github.io/chrome-for-testing/
    private final static String DEFAULT_CHROME_DRIVER_PATH = "/usr/bin//chromedriver";
    boolean isHeadless = false;

    @Getter
    WebDriver driver;
    public static boolean ENGLISH_CHROME = true;

    public static ChromeDriver createChromeDriver() {
        ChromeOptions chromeOptions = new ChromeOptions();
        return new ChromeDriver(chromeOptions);
    }

    public static void quitChromeDriver(WebDriver driver) {
        if (driver != null) {
            driver.quit();
        }
    }

    public WebDriver getHtmlUnitDriver(String downloadFilepath) {
        HtmlUnitDriver htmlUnitDriver = new HtmlUnitDriver();
        Logger.getLogger("com.gargoylesoftware").setLevel(Level.OFF);
        Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        return htmlUnitDriver;
    }

    public WebDriver createChromeWebDriver() {
        return createChromeWebDriver("", null);
    }

    public WebDriver createChromeWebDriver(String downloadFilepath) {
        return createChromeWebDriver(downloadFilepath, null);
    }

    public WebDriver createChromeWebDriver(String downloadFilepath, String chromeProfile) {
        String systemPropName = "webdriver.chrome.driver";
        String driverName = "chromedriver" + (isWindows() ? ".exe" : "");
        this.setDriver(systemPropName, driverName);
        ChromeOptions options = getChromeOptions(downloadFilepath, chromeProfile);
        ChromeDriverService driverService = ChromeDriverService.createDefaultService();
        ChromeDriver driver = new ChromeDriver(driverService, options);

        String command = "{\"cmd\":\"Page.setDownloadBehavior\",\"params\":{\"downloadPath\":\"" + (downloadFilepath != null ? downloadFilepath.replace("\\", "\\\\") : "") + "\",\"behavior\":\"allow\"}}";

        try {
            HttpClient httpClient = HttpClientBuilder.create().build();
            String u = driverService.getUrl().toString() + "/session/" + driver.getSessionId() + "/chromium/send_command";
            HttpPost request = new HttpPost(u);
            request.addHeader("Content-type", "application/json");
            request.setEntity(new StringEntity(command));
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200 && statusCode != 120) {
                throw new Exception(String.format("Response status code=%s; command='%s';", response.getStatusLine().getStatusCode(), command));
            }
        } catch (Exception ex) {
            log.error("Failed to send Chrome command!", ex);
        }
        Logger logger = Logger.getLogger(RemoteWebDriver.class.getName());
        logger.setLevel(Level.OFF);

        for (Handler handler : Logger.getLogger("").getHandlers()) {
            handler.setLevel(Level.OFF);
        }
        return driver;
    }

    private void setDriver(String systemPropName, String driverName) {
        String driverPath = System.getProperty(systemPropName);
        if (driverPath == null || driverPath.isEmpty()) {
            driverPath = System.getenv().getOrDefault("user.dir", System.getProperty("user.dir"));
            if (!driverPath.endsWith(driverName)) {
                driverPath += "/" + driverName;
            }
            System.setProperty(systemPropName, driverPath);
        }
        if (!(new File(driverPath)).exists()) {
            System.setProperty(systemPropName, DEFAULT_CHROME_DRIVER_PATH);
            if (!(new File(DEFAULT_CHROME_DRIVER_PATH)).exists()) {
                log.error("Missing " + driverName + " binary! Path:" + DEFAULT_CHROME_DRIVER_PATH);
            } else {
                log.info("Set " + driverName + " binary! Path:" + DEFAULT_CHROME_DRIVER_PATH);
            }
        } else {
            log.info("Set " + driverName + " binary! Path:" + driverPath);
        }
    }

    public WebDriver createRemoteWebDriver(String downloadFilepath, String chromeProfile) {
        driver = getRemoteWebDriver(downloadFilepath, chromeProfile);
        return driver;
    }

    public DesiredCapabilities getChromeCapabilities(String downloadFilepath, String chromeProfile) {
        DesiredCapabilities capabilities = new DesiredCapabilities();
        ChromeOptions options = getChromeOptions(downloadFilepath, chromeProfile);
        capabilities.setCapability("goog:chromeOptions", options);

        Dictionary<String, Object> dic = new Hashtable();
        if (downloadFilepath != null) {
            dic.put("savefile.default_directory", downloadFilepath);
        }

        dic.put("savefile.type", 1);
        capabilities.setCapability("chrome.prefs", dic);
        return capabilities;
    }

    private ChromeOptions getChromeOptions(String downloadFilepath, String chromeProfile) {
        // CHROME
        HashMap<String, Object> prefs = new HashMap<>();
        prefs.put("download.prompt_for_download", "false");
        prefs.put("download.default_directory", downloadFilepath);

        prefs.put("profile.default_content_setting_values.notifications", 1);
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("password_manager_enabled", false);
        prefs.put("safebrowsing.enabled", true);
        prefs.put("profile.default_content_settings.popups", 0);
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.directory_upgrade", true);
        prefs.put("plugins.always_open_pdf_externally", true);
        prefs.put("plugins.plugins_disabled", new String[]{"Adobe Flash Player", "Chrome PDF Viewer", "Foxit Reader"});

        ChromeOptions options = new ChromeOptions();
        options.setBinary("/usr/bin/chromium-browser");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("'--ignore-certificate-errors");
        options.addArguments("ignore-certificate-errors");
        options.setExperimentalOption("prefs", prefs);
        if (isHeadless) {
            options.addArguments("--headless");
            options.addArguments("--headless=new");
        }
        options.addArguments("start-maximized");
        options.addArguments("disable_infobars");
        options.addArguments("--disable-gpu");
        options.setCapability(CapabilityType.BROWSER_NAME, "chrome");
        options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
        options.addArguments("--log-level=3");
        if (StringUtils.isNoneBlank(chromeProfile)) {
            options.addArguments("user-data-dir=" + chromeProfile);
        }
        if (ENGLISH_CHROME) {
            options.addArguments("lang=en-GB");
        }
        return options;
    }


    public WebDriver getRemoteWebDriver(String downloadFilepath, String chromeProfile) {
        try {
            DesiredCapabilities capabilities = getChromeCapabilities(downloadFilepath, chromeProfile);
            RemoteWebDriver remoteWebDriver = new RemoteWebDriver(new URL("http://localhost:4444/wd/hub"), capabilities);

            return remoteWebDriver;
        } catch (MalformedURLException e) {
            log.error("Failed to read URL!", e);
            return null;
        }
    }
}
