package com.precognox.ceu.legislative_data_collector.india;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.precognox.ceu.legislative_data_collector.india.Constants.*;
import static java.util.Collections.singletonMap;

@Slf4j
@Service
public class IndiaPageSourceDownloader {

    private final PageSourceRepository pageSourceRepository;

    @Autowired
    public IndiaPageSourceDownloader(PageSourceRepository pageSourceRepository) {
        this.pageSourceRepository = pageSourceRepository;
    }

    public void downloadPages() {
        ChromeDriver br = new ChromeDriver(CHROME_OPTS);

        try {
            br.get(START_PAGE);

            br.findElement(By.cssSelector("input[value='both']")).click();
            br.findElement(By.cssSelector("input[value='All']")).click();
            br.findElement(By.cssSelector("input[value='6']")).click();
            br.findElement(By.cssSelector("input[value='Submit']")).click();

            int currentPage = 1;

            do {
                if (pageSourceRepository.existsByPageTypeAndMetadata(PageType.BILL_LIST.name(), "Page " + currentPage)) {
                    log.info("Skipping already stored page: {}", currentPage);
                } else {
                    log.info("Collecting page {}", currentPage);

                    PageSource storedSource = new PageSource();
                    storedSource.setCountry(Country.INDIA);
                    storedSource.setPageType(PageType.BILL_LIST.name());
                    storedSource.setPageUrl(START_PAGE);
                    storedSource.setRawSource(br.getPageSource());
                    storedSource.setMetadata("Page " + currentPage);
                    pageSourceRepository.save(storedSource);
                }

                String nextPgLinkXpath = currentPage % 10 != 0
                        ? String.format("//a[text() = '%s']", currentPage + 1)
                        : "//a[text() = 'Next']";

                WebElement nextBtn = br.findElement(By.xpath(nextPgLinkXpath));
                nextBtn.click();
                new WebDriverWait(br, TIMEOUT).until(ExpectedConditions.stalenessOf(nextBtn));

                currentPage++;
            } while (currentPage < 305);
        } catch (NoSuchElementException e) {
            log.info("Can not find next page link, last page reached?");
        } finally {
            br.close();
        }
    }

    public void downloadPagesWithCategoryFilter() {
        try {
            Path tempDirPath = Path.of(USER_HOME_DIR, UUID.randomUUID().toString());
            Files.createDirectory(tempDirPath);

            CHROME_OPTS.setExperimentalOption(
                    "prefs", singletonMap("download.default_directory", tempDirPath.toString())
            );

            ChromeDriver br = new ChromeDriver(CHROME_OPTS);

            try {
                br.get(START_PAGE);
                br.findElement(By.cssSelector("input[value='both']")).click();
                br.findElement(By.cssSelector("input[value='All']")).click();
                br.findElement(By.cssSelector("input[value='6']")).click();
                br.findElement(By.cssSelector("input[value='Submit']")).click();

                Select categorySelector = new Select(br.findElement(By.id("ContentPlaceHolder1_ddlCategory")));

                List<String> categories = categorySelector.getOptions()
                        .stream()
                        .map(WebElement::getText)
                        .toList();

                //skip first option
                for (int i = 1; i < categories.size(); i++) {
                    String currentCategory = categories.get(i); //needed for lambda below
                    log.info("Downloading category: {}", currentCategory);

                    categorySelector.selectByIndex(i);
                    br.findElement(By.cssSelector("input[value='Submit']")).click();

                    Select formatSelect = new Select(br.findElement(By.id("ContentPlaceHolder1_ddlfile")));
                    formatSelect.selectByVisibleText("Html");
                    br.findElement(By.className("down_icon")).click();

                    waitForDownload(tempDirPath);

                    try (Stream<Path> fileStream = Files.list(tempDirPath)) {
                        Optional<Path> file = fileStream.findFirst();

                        file.map(this::readFile)
                                .map(source -> buildPageSourceEntity(currentCategory, source))
                                .ifPresent(pageSourceRepository::save);

                        Files.delete(file.get());
                    }

                    categorySelector = new Select(br.findElement(By.id("ContentPlaceHolder1_ddlCategory")));
                }
            } finally {
                br.close();
            }

            Files.delete(tempDirPath);
        } catch (IOException e) {
            log.error("Error when saving bill list export", e);
        }
    }

    private void waitForDownload(Path downloadDir) {
        log.info("Waiting for download in directory: {}", downloadDir.toString());

        Wait<Path> wait = new FluentWait<>(downloadDir)
                .withTimeout(Duration.ofMinutes(10))
                .pollingEvery(Duration.ofSeconds(5));

        wait.until(dirPath -> {
            File[] list = dirPath.toFile().listFiles();

            return Arrays.stream(list).anyMatch(file -> file.getName().endsWith(".htm"));
        });

        log.info("Wait completed");
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private PageSource buildPageSourceEntity(String currentCategory, String source) {
        PageSource storedSource = new PageSource();
        storedSource.setRawSource(source);
        storedSource.setPageUrl(START_PAGE);
        storedSource.setPageType(PageType.BILL_CATEGORY_EXPORT.name());
        storedSource.setMetadata(currentCategory);
        storedSource.setCountry(Country.INDIA);

        return storedSource;
    }

}
