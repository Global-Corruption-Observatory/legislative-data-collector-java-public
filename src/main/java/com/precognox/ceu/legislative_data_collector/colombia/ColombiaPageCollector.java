package com.precognox.ceu.legislative_data_collector.colombia;

import com.precognox.ceu.legislative_data_collector.colombia.constants.PageType;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.exceptions.PageResponseException;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.BatchProcessingUtils;
import com.precognox.ceu.legislative_data_collector.utils.selenium.SeleniumUtils;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * This class saves the main bill and vote pages to the database
 */
@Slf4j
@Service
public class ColombiaPageCollector {
    private static final String BILLPAGE_BASEURL =
            "https://congresovisible.uniandes.edu.co/proyectos-de-ley/?rows=100&page=%s";
    private static final String VOTES_WEBPAGE_BASEURL =
            "https://congresovisible.uniandes.edu.co/votaciones/?page=%d&rows=500";
    private static final boolean REFRESH_DATABASE_NEEDED = false;
    private static final int BATCH_SIZE = 100;

    private final PageSourceRepository pageRepository;

    public ColombiaPageCollector(PageSourceRepository pageRepository) {
        this.pageRepository = pageRepository;
    }

    public void collectPageSources(WebDriver browser) throws DataCollectionException {
        if (REFRESH_DATABASE_NEEDED) {
            log.info("Refreshing database");
            updateDownloadedPageSourceHtmlsInBatches(browser);
        }
        collectVotePages(browser);
        List<String> billUrls = collectBillLinks(browser);
        log.info("Collecting bill page html sources. Found {} bills", billUrls.size());
        List<String> requiredBillUrls = billUrls.stream()
                .distinct()
                .filter(url -> !pageRepository.existsByPageUrl(url + "/"))
                .toList();
        BatchProcessingUtils.completeInBatches(downloadBillPagesTask(browser), requiredBillUrls, BATCH_SIZE);
        log.info("Collecting bill page html sources finished successfully.");
        browser.quit();
    }

    private void updateDownloadedPageSourceHtmlsInBatches(WebDriver browser) {
        log.info("Update downloaded page sources");
        Pageable paging = PageRequest.of(0, BATCH_SIZE, Sort.by("id").ascending());
        Slice<PageSource> pageSlice;
        do {
            pageSlice = pageRepository.findByCountry(Country.COLOMBIA, paging);
            int pageSourceRowNumber = paging.getPageNumber() * BATCH_SIZE;
            log.info("Update from {} to {}", pageSourceRowNumber, pageSourceRowNumber + BATCH_SIZE);
            List<PageSource> sourcesPerPage = pageSlice.getContent().stream()
                    .map(pageSource -> updateDownloadedPageSourceHtml(pageSource, browser))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            pageRepository.saveAll(sourcesPerPage);
            paging = pageSlice.nextPageable();
        } while (pageSlice.hasNext());
        log.info("Downloaded page sources updated successfully.");
    }

    private Optional<PageSource> updateDownloadedPageSourceHtml(PageSource pageSource, WebDriver browser) {
        String url = pageSource.getPageUrl();
        try {
            browser.get(url);
            pageSource.setRawSource(browser.getPageSource());
            pageSource.setCollectionDate(LocalDate.now());
            return Optional.of(pageSource);
        } catch (Exception e) {
            log.error("Error while refreshing page source: {}, {}", browser.getCurrentUrl(), e.getMessage());
            return Optional.empty();
        }
    }

    private void collectVotePages(WebDriver browser) {
        log.info("Collecting vote pages with 500 vote-data / page");
        String votesUrl = String.format(VOTES_WEBPAGE_BASEURL, 1);
        try {
            browser.get(votesUrl);
            WebElement pageOne = browser.findElement(By.cssSelector("html"));
            int pageCount = getNumberOfPages(pageOne);
            if (!pageRepository.existsByPageUrl(browser.getCurrentUrl())) {
                pageRepository.save(SeleniumUtils.downloadColombianPageSource(browser, PageType.VOTES.label));
            }
            log.info("Number of vote pages: {}", pageCount);
            List<PageSource> votePages = IntStream.range(1, pageCount + 1)
                    .mapToObj(pageNumber -> String.format(VOTES_WEBPAGE_BASEURL, pageNumber))
                    .filter(url -> !pageRepository.existByPageTypeAndPageUrl(PageType.VOTES.label, url))
                    .map(url -> getPageByUrl(url, PageType.VOTES.label, browser))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();

            pageRepository.saveAll(votePages);
            log.info("Vote page collection finished");
        } catch (PageResponseException ex) {
            log.error("Page not responded, unable to collect vote pages!");
        } catch (Exception ex) {
            log.error("Error while saving vote pages: {}", ex.getMessage());
            browser.quit();
        }
    }

