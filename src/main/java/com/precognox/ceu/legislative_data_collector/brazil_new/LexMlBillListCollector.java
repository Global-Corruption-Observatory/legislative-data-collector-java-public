package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.entities.BillUrl;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.BillUrlRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;

/**
 * Collects the bill LINKS only from the LexML site, after applying the filtering rules in the annotation. Stores results as BillUrl entities (in the bill_links table). Additionally, it stores the bill list pages for caching as PageSource entities (page_source table).
 *
 * The bill pages themselves are collected in the next step by {@link LexMlBillCollector}.
 */
@Slf4j
@Service
public class LexMlBillListCollector {

    private final PageSourceRepository pageSourceRepository;
    private final BillUrlRepository billLinkRepository;

    //filters and sorting are included in the URL
    private static final String BASE_URL = "https://www.lexml.gov.br/busca/search?sort=reverse-year&f1-tipoDocumento=Proposi%C3%A7%C3%B5es%C2%A0Legislativas::Projeto%C2%A0de%C2%A0Lei;f2-localidade=Brasil;startDoc={0}";

    //don't change this, the site always returns 20 bills per page
    private static final int PAGE_SIZE = 20;

    @Autowired
    public LexMlBillListCollector(PageSourceRepository pageSourceRepository, BillUrlRepository billLinkRepository) {
        this.pageSourceRepository = pageSourceRepository;
        this.billLinkRepository = billLinkRepository;
    }

    public void collectLinks() {
        int docIndex = 1;
        boolean hasNextPage = true;

        do {
            String currentUrl = MessageFormat.format(BASE_URL, Integer.toString(docIndex));
            Optional<PageSource> source = Optional.empty();

            //check in db first by url
            if (pageSourceRepository.existsByPageUrl(currentUrl)) {
                source = pageSourceRepository.findByPageUrl(currentUrl);
            } else {
                HttpResponse<String> response = Unirest.get(currentUrl).asString();

                //if the page is empty, the while loop will exit (hasNextPage will be false)
                if (response.isSuccess() && isNotEmptyPage(response)) {
                    PageSource sourceEntity = new PageSource(
                            Country.BRAZIL,
                            PageType.LEXML_BILL_LIST.name(),
                            currentUrl,
                            response.getBody()
                    );

                    pageSourceRepository.save(sourceEntity);
                }
            }

            if (source.isPresent()) {
                //get the bill links from the page and store them
                List<BillUrl> storedLinks = getBillLinksFromPage(source.get().getRawSource())
                        .stream()
                        .map(link -> new BillUrl(Country.BRAZIL, link))
                        .filter(link -> !billLinkRepository.existsByCleanUrl(link.getCleanUrl()))
                        .toList();

                billLinkRepository.saveAll(storedLinks);
                log.info("Stored {} new bill links from page {}", storedLinks.size(), currentUrl);
            } else {
                hasNextPage = false; //we reached the end - stop the while loop
            }

            //go to next page
            docIndex += PAGE_SIZE;
        } while (hasNextPage);
    }

    private boolean isNotEmptyPage(HttpResponse<String> response) {
        return !response.getBody().contains("Desculpe, nenhum resultado encontrado..."); //meaning: No results found
    }

    private List<String> getBillLinksFromPage(String pageSource) {
        return Jsoup.parse(pageSource)
                .select("div.docHit")
                .stream()
                .flatMap(element -> element.select("a").stream())
                .map(link -> link.attr("href"))
                .filter(link -> !link.startsWith("javascript"))
                .map(Utils::toAbsolute)
                .toList();
    }

}
