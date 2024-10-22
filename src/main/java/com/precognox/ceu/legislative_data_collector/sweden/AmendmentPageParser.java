package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.AmendmentOriginator;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AmendmentPageParser {

    private final PageSourceLoader pageSourceLoader;
    private final PrimaryKeyGeneratingRepository recordRepository;
    private final ReportPageParser reportPageParser;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    private static final Pattern AMENDMENT_ID_PATTERN = Pattern.compile("\\d{4}/\\d+:\\w+");

    public AmendmentPageParser(
            PrimaryKeyGeneratingRepository recordRepository,
            PageSourceLoader pageSourceLoader,
            ReportPageParser reportPageParser) {
        this.recordRepository = recordRepository;
        this.pageSourceLoader = pageSourceLoader;
        this.reportPageParser = reportPageParser;
    }

    @Transactional
    public void processAllRecords() {
        log.info("Processing amendments for all records...");

        recordRepository.findRecordsWithAmendments(Country.SWEDEN).forEach(
                record -> executorService.submit(() -> processRecord(record))
        );

        log.info("Finished processing");
    }

    public void processRecord(LegislativeDataRecord record) {
        for (Amendment amendment : record.getAmendments()) {
            Optional<PageSource> pageSource = pageSourceLoader.loadFromDbOrFetchWithHttpGet(
                    Country.SWEDEN, PageType.AMENDMENT.name(), amendment.getPageUrl()
            );

            pageSource.ifPresent(source -> {
                processPage(amendment, source);
                processVotes(amendment, source);
            });
        }

        recordRepository.mergeInNewTransaction(record);

        log.info("Processed record: {}", record.getRecordId());
    }

    public void processPage(Amendment amendment, PageSource pageSource) {
        Element page = Jsoup.parse(pageSource.getRawSource()).body();

        if (amendment.getAmendmentId() == null) {
            Optional.ofNullable(page.selectFirst("p[aria-roledescription='Underrubrik']"))
                    .map(Element::text)
                    .map(AMENDMENT_ID_PATTERN::matcher)
                    .stream()
                    .flatMap(Matcher::results)
                    .findFirst()
                    .map(MatchResult::group)
                    .ifPresent(amendment::setAmendmentId);
        }

        Optional.ofNullable(page.selectFirst("dt:contains(Tilldelat)"))
                .map(Element::nextElementSibling)
                .map(Element::text)
                .map(String::trim)
                .ifPresent(amendment::setCommitteeName);

        Optional.ofNullable(page.selectFirst("main#content"))
                .map(div -> div.selectFirst("a"))
                .map(a -> a.attr("href"))
                .filter(link -> !link.contains("/ledamot/"))
                .ifPresent(amendment::setTextSourceUrl);

        Optional.ofNullable(page.selectFirst("h3:contains(Yrkanden)"))
                .map(h3 -> h3.parents().get(3))
                .filter(e -> "div".equals(e.tagName()))
                .map(div -> div.getElementsByTag("li"))
                .stream()
                .flatMap(Elements::stream)
                .map(li -> li.selectFirst("dt:contains(Kammarens beslut)"))
                .filter(Objects::nonNull)
                .map(Element::nextElementSibling)
                .filter(Objects::nonNull)
                .filter(e -> "dd".equals(e.tagName()))
                .map(Element::text)
                .map(this::parseOutcome)
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(amendment::setOutcome);

        List<AmendmentOriginator> origs = new OriginatorsParser().parseOriginators(page)
                .stream()
                .map(o -> new AmendmentOriginator(o.getName(), o.getAffiliation()))
                .toList();

        amendment.setOriginators(origs);
    }

    private Amendment.Outcome parseOutcome(String label) {
        //edge case: https://www.riksdagen.se/sv/dokument-och-lagar/dokument/motion/med-anledning-av-prop.-202122257-en-ny_ha024

        return switch (label) {
            case "Avslag" -> Amendment.Outcome.REJECTED;
            case "Bifall" -> Amendment.Outcome.APPROVED;
            default -> null;
        };
    }

    private void processVotes(Amendment amendment, PageSource pageSource) {
        Element parsedPage = Jsoup.parse(pageSource.getRawSource()).body();

        Optional<String> votesPageLink = Optional.ofNullable(parsedPage.selectFirst("h3:contains(Yrkanden)"))
                .map(h3 -> h3.parents().get(3))
                .filter(e -> "div".equals(e.tagName()))
                .map(div -> div.selectFirst("li"))
                .map(li -> li.selectFirst("a"))
                .map(a -> a.attr("href"));

        votesPageLink.ifPresent(link -> reportPageParser.processReportPage(amendment, link));
    }

}
