package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class SuggestionsPageParser {

    private final PageSourceLoader pageSourceLoader;
    private final PrimaryKeyGeneratingRepository recordRepository;

    @Autowired
    public SuggestionsPageParser(
            PageSourceLoader pageSourceLoader, PrimaryKeyGeneratingRepository recordRepository) {
        this.pageSourceLoader = pageSourceLoader;
        this.recordRepository = recordRepository;
    }

    @Transactional
    public void processRecords() {
        log.info("Processing suggestions page for all records...");

        recordRepository.streamAllWithSuggestionsPageUrl().forEach(this::parseVariables);
    }

    public void parseVariables(LegislativeDataRecord record) {
        String forslagspunkterPageUrl = record.getSwedenCountrySpecificVariables().getForslagspunkterPageUrl();

        Optional<PageSource> storedPage = pageSourceLoader.loadFromDbOrFetchWithHttpGet(
                Country.SWEDEN,
                PageType.SUGGESTIONS.name(),
                forslagspunkterPageUrl
        );

        storedPage.ifPresent(page -> {
            processPage(record, page);

            recordRepository.mergeInNewTransaction(record);
            log.info("Processed record: {}", record.getRecordId());
        });
    }

    public void processPage(LegislativeDataRecord record, PageSource pageSource) {
        Element parsedPage = Jsoup.parse(pageSource.getRawSource()).body();

        parseReportId(parsedPage).ifPresent(record.getSwedenCountrySpecificVariables()::setReportId);
        checkForPassedStatus(parsedPage).ifPresent(record::setBillStatus);
        parseCommitteeHearingCount(parsedPage).ifPresent(record::setCommitteeHearingCount);
        parseStage1TextUrl(parsedPage).ifPresent(
                stage1Url -> record.getSwedenCountrySpecificVariables().setStage1TextUrl(stage1Url));

        if (record.getDatePassing() == null) {
            parseDatePassing(parsedPage).ifPresent(datePassing -> {
                record.setDatePassing(datePassing);
                record.setBillStatus(LegislativeDataRecord.BillStatus.PASS);
            });
        }

        parseDateCommittee(parsedPage).ifPresent(commDate -> {
            record.setCommitteeDate(commDate);

            if (!record.getCommittees().isEmpty()) {
                record.getCommittees().get(0).setDate(commDate);
            }
        });

        List<LegislativeStage> newStages = parseStages(parsedPage);

        Optional<LegislativeStage> firstStage = record.getStages().stream()
                .filter(stg -> stg.getStageNumber().equals(1))
                .findFirst();

        List<LegislativeStage> stages = new ArrayList<>();
        firstStage.ifPresent(stages::add);
        stages.addAll(newStages);

        record.setStages(stages);
        record.setStagesCount(stages.size());
    }

    private Optional<String> parseReportId(Element page) {
        Pattern reportIdRegex = Pattern.compile("\\d{4}/\\d+.+$", Pattern.MULTILINE);

        return Optional.ofNullable(page.selectFirst("p[aria-roledescription='Underrubrik']"))
                .map(Element::text)
                .map(String::strip)
                .map(reportIdRegex::matcher)
                .map(Matcher::results)
                .flatMap(Stream::findFirst)
                .map(MatchResult::group);
    }

    /**
     * Overrides the existing bill status based on the data on the current page, only if it's passed.
     *
     * @param parsedPage
     * @return
     */
    private Optional<LegislativeDataRecord.BillStatus> checkForPassedStatus(Element parsedPage) {
        boolean passed = Optional.ofNullable(parsedPage.selectFirst("h3:contains(Beslut)"))
                .map(Element::nextElementSibling)
                .filter(e -> "div".equals(e.tagName()))
                .map(Element::text)
                .filter(this::containsPassedLabel)
                .isPresent();

        return passed ? Optional.of(LegislativeDataRecord.BillStatus.PASS) : Optional.empty();
    }

    private boolean containsPassedLabel(String text) {
        List<String> passedLabels = List.of(
                "Riksdagens beslut Kammaren biföll utskottets förslag till beslut.",
                "Riksdagens beslut Kammaren biföll utskottets förslag.",
                "Riksdagen sa ja till regeringens förslag."
        );

        return passedLabels.stream().anyMatch(text::contains);
    }

    private Optional<LocalDate> parseDatePassing(Element parsedPage) {
        return Optional.ofNullable(parsedPage.selectFirst("dt:contains(Beslutat)"))
                .map(Element::nextElementSibling)
                .map(Element::text)
                .map(Utils::parseDateExpr);
    }

    private Optional<LocalDate> parseDateCommittee(Element parsedPage) {
        return Optional.ofNullable(parsedPage.selectFirst("h4:contains(Alla beredningar i utskottet)"))
                .map(Element::nextElementSibling)
                .map(Element::text)
                .map(text -> text.split(","))
                .filter(array -> array.length != 0)
                .map(array -> array[array.length - 1].trim())
                .map(Utils::parseNumericDate);
    }

    private Optional<String> parseStage1TextUrl(Element parsedPage) {
        return Optional.ofNullable(parsedPage.selectFirst("h2:contains(Hela betänkandet)"))
                .map(Element::nextElementSibling)
                .filter(next -> "div".equals(next.tagName()))
                .map(div -> div.getElementsByTag("a"))
                .filter(elements -> !elements.isEmpty())
                .map(elements -> elements.get(0))
                .map(link -> link.attr("href"));
    }

    private Optional<Integer> parseCommitteeHearingCount(Element parsedPage) {
        return Optional.ofNullable(parsedPage.selectFirst("h4:contains(Alla beredningar i utskottet)"))
                .map(Element::nextElementSibling)
                .map(Element::text)
                .map(dates -> dates.split(",").length);
    }

    private List<LegislativeStage> parseStages(Element page) {
        List<LegislativeStage> stages = new ArrayList<>();

        String stage2DatePattern = "Justering: (\\d{4}-\\d{2}-\\d{2})";
        String stage3DatePattern = "Debatt i kammaren: (\\d{4}-\\d{2}-\\d{2})";
        String stage4DatePattern = "Beslut: (\\d{4}-\\d{2}-\\d{2})";

        getLegislativeStage(page, 2, "Beredning", stage2DatePattern).ifPresent(stages::add);
        getLegislativeStage(page, 3, "Debatt", stage3DatePattern).ifPresent(stages::add);
        getLegislativeStage(page, 4, "Beslut", stage4DatePattern).ifPresent(stages::add);

        return stages;
    }

    private Optional<LegislativeStage> getLegislativeStage(
            Element parsedPage, int num, String name, String datePattern) {
        String selector = "h3:contains(%s)".formatted(name);
        Pattern dateRegex = Pattern.compile(datePattern, Pattern.MULTILINE);

        return Optional.of(parsedPage.select(selector))
                .stream()
                .flatMap(Elements::stream)
                .map(Element::parent).filter(Objects::nonNull)
                .map(Element::text)
                .map(dateRegex::matcher)
                .flatMap(Matcher::results)
                .map(matchResult -> matchResult.group(1))
                .map(Utils::parseNumericDate)
                .map(date -> new LegislativeStage(num, date, name))
                .findFirst();
    }

}
