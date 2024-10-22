package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.BillUrl;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.repositories.BillUrlRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static java.text.MessageFormat.format;

@Slf4j
@Service
public class SwedenBillUrlCollector {

    private final BillUrlRepository billUrlRepository;

    private static final int MAX_PAGE = 500;
    private static final String MOTIONS_URL_TEMPLATE = "https://www.riksdagen.se/sv/sok/?doktyp=mot&p={0}";
    private static final String PROPOSITIONS_URL_TEMPLATE = "https://www.riksdagen.se/sv/sok/?doktyp=prop&p={0}";
    private static final String LAWS_URL_TEMPLATE = "https://www.riksdagen.se/sv/sok/?doktyp=sfs&dokstat=g%C3%A4llande+sfs&p={0}";

    //URLs are filtered below by this list
    private static final List<String> FILTERS = List.of("/lag", "/motion/", "/proposition/");

    @Autowired
    public SwedenBillUrlCollector(BillUrlRepository billUrlRepository) {
        this.billUrlRepository = billUrlRepository;
    }

    public void collectLinks() {
        log.error("Downloading bill links for Sweden...");

        downloadBillLinks(LAWS_URL_TEMPLATE);
        downloadBillLinks(MOTIONS_URL_TEMPLATE);
        downloadBillLinks(PROPOSITIONS_URL_TEMPLATE);
    }

    public void downloadBillLinks(String urlTemplate) {
        String currentUrl;

        for (int currentPage = 1; currentPage < MAX_PAGE; currentPage++) {
            currentUrl = format(urlTemplate, currentPage);
            HttpResponse<String> resp = Unirest.get(currentUrl).asString();

            if (resp.isSuccess()) {
                List<BillUrl> newLinks = parseLinks(resp.getBody());
                billUrlRepository.saveAll(newLinks);
                log.info("Stored {} links from page {}", newLinks.size(), currentPage);
            } else {
                log.error("{} error returned for URL: {}", resp.getStatus(), currentUrl);
            }
        }
    }

    @NotNull
    private List<BillUrl> parseLinks(String htmlPage) {
        Document parsed = Jsoup.parse(htmlPage);

        return parsed.body()
                .getElementsByTag("h3")
                .stream()
                .map(h3 -> h3.selectFirst("a"))
                .filter(Objects::nonNull)
                .map(link -> link.attr("href"))
                .filter(link -> FILTERS.stream().anyMatch(link::contains))
                .filter(url -> !billUrlRepository.existsByUrl(url))
                .map(link -> new BillUrl(Country.SWEDEN, link))
                .toList();
    }

}
