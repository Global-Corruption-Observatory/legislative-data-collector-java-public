package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.AmendmentOriginator;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Processes the amendment pages and PDFs, stored for records in a previous step.
 */
@Slf4j
@Service
public class BrAmendmentCollector {

    private final EntityManager entityManager;
    private final PageSourceLoader pageSourceLoader;
    private final TransactionTemplate transactionTemplate;

    /**
     * Cache for originator name and affiliation, to minimize page fetches. Key is name, value is affiliation.
     */
    private final Map<String, String> originatorAffiliationCache = new HashMap<>();

    @Autowired
    public BrAmendmentCollector(
            PageSourceLoader pageSourceLoader,
            EntityManager entityManager,
            TransactionTemplate transactionTemplate) {
        this.pageSourceLoader = pageSourceLoader;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public void processAll() {
        processAmendmentPages();
    }

    /**
     * Processes the stored amendment links for a single record (fetch pages).
     */
    private void processAmendmentPages() {
        //query for unprocessed records
        String query = "SELECT r FROM LegislativeDataRecord r"
                + " WHERE r.country = :country"
                + " AND r.amendmentCount > 0"
                + " AND r.amendments IS EMPTY";

        Stream<LegislativeDataRecord> records = entityManager.createQuery(query, LegislativeDataRecord.class)
                .setParameter("country", Country.BRAZIL)
                .getResultStream();

        records
                .peek(record -> log.info("Processing amendments for record {}", record.getRecordId()))
                .forEach(record -> transactionTemplate.executeWithoutResult(status -> processRecord(record)));

        log.info("Done processing amendment pages");
    }

    public void processRecord(LegislativeDataRecord record) {
        record.getBrazilCountrySpecificVariables()
                .getAmendmentPageLinks()
                .stream()
                .map(this::fetchAmendmentPage)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(page -> parsePage(page).stream())
                .forEach(amendment -> {
                    amendment.setDataRecord(record);
                    entityManager.persist(amendment);
                });
    }

    private Optional<PageSource> fetchAmendmentPage(String link) {
        return pageSourceLoader.loadFromDbOrFetchWithHttpGet(Country.BRAZIL, PageType.AMENDMENT_LIST.name(), link);
    }

    //example: https://www.camara.leg.br/proposicoesWeb/prop_emendas?idProposicao=103297&subst=0
    public List<Amendment> parsePage(PageSource source) {
        //collect amendment ID, originator, orig. affiliation, date, committee name, text link
        Element page = Jsoup.parse(source.getRawSource()).body();

        //get committee name from header, set for all amendments on page later
        Optional<String> committeeName =
                page.selectXpath("h4[text()[contains(.,'Comissão')]]").stream().map(Element::text).findFirst();

        //get the only table on the page, map every row to an amendment entity
        Element table = page.selectFirst("table");

        if (table != null) {
            List<Amendment> amendments = table.select("tr").stream().skip(1) //skip header row
                    .map(row -> {
                        Elements cells = row.select("td");

                        Amendment amendment = new Amendment();
                        amendment.setAmendmentId(cells.get(0).text().strip());

                        String origName = cells.get(3).text().strip();
                        AmendmentOriginator orig = new AmendmentOriginator(origName);
                        amendment.setOriginators(List.of(orig));

                        if (originatorAffiliationCache.containsKey(origName)) {
                            orig.setAffiliation(originatorAffiliationCache.get(origName));
                        } else {
                            String detailsLink = cells.get(0).selectFirst("a").attr("href");

                            Optional<PageSource> amDetailsPage =
                                    pageSourceLoader.loadFromDbOrFetchWithHttpGet(
                                            Country.BRAZIL,
                                            PageType.AMENDMENT_DETAILS.name(),
                                            toAbsolute(detailsLink)
                                    );

                            if (amDetailsPage.isPresent()) {
                                Element pg = Jsoup.parse(amDetailsPage.get().getRawSource()).body();

                                pg.selectXpath("//strong[text()='Autor']//..//a")
                                        .stream()
                                        .map(Element::text)
                                        .findFirst()
                                        .ifPresent(origString -> {
                                            String affiliation = parseAffil(origString.strip());

                                            orig.setAffiliation(affiliation);
                                            originatorAffiliationCache.put(origName, affiliation);
                                        });
                            }
                        }

                        cells.get(4)
                                .selectXpath("//a[text()[contains(.,'Inteiro teor')]]")
                                .stream()
                                .map(a -> a.attr("href"))
                                .map(this::toAbsolute)
                                .findFirst()
                                .ifPresent(amendment::setTextSourceUrl);

                        amendment.setDate(Utils.parseDate(cells.get(2).text().strip()));
                        committeeName.ifPresent(amendment::setCommitteeName);

                        return amendment;
                    }).toList();

            return amendments;
        }

        return List.of();
    }

    private String toAbsolute(String url) {
        return "https://www.camara.leg.br/proposicoesWeb/" + url;
    }

    private String parseAffil(String origString) {
        return origString.contains(" - ") ? origString.split(" - ")[1].strip() : origString.strip();
    }

}
