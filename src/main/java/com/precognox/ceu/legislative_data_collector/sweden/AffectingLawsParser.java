package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class AffectingLawsParser {

    private final PageSourceLoader pageSourceLoader;
    private final PrimaryKeyGeneratingRepository recordRepository;

    private static final Pattern DATE_EF_REGEX = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern LAW_REFERENCE_REGEX = Pattern.compile("Ändring, SFS (\\d{4}:\\d+)");

    @Autowired
    public AffectingLawsParser(
            PageSourceLoader pageSourceLoader, PrimaryKeyGeneratingRepository recordRepository) {
        this.pageSourceLoader = pageSourceLoader;
        this.recordRepository = recordRepository;
    }

    @Transactional
    public void processRecords() {
        log.info("Processing affecting laws for all records...");
        recordRepository.streamAllWithUnprocessedAffectingLawsPage().forEach(this::processRecord);
        log.info("Finished processing");
    }

    public void processRecord(LegislativeDataRecord record) {
        String pageUrl = record.getSwedenCountrySpecificVariables().getAffectingLawsPageUrl();

        if (pageUrl != null && record.getAffectingLawsCount() == null) {
            PageSource storedPage =
                    pageSourceLoader.loadFromDbOrFetchWithBrowser(PageType.AFFECTING_LAWS.name(), pageUrl, null);

            //parse date entering into force
            Document parsedPage = Jsoup.parse(storedPage.getRawSource());
            Optional.ofNullable(parsedPage.body().selectXpath("//span[contains(text(), 'Ikraft')]").first())
                    .map(Element::parent)
                    .map(Element::text)
                    .map(DATE_EF_REGEX::matcher)
                    .map(Matcher::results)
                    .flatMap(Stream::findFirst)
                    .map(MatchResult::group)
                    .map(Utils::parseNumericDate)
                    .ifPresent(record::setDateEnteringIntoForce);

            Set<String> laws = parseLawReferences(parsedPage);
            record.setAffectingLawsCount(laws.size());

            laws.stream()
                    .map(recordRepository::findByLawId)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(LegislativeDataRecord::getDatePassing)
                    .min(Comparator.naturalOrder())
                    .ifPresent(record::setAffectingLawsFirstDate);

            recordRepository.merge(record);
        }

        log.info("Processed record: {}", record.getRecordId());
    }

    public Set<String> parseLawReferences(Document page) {
        return Optional.ofNullable(page.selectFirst("div.content"))
                .stream()
                .flatMap(div -> div.select("div.result-inner-sub-box-header").stream())
                .map(Element::text)
                .flatMap(headerText -> LAW_REFERENCE_REGEX.matcher(headerText).results())
                .map(matchResult -> matchResult.group(1))
                .collect(Collectors.toSet());
    }
}
