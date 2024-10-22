package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.DocumentDownloader;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Collects law_text, law_size, date_entering_force, and publication_date, for records where the law text URL is available.
 */
@Slf4j
@Service
public class BrLawTextCollector {

    private final DocumentDownloader docDownloader;
    private final PageSourceLoader pageSourceLoader;
    private final BrAffectingLawsParser affectingLawsParser;
    private final PrimaryKeyGeneratingRepository recordRepository;

    @Autowired
    public BrLawTextCollector(
            DocumentDownloader docDownloader,
            PageSourceLoader pageSourceLoader,
            BrAffectingLawsParser affectingLawsParser,
            PrimaryKeyGeneratingRepository recordRepository) {
        this.docDownloader = docDownloader;
        this.pageSourceLoader = pageSourceLoader;
        this.affectingLawsParser = affectingLawsParser;
        this.recordRepository = recordRepository;
    }

    @Transactional
    public void collectAll() {
        recordRepository.streamAllWithUnprocessedLawTextUrl(Country.BRAZIL)
                .peek(record -> log.info("Processing record: {}", record.getRecordId()))
                .forEach(this::processRecord);
    }

    private void processRecord(LegislativeDataRecord record) {
        Optional<PageSource> page = pageSourceLoader.loadFromDbOrFetchWithHttpGet(Country.BRAZIL,
                PageType.LAW_TEXT.name(),
                record.getLawTextUrl()
        );

        if (page.isPresent()) {
            Element parsed = Jsoup.parse(page.get().getRawSource()).body();
            affectingLawsParser.processRecord(record, parsed);

            Element lawTextLink = parsed.selectXpath("//a[text()='Imprensa Nacional']").first(); //OR: check for 'Publicação Original' cell in table

            if (lawTextLink != null) {
                String cellText = lawTextLink.parent().text();

                Optional<LocalDate> datePublication = Utils.DATE_PATTERN.matcher(cellText)
                        .results()
                        .map(MatchResult::group)
                        .distinct()
                        .map(Utils::parseDate)
                        .findFirst();

                datePublication.ifPresent(datePub -> {
                    record.getBrazilCountrySpecificVariables().setPublicationDate(datePub);
                    recordRepository.mergeInNewTransaction(record);
                });

                Optional<String> lawText = docDownloader.processWithBrowser(lawTextLink.attr("href"));

                lawText.ifPresent(text -> {
                    if (datePublication.isPresent()) {
                        //parse date EF from text
                        if (text.contains("entra em vigor na data de sua publicação")) {
                            record.setDateEnteringIntoForce(datePublication.get());
                        } else {
                            //handle with line breaks: entra em vigor após decorridos 180 (cento e oitenta) dias de sua publicação oficial
                            String regex =
                                    "entra em vigor .+? (\\d+) \\(.+?\\)? dias de sua publicação oficial";

                            Optional<Integer> days = Pattern.compile(regex, Pattern.DOTALL)
                                    .matcher(text)
                                    .results()
                                    .map(matchResult -> matchResult.group(1))
                                    .map(Integer::parseInt)
                                    .findFirst();

                            days.ifPresent(day ->
                                    record.setDateEnteringIntoForce(datePublication.get().plusDays(day)));
                        }
                    }

                    record.setLawText(text);
                    recordRepository.mergeInNewTransaction(record);

                    log.info("Law text collected for record: {} with size: {}",
                            record.getRecordId(),
                            record.getLawText().length()
                    );
                });
            }
        }
    }

}
