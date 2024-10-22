package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.swe.SwedenCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class LawPageParser {

    private final EntityManager entityManager;
    private final PrimaryKeyGeneratingRepository recordRepository;
    private final PageSourceRepository pageSourceRepository;

    public LawPageParser(EntityManager entityManager,
            PrimaryKeyGeneratingRepository recordRepository, PageSourceRepository pageSourceRepository) {
        this.entityManager = entityManager;
        this.recordRepository = recordRepository;
        this.pageSourceRepository = pageSourceRepository;
    }

    @Transactional
    public void parseAllPages() {
        log.info("Parsing law pages for Sweden...");

        entityManager.createQuery("SELECT p FROM PageSource p WHERE p.pageUrl LIKE '%/lag%'", PageSource.class)
                .getResultStream()
                .peek(source -> log.info("Parsing page: {}", source.getPageUrl()))
                .map(this::parsePage)
                .forEach(recordRepository::save);
    }

    public LegislativeDataRecord parsePage(PageSource source) {
        LegislativeDataRecord record = new LegislativeDataRecord(Country.SWEDEN);
        record.setBillPageUrl(source.getPageUrl());
        record.setDateProcessed(LocalDateTime.now());

        Element parsed = Jsoup.parse(source.getRawSource()).body();

        Optional.ofNullable(parsed.selectXpath("//main[@id='content']//h1").first())
                .ifPresent(title -> record.setBillTitle(title.text().strip()));

        Element lawIdLabel = parsed.selectXpath("//main[@id='content']//b[text()='SFS nr']").first();

        if (lawIdLabel != null) {
            record.setLawId(getTextOfFollowingNode(lawIdLabel));
        }

        Element datePassingLabel = parsed.selectXpath("//main[@id='content']//b[text()='Utfärdad']").first();

        if (datePassingLabel != null) {
            record.setDatePassing(Utils.parseNumericDate(getTextOfFollowingNode(datePassingLabel)));
        }

        Element lawTextLink = parsed.selectXpath(
                "//main[@id='content']//b[text()='Källa']/following-sibling::a").first();

        Optional.ofNullable(lawTextLink)
                .map(a -> a.attr("href"))
                .ifPresent(record::setLawTextUrl);

        if (record.getSwedenCountrySpecificVariables() == null) {
            record.setSwedenCountrySpecificVariables(new SwedenCountrySpecificVariables());
            record.getSwedenCountrySpecificVariables().setLegislativeDataRecord(record);
        }

        Element affectingLawsLink = parsed.selectXpath(
                "//main[@id='content']//b[text()='Ändringsregister']/following-sibling::a").first();

        Optional.ofNullable(affectingLawsLink)
                .map(a -> a.attr("href"))
                .ifPresent(record.getSwedenCountrySpecificVariables()::setAffectingLawsPageUrl);

        return record;
    }

    private @NotNull String getTextOfFollowingNode(Element lawIdLabel) {
        List<Node> childNodes = lawIdLabel.parent().childNodes();
        int i = childNodes.indexOf(lawIdLabel);

        return childNodes.get(i + 1).toString().replace(": ", "").strip();
    }

}
