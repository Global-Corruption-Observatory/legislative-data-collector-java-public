package com.precognox.ceu.legislative_data_collector.usa;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.CaptchaException;
import com.precognox.ceu.legislative_data_collector.utils.JsoupUtils;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.precognox.ceu.legislative_data_collector.usa.Constants.SITE_BASE_URL;
import static java.text.MessageFormat.format;

@Slf4j
@Service
public class PageDownloader {

    @Autowired
    private PageSourceRepository pageSourceRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JsoupUtils jsoupUtils;
    @Autowired
    private UsaCommonFunctions commonFunctions;

    private WebDriver driver;

    public void saveBillPages() {

        try {
            driver = WebDriverUtil.createChromeDriver();
            List<String> periodLinks = commonFunctions.getPeriodsToCollect(driver)
                    .mapToObj(period -> format(Constants.TERM_LIST_PAGE_TEMPLATE, period, 1)).toList();

            periodLinks.stream()
                    .peek(pageLink -> log.info("Visiting: " + pageLink))
                    .flatMap(this::getAllPeriodPageLinks)
                    .forEach(this::getBillLinksFromListPage);
        } finally {
            WebDriverUtil.quitChromeDriver(driver);
        }
    }

    public static int extractPeriodNumber(String periodString) {
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(periodString);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }
        throw new IllegalArgumentException("No number found in the input string");
    }

    private Stream<String> getAllPeriodPageLinks(String periodUrl) {
        int maxPageForPeriod = findMaxPageForPeriod(periodUrl);

        return IntStream.rangeClosed(1, maxPageForPeriod)
                .mapToObj(page -> periodUrl.replace("page=1", "page=" + page));
    }

    private int findMaxPageForPeriod(String termListPageLink) {
        int maxPageNum = 0;
        try {
            Document document = jsoupUtils.getPage(driver, termListPageLink);

            if (document != null) {
                Optional<Element> pageNumElement = Optional.ofNullable(
                                document.getElementsByClass("basic-search-tune-number").first())
                        .map(s -> s.getElementsByClass("pagination").first())
                        .map(s -> s.getElementsByClass("results-number").first());

                if (pageNumElement.isPresent()) {
                    maxPageNum = Integer.parseInt(pageNumElement.get().text().substring(3));
                    log.info("Max page number for current period: " + maxPageNum);
                } else {
                    log.error("Unable to parse page number from {}", termListPageLink);
                }
            } else {
                log.error("Unable to load page {}", termListPageLink);
            }
        } catch (Exception e) {
            log.error("Unable to load page: {}", termListPageLink);
            throw new RuntimeException(e);
        }

        return maxPageNum;
    }

    private void getBillLinksFromListPage(String pageLink) {
        Document document;

        try {
            document = jsoupUtils.getPage(driver, pageLink);
            log.info("Getting bill links from page: " + pageLink);

            if (document != null) {
                Optional<Element> resultList = Optional.ofNullable(
                        document.getElementsByClass("basic-search-results-lists").first());

                resultList.ifPresent(list -> list.getElementsByClass("compact")
                        .stream()
                        .map(li -> li.getElementsByClass("result-heading").first())
                        .map(e -> e.getElementsByTag("a").first())
                        .map(link -> SITE_BASE_URL + link.attr("href"))
                        .forEach(this::savePage));
            } else {
                log.error("Unable to parse bill links from {}", pageLink);
            }
        } catch (CaptchaException e) {
            throw new RuntimeException(e);
        }
    }

    public void savePage(String billLink) {
        Document document;
        String cleanUrl = commonFunctions.removeQueryParametersFromUrl(billLink);

        if (notExistByPageUrl(cleanUrl)) {
            try {
                document = jsoupUtils.getPage(driver, billLink);
            } catch (CaptchaException e) {
                throw new RuntimeException(e);
            }

            if (document != null) {
                String source = document.toString();

                PageSource pageSource = new PageSource();
                pageSource.setCountry(Country.USA);
                pageSource.setPageType(PageTypes.BILL.name());
                pageSource.setPageUrl(billLink);
                pageSource.setCleanUrl(cleanUrl);
                pageSource.setRawSource(source);
                pageSource.setCollectionDate(LocalDate.now());

                saveInNewTransaction(pageSource);
                log.info("Page saved: " + billLink);
            } else {
                log.warn("Unable to load page: {}", billLink);
            }
        } else {
            log.info("Page source already saved {}", billLink);
        }
    }

    private synchronized boolean notExistByPageUrl(String pageUrl) {
        return pageSourceRepository.notExistByCleanUrl(pageUrl);
    }

    private synchronized void saveInNewTransaction(PageSource data) {
        transactionTemplate.execute(status -> pageSourceRepository.save(data));
    }
}
