package com.precognox.ceu.legislative_data_collector.usa.parsers;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.usa.PageDownloader;
import com.precognox.ceu.legislative_data_collector.usa.PageTypes;
import com.precognox.ceu.legislative_data_collector.usa.UsaCommonFunctions;
import com.precognox.ceu.legislative_data_collector.utils.JsoupUtils;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.precognox.ceu.legislative_data_collector.usa.Constants.TERM_LIST_PAGE_TEMPLATE;
import static java.text.MessageFormat.format;

@Slf4j
@Service
public class UsaCommitteesParser {

    @Autowired
    private PageSourceRepository pageSourceRepository;

    @Autowired
    private PageDownloader pageDownloader;

    @Autowired
    private JsoupUtils jsoupUtils;
    private WebDriver driver;

    @Autowired
    private UsaCommonFunctions commonFunctions;

    /**
     * Excluding subcommittees, stores the existing committee names from all periods (this is needed for collecting the
     * committees for individual bills).
     */
    public void storeAllCommitteesPerPeriod() {
        driver = WebDriverUtil.createChromeDriver();

        commonFunctions.getPeriodsToCollect(driver)
                .mapToObj(this::getPageSource)
                .filter(Objects::nonNull)
                .forEach(pageSourceRepository::save);

        WebDriverUtil.quitChromeDriver(driver);
    }

    @Nullable
    private PageSource getPageSource(int period) {
        String url = format(TERM_LIST_PAGE_TEMPLATE, period, 1);

        if (pageSourceRepository.notExistByPageUrlAndType(url, PageTypes.COMMITTEE_LIST.name())) {
            Set<String> commList = getCommittees(url);

            PageSource storedSource = new PageSource();
            storedSource.setRawSource(String.join("; ", commList));
            storedSource.setPageType(PageTypes.COMMITTEE_LIST.name());
            storedSource.setCountry(Country.USA);
            storedSource.setPageUrl(url);
            storedSource.setMetadata("Period: " + period);

            log.info("Collected {} committees for period {}", commList.size(), period);

            return storedSource;
        }

        log.info("Skipping period {} (already exists in DB)", period);

        return null;
    }

    @SneakyThrows
    public Set<String> getCommittees(String periodPageUrl) {
        Document parsedPage = jsoupUtils.getPage(driver, periodPageUrl);
        Element committeesFacetbox = parsedPage.body().getElementById("facetbox_committee");

        if (committeesFacetbox != null) {
            Stream<Element> listItems = Stream.concat(
                    committeesFacetbox.select("li.facetbox-shownrow").stream(),
                    committeesFacetbox.select("li.facetbox-hiddenrow").stream()
            );

            Set<String> committeeNames = listItems
                    .map(li -> li.getElementsByTag("a").first())
                    .filter(Objects::nonNull)
                    .map(element -> element.childNode(0).toString())
                    .map(String::trim)
                    .collect(Collectors.toSet());

            //store subheadings from the subcommittees list here (which mean parent committees)
            Element subcommitteesFacetbox = parsedPage.body().getElementById("facetbox_sub-committee");

            if (subcommitteesFacetbox != null) {
                Set<String> subcommitteeGroupNames = subcommitteesFacetbox.getElementsByTag("h4")
                        .stream()
                        .map(Element::text)
                        .map(String::trim)
                        .collect(Collectors.toSet());

                committeeNames.addAll(subcommitteeGroupNames);
            }

            return committeeNames;
        }

        throw new RuntimeException("Can not get committee list, expected element not found on page: " + periodPageUrl);
    }

}
