package com.precognox.ceu.legislative_data_collector.hungary;

import com.google.common.collect.Streams;
import com.jauntium.Browser;
import com.jauntium.Document;
import com.jauntium.Element;
import com.jauntium.Elements;
import com.jauntium.Form;
import com.jauntium.NotFound;
import com.precognox.ceu.legislative_data_collector.common.BrowserPool;
import com.precognox.ceu.legislative_data_collector.entities.BillUrl;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.repositories.BillUrlRepository;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Collects the links of bills to download in the next step. Results are saved to the 'bill_links' database table.
 */
@Slf4j
@Service
public class BillUrlCollector {

    private final BrowserPool browserPool;
    private final BillUrlRepository billUrlRepository;

    private static final String NUMBER_INTERVAL_REGEX = "\\d+? - \\d+?";

    @Autowired
    public BillUrlCollector(BrowserPool browserPool, BillUrlRepository billUrlRepository) {
        this.browserPool = browserPool;
        this.billUrlRepository = billUrlRepository;
    }

    public void collectLinks() {
        List<String> termPageLinks = getPeriodLinks();

        termPageLinks.forEach(termPageLink -> {
            try {
                runCollection(termPageLink);
            } catch (Exception e) {
                log.error("Collection aborted for term", e);
            }
        });

        log.info("Collection finished");
    }

    private List<String> getPeriodLinks() {
        String termListPage = "https://www.parlament.hu/web/guest/iromanyok-elozo-ciklusbeli-adatai";
        Browser browser = browserPool.get();
        Document page = browser.visit(termListPage);
        Element termsTable = browser.wrap(page.findElement(By.className("table-bordered")));

        Browser newWindow = browserPool.get();

        List<String> termSearchPageLinks = termsTable.findElements(By.tagName("tr"))
                .stream()
                .skip(2)
                .map(row -> row.findElements(By.tagName("td")).get(1))
                .map(cell -> cell.findElement(By.tagName("a")))
                .map(link -> link.getAttribute("href"))
                .map(newWindow::visit)
                .map(termOptionsPage -> tryFind(termOptionsPage, "<div id=\"main-content\">"))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(container -> tryFind(container, "<a>Irományok egyszerűsített lekérdezése"))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(link -> link.getAttribute("href"))
                .toList();

        browserPool.returnToPool(newWindow);
        browserPool.returnToPool(browser);

        return termSearchPageLinks;
    }

    private Optional<Element> tryFind(Element container, String query) {
        try {
            return Optional.of(container.findFirst(query));
        } catch (NotFound e) {
            log.error("Element not found by query: {}", query);
        }

        return Optional.empty();
    }

    private void runCollection(String termSearchPage) {
        //requirement:
        //  - libs (jaunt, jauntium)
        //  - sudo apt-get install chromium-driver

        Browser browser = browserPool.get();
        Document searchPage = browser.visit(termSearchPage);

        Form form;

        try {
            form = searchPage.getForm(1);
        } catch (NotFound e) {
            throw new RuntimeException(e);
        }

        WebElement firstSelect = form.element.findElement(By.tagName("select"));

        List<String> availableYears = firstSelect.findElements(By.tagName("option"))
                .stream()
                .map(e -> e.getAttribute("value"))
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .toList();

        for (String yearStr : availableYears) {
            log.info("Collecting year {}", yearStr);

            try {
                //reload page to avoid stale element ref. error
                searchPage = browser.visit(termSearchPage);
                form = searchPage.getForm(1);

                searchPage.findFirst("<option>" + yearStr).setAttribute("selected", "true");
                searchPage.findFirst("<option>törvényjavaslat").setAttribute("selected", "true");

                Document yearPage = form.submit();

                Element firstTable = browser.wrap(yearPage.findElement(By.className("table-bordered")));
                String tableTitle = firstTable.findElement(By.tagName("th")).getText();

                Stream<String> billLinks = Stream.empty();

                if (tableTitle.contains("Iromány intervallumok")) {
                    Browser newWindow = browserPool.get();

                    billLinks = firstTable.findElements(By.tagName("a"))
                            .stream()
                            .map(browser::wrap)
                            .filter(linkElement -> linkElement.innerHTML().replace("&nbsp;", " ").matches(NUMBER_INTERVAL_REGEX))
                            .map(linkElement -> linkElement.getAttribute("href"))
                            .map(newWindow::visit)
                            .flatMap(this::getBillLinks);

                    browserPool.returnToPool(newWindow);
                } else if (tableTitle.contains("Iromány adatai")) {
                    billLinks = getBillLinks(yearPage);
                }

                storeLinks(billLinks);
            } catch (NotFound | NoSuchElementException e) {
                log.error("Expected element missing on page: " + termSearchPage, e);
            }
        }

        browserPool.returnToPool(browser);
    }

    private Stream<String> getBillLinks(Document billListPage) {
        Elements iromanyDivs = billListPage.findEach("<div class=egy-iromany>");

        return Streams.stream(iromanyDivs.iterator())
                .map(iromanyDiv -> iromanyDiv.findElement(By.tagName("a")).getAttribute("href"));
    }

    private void storeLinks(Stream<String> billUrls) {
        long savedCount = billUrls
                .filter(link -> !billUrlRepository.existsByCleanUrl(Utils.cleanUrl(link)))
                .map(link -> new BillUrl(Country.HUNGARY, link))
                .peek(billUrlRepository::save)
                .count();

        if (savedCount > 0) {
            log.info("Saved {} links", savedCount);
        }
    }

}
