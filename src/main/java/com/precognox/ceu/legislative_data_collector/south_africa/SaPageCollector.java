package com.precognox.ceu.legislative_data_collector.south_africa;


import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.hungary.Utils;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.lang.String.format;

@Slf4j
@Service
@AllArgsConstructor
public class SaPageCollector {

    private final PageSourceRepository pageSourceRepository;
    private final TransactionTemplate transactionTemplate;
    public static final String SOUTH_AFRICA_MAIN_URL = "https://pmg.org.za";
    public static final String SOUTH_AFRICA_GOVERNMENT_PAGE_URL = "https://www.gov.za";

    @Transactional
    public void collectPages() {
        collectBillPages();
        collectGovPages();
        collectOriginatorPages();
    }

    private void collectBillPages() {
        String urlTemplate = SOUTH_AFRICA_MAIN_URL + "/bills/tabled/year/%d/";
        String currentUrl;
        int firstYear = 2006;
        int lastYear = Year.now().getValue();

        for (int currentYear = firstYear; currentYear <= lastYear; currentYear++) {
            currentUrl = format(urlTemplate, currentYear);
            HttpResponse<String> resp = Unirest.get(currentUrl).asString();

            if (resp.isSuccess()) {
                List<String> billUrls = parseBillUrls(resp.getBody());
                billUrls.forEach(billUrl -> downloadPage(billUrl, SaPageType.BILL));
                log.info("Stored {} pages from year {}", billUrls.size(), currentYear);
            } else {
                log.error("{} error returned for URL: {}", resp.getStatus(), currentUrl);
            }
        }
    }

    @NotNull
    private List<String> parseBillUrls(String htmlPage) {
        Document currentParsedPage = Jsoup.parse(htmlPage);

        return currentParsedPage.body()
                .getElementsByClass("bill-stub")
                .stream()
                .map(div -> div.getElementsByTag("a"))
                .map(link -> SOUTH_AFRICA_MAIN_URL + link.attr("href"))
                .filter(url -> !pageSourceRepository.existsByPageUrl(url))
                .toList();
    }

    private void collectGovPages() {
        String currentUrl;
        int maxPage = 100;
        String actUrlTemplate = SOUTH_AFRICA_GOVERNMENT_PAGE_URL +
                "/document?search_query=&field_gcisdoc_doctype=542&field_gcisdoc_subjects=All&start_date=&end_date=&page=%d/";

        for (int currentPage = 0; currentPage <= maxPage; currentPage++) {

            currentUrl = format(actUrlTemplate, currentPage);
            HttpResponse<String> resp = Unirest.get(currentUrl).asString();

            if (resp.isSuccess()) {
                List<String> govUrls = parseGovUrls(resp.getBody());
                govUrls.forEach(govUrl -> downloadPage(govUrl, SaPageType.ACT));
                log.info("Stored {} pages from page {}", govUrls.size(), currentPage);
            } else {
                log.error("{} error returned for URL: {}", resp.getStatus(), currentUrl);
            }
        }
    }

    @NotNull
    private List<String> parseGovUrls(String htmlPage) {
        Document currentParsedPage = Jsoup.parse(htmlPage);
        return currentParsedPage.body()
                .select("td[class*='title']")
                .stream()
                .map(td -> td.getElementsByTag("a"))
                .map(link -> SOUTH_AFRICA_GOVERNMENT_PAGE_URL + link.attr("href"))
                .filter(url -> !pageSourceRepository.existsByPageUrl(url))
                .toList();
    }

    private void collectOriginatorPages() {
        String currentUrl = "https://pmg.org.za/members/";

        HttpResponse<String> resp = Unirest.get(currentUrl).asString();

        if (resp.isSuccess()) {
            List<String> originatorUrls = parseOriginatorUrls(resp.getBody());
            originatorUrls.forEach(originatorUrl -> downloadPage(originatorUrl, SaPageType.ORIGINATOR));
            log.info("Stored links {} for originators", originatorUrls.size());
        } else {
            log.error("{} error returned for URL: {}", resp.getStatus(), currentUrl);
        }
    }

    @NotNull
    private List<String> parseOriginatorUrls(String htmlPage) {
        Document currentParsedPage = Jsoup.parse(htmlPage);

        return currentParsedPage.body()
                .getElementsByClass("content-card")
                .stream()
                .map(div -> div.getElementsByTag("a"))
                .map(url -> url.attr("href"))
                .map(url -> {
                    if (url.contains("/member/")) {
                        url = SOUTH_AFRICA_MAIN_URL + url;
                    }

                    return url;
                })
                .filter(url -> !pageSourceRepository.existsByPageUrl(url))
                .toList();
    }

    public void downloadPage(String pageUrl, SaPageType pageType) {

        if (!pageSourceRepository.existsByPageUrl(pageUrl)) {
            HttpResponse<String> resp = Unirest.get(pageUrl).asString();
            if (resp.isSuccess()) {
                PageSource stored = new PageSource();
                stored.setCountry(Country.SOUTH_AFRICA);
                stored.setPageType(pageType.name());
                stored.setPageUrl(pageUrl);
                stored.setCleanUrl(Utils.cleanUrl(pageUrl));
                stored.setSize(resp.getBody().length());
                stored.setRawSource(resp.getBody());
                stored.setCollectionDate(LocalDate.now());
                if (pageType.name().equals(SaPageType.ORIGINATOR.name())) {
                    Pattern originatorNamePattern = Pattern.compile("https://www.pa.org.za/person/(.*?)/");

                    Optional<String> originatorName = originatorNamePattern
                            .matcher(pageUrl)
                            .results()
                            .map(name -> name.group(1))
                            .findFirst();

                    originatorName.ifPresent(stored::setMetadata);
                }

                log.info("Storing page: {}", pageUrl);
                transactionTemplate.execute(status -> pageSourceRepository.save(stored));
            } else {
                log.error("{} error response for page: {}", resp.getStatus(), pageUrl);
            }
        } else {
            log.info("Page url: " + pageUrl + " already stored");
        }
    }
}