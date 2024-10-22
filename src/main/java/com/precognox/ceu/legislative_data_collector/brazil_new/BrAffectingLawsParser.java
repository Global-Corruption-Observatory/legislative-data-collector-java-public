package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

@Slf4j
@Service
public class BrAffectingLawsParser {

    private final PageSourceLoader pageSourceLoader;
    private final PrimaryKeyGeneratingRepository recordRepository;

    @Autowired
    public BrAffectingLawsParser(
            PageSourceLoader pageSourceLoader, PrimaryKeyGeneratingRepository recordRepository) {
        this.pageSourceLoader = pageSourceLoader;
        this.recordRepository = recordRepository;
    }

    /**
     * Called from BrLawTextCollector.
     *
     * @param record The record to update.
     * @param parsedLawTextPage The same page we get the law text from, in PDF format.
     */
    public void processRecord(LegislativeDataRecord record, Element parsedLawTextPage) {
        Element senadoLink = parsedLawTextPage.selectXpath("//a[text()='Senado Federal']").first();

        if (senadoLink != null) {
            pageSourceLoader.loadFromDbOrFetchWithHttpGet(record.getCountry(), "", senadoLink.attr("href"))
                    .ifPresent(senadoPage -> {
                        parseAffectingLaws(record, senadoPage);
                        recordRepository.mergeInNewTransaction(record);

                        log.info(
                                "Stored {} affecting laws for record: {}",
                                record.getAffectingLawsCount(),
                                record.getRecordId()
                        );
                    });
        }
    }

    public void parseAffectingLaws(LegislativeDataRecord record, PageSource page) {
        Element parsed = Jsoup.parse(page.getRawSource()).body();

        //find Normas posteriores section - div#collapseVide
        Pattern lawReferenceRegex = Pattern.compile("(Decreto|Lei) nº ([\\d.]+) de (\\d{2}/\\d{2}/(\\d{4}))");

        List<MatchResult> lawReferences = parsed.selectXpath("//div[@id='collapseVide']//a")
                .stream()
                .map(Element::text)
                .flatMap(text -> lawReferenceRegex.matcher(text).results())
                .toList();

        //parse dates from law references and find earliest
        lawReferences.stream()
                .map(matchResult -> matchResult.group(3))
                .map(Utils::parseDate)
                .min(Comparator.naturalOrder())
                .ifPresent(record::setAffectingLawsFirstDate);

        List<String> affLaws = lawReferences.stream()
                .map(matchResult -> matchResult.group(2) + "/" + matchResult.group(4))
                .map(law -> law.replace(".", ""))
                .distinct()
                .toList();

        record.setAffectingLawsCount(affLaws.size());
        record.getBrazilCountrySpecificVariables().setAffectingLaws(affLaws);
    }

}
