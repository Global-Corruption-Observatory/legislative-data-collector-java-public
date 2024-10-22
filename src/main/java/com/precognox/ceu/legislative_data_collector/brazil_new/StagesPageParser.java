package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Stage variables for Brazil are collected from a separate page. This class is responsible for parsing those stages. The stages page URL is stored in a previous step.
 */
@Slf4j
@Service
public class StagesPageParser {

    private final EntityManager entityManager;
    private final PageSourceLoader pageSourceLoader;
    private final TransactionTemplate transactionTemplate;

    public static final Map<String, Integer> STORED_STAGES = Map.of(
            "Casa Iniciadora", 1,
            "Casa Iniciadora (Câmara)", 1,
            "Casa Iniciadora (Senado)", 1,
            "Casa Revisora", 2,
            "Casa Revisora (Senado)", 2,
            "Sanção", 3,
            "Sanção (Presidência da República)", 3
    );

    public StagesPageParser(
            EntityManager entityManager,
            PageSourceLoader pageSourceLoader,
            TransactionTemplate transactionTemplate) {
        this.entityManager = entityManager;
        this.pageSourceLoader = pageSourceLoader;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public void parseForAllBills() {
        log.info("Parsing stages for all records...");

        String query = "SELECT r FROM LegislativeDataRecord r "
                + "WHERE r.brazilCountrySpecificVariables.stagesPageUrl IS NOT NULL";

        Stream<LegislativeDataRecord> records =
                entityManager.createQuery(query, LegislativeDataRecord.class).getResultStream();

        records.forEach(this::processRecord);
    }

    private void processRecord(LegislativeDataRecord record) {
        String stagesPageLink = record.getBrazilCountrySpecificVariables().getStagesPageUrl();
        List<LegislativeStage> stages = parseStages(stagesPageLink);

        //set publication date from last stage
        if (record.getBrazilCountrySpecificVariables().getPublicationDate() == null) {
            stages.stream()
                    .filter(stage -> stage.getName().toLowerCase().contains("sanção"))
                    .map(LegislativeStage::getDate)
                    .findFirst()
                    .ifPresent(record.getBrazilCountrySpecificVariables()::setPublicationDate);
        }

        transactionTemplate.execute(status -> {
            record.setStages(stages);
            record.setStagesCount(stages.size());
            entityManager.merge(record);
            log.info("Updated record: {}", record.getRecordId());

            return status;
        });
    }

    public List<LegislativeStage> parseStages(String stagesPageLink) {
        Optional<PageSource> page =
                pageSourceLoader.loadFromDbOrFetchWithHttpGet(
                        Country.BRAZIL,
                        PageType.BILL_STAGES.name(),
                        stagesPageLink
                );

        return processPage(page.get().getRawSource());
    }

    public @NotNull List<LegislativeStage> processPage(String html) {
        //example: https://www.congressonacional.leg.br/materias/materias-bicamerais/-/ver/pl-583-2011
        Element stagesPage = Jsoup.parse(html).body();
        Elements headers = stagesPage.selectXpath("//div[@class='cn-mb-quadro']//h2");

        return headers.stream()
                .filter(element -> STORED_STAGES.keySet().stream().anyMatch(stage -> element.text().contains(stage)))
                .map(this::parseStage)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<LegislativeStage> parseStage(Element h2Element) {
        String name = h2Element.text().strip();
        Integer number = STORED_STAGES.get(name);
        LegislativeStage stage = new LegislativeStage(number, name);

        Element stageStatusDiv = h2Element.parent().selectXpath("//div[@class='cn-mb-fase--situacao']").first();

        if (stageStatusDiv != null && stageStatusDiv.text().contains("Fase concluída")) {
            Element dateElement = h2Element.parent().parent().selectFirst("div.cn-mb-fase--casa");

            if (dateElement != null) {
                parseDate(dateElement.text().strip()).ifPresent(stage::setDate);
            }

            return Optional.of(stage);
        }

        return Optional.empty();
    }

    private Optional<LocalDate> parseDate(String dateText) {
        /*
        examples:
            23/fev 2011
            4/ago 2022
            21/mar 2024
        */
        Optional<MatchResult> dateMatch =
                Pattern.compile("(\\d{1,2})/([a-z]{3}) (\\d{4})").matcher(dateText).results().findFirst();

        return dateMatch.map(match -> {
            int day = Integer.parseInt(match.group(1));
            int month = Constants.MONTH_TRANSLATIONS.get(match.group(2));
            int year = Integer.parseInt(match.group(3));

            return LocalDate.of(year, month, day);
        });
    }
}