    private List<String> collectBillLinks(WebDriver browser) throws DataCollectionException {
        log.info("Collecting bill pages with 100 items/page");
        String firstBillPageUrl = String.format(BILLPAGE_BASEURL, 1);
        WebElement billPage;
        try {
            browser.get(firstBillPageUrl);
            billPage = browser.findElement(By.cssSelector("html"));
        } catch (Exception ex) {
            log.error("Bills cannot be collected as the page is not responding");
            return Collections.emptyList();
        }
        int pagesCount = getNumberOfPages(billPage);
        log.info("Total number of bill pages: {}", pagesCount);

        log.info("Collecting links to bills.");
        List<String> billLinks = IntStream.range(1, pagesCount + 1)
                .mapToObj(pageNumber -> String.format(BILLPAGE_BASEURL, pageNumber))
                .flatMap(url -> collectBillLinksFromPage(url, browser).stream())
                .toList();
        log.info("Collecting bill links finished successfully.");
        return billLinks;
    }

    //This reads from actual links to pages; If incorrect url parameters := These do not exist
    private int getNumberOfPages(WebElement page) throws DataCollectionException {
        try {
            WebElement pageNumbersDiv = page.findElement(By.className("CIDPagination_PaginationBar__DrMLD"));
            List<WebElement> pageLinks = pageNumbersDiv.findElements((By.cssSelector("a")));
            if (pageLinks.isEmpty()) {
                throw new DataCollectionException("Paging elements not found for collecting bill page links.");
            }
            WebElement lastPageLink = pageLinks.get(pageLinks.size() - 1);
            return Integer.parseInt(lastPageLink.getText());
        } catch (NoSuchElementException | DataCollectionException ex) {
            log.error("Paging elements not found for collecting bill page links.");
            return getNumberOfPagesFromText(page);
        }
    }

    //This reads from a paging text as a backup; If incorrect url parameters := This reads 0
    private int getNumberOfPagesFromText(WebElement page) throws DataCollectionException {
        try {
            WebElement pageNumbersDiv = page.findElement(By.className("pagination"));
            String paginationText = pageNumbersDiv.getText();
            Matcher pageNumberMather = Pattern.compile("1 de (\\d+)").matcher(paginationText);
            if (pageNumberMather.find()) {
                return Integer.parseInt(pageNumberMather.group(1));
            } else {
                throw new DataCollectionException("Cannot determine the number of pages to be collected");
            }
        } catch (NoSuchElementException ex) {
            throw new DataCollectionException("Cannot determine the number of pages to be collected");
        }
    }

    private List<String> collectBillLinksFromPage(String pageUrl, WebDriver browser) {
        WebElement page;
        try {
            browser.get(pageUrl);
            page = browser.findElement(By.cssSelector("html"));
        } catch (RuntimeException ex) {
            log.error("Bills cannot be collected as the page is not responding");
            return Collections.emptyList();
        }

        return page.findElements(By.xpath("/html/body/div/body/main/div[3]/div[2]/div/div/div[2]/div[2]/div"))
                .stream()
                .filter(this::isRequiredType)
                .map(this::getLinkElement)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(link -> link.getAttribute("href"))
                .toList();
    }

    private boolean isRequiredType(WebElement billElement) {
        Optional<String> billType = billElement.findElements(By.xpath("./div[2]/div[1]/div/ul/li"))
                .stream()
                .filter(element -> {
                    try {
                        String informationType = element.findElement(By.tagName("strong")).getText();
                        return informationType.contains("Tipo de proyecto");
                    } catch (NoSuchElementException ex) {
                        return false;
                    }
                })
                .map(WebElement::getText)
                .findFirst();

        return billType.filter(s -> !s.contains("Acto")).isPresent();
    }

    private Optional<WebElement> getLinkElement(WebElement billElement) {
        Optional<WebElement> link = Optional.empty();
        try {
            return Optional.of(billElement.findElement(By.xpath("./div[1]//a")));
        } catch (NoSuchElementException ex) {
            return link;
        }
    }

    private Consumer<List<String>> downloadBillPagesTask(WebDriver browser) {
        return (List<String> urls) -> {
            List<PageSource> billPages = urls.stream()
                    .map(url -> getPageByUrl(url, PageType.BILL.label, browser))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            pageRepository.saveAll(billPages);
        };
    }

    private Optional<PageSource> getPageByUrl(String pageUrl, String pageType, WebDriver browser) {
        try {
            browser.get(pageUrl);
            PageSource pageSource = SeleniumUtils.downloadColombianPageSource(browser, pageType);
            return Optional.of(pageSource);
        } catch (RuntimeException e) {
            log.error("Error while downloading page source form {}: {}", browser.getCurrentUrl(), e.getMessage());
            return Optional.empty();
        }
    }
}
