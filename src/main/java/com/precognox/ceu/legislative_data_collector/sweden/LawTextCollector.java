package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LawTextCollector {

    private final PageSourceLoader pageSourceLoader;
    private final PrimaryKeyGeneratingRepository recordRepository;

    private static final String START_LABEL = "Lagens innehåll";
    private static final Pattern END_REGEX = Pattern.compile("^2 kap.", Pattern.MULTILINE);

    @Autowired
    public LawTextCollector(PageSourceLoader pageSourceLoader, PrimaryKeyGeneratingRepository recordRepository) {
        this.pageSourceLoader = pageSourceLoader;
        this.recordRepository = recordRepository;
    }

    @Transactional
    public void processAllRecords() {
        log.info("Processing law text for all records...");

        recordRepository.streamAllWithUnprocessedLawTextUrl().forEach(this::processRecord);
    }

    public void processRecord(LegislativeDataRecord record) {
        if (record.getLawTextUrl() != null && record.getLawText() == null) {
            PageSource storedPage = pageSourceLoader.loadFromDbOrFetchWithBrowser(
                    PageType.LAW_TEXT.name(), record.getLawTextUrl(), null
            );

            processPage(storedPage).ifPresent(lawText -> {
                record.setLawText(lawText);
                record.setLawSize(TextUtils.getLengthWithoutWhitespace(lawText));

                recordRepository.mergeInNewTransaction(record);
            });
        }
    }

    public Optional<String> processPage(PageSource page) {
        return parseTextFromPage(page.getRawSource()).flatMap(this::parseFromText);
    }

    public Optional<String> parseTextFromPage(String pageSource) {
        Element page = Jsoup.parse(pageSource).body();

        return Optional.ofNullable(page.selectFirst("div.result-box-text.body-text")).map(Element::wholeText);
    }

    public Optional<String> parseFromText(String fullText) {
        String lawText = TextUtils.trimLines(fullText);
        int startIndex = lawText.indexOf(START_LABEL);

        if (startIndex != -1) {
            lawText = lawText.substring(startIndex + START_LABEL.length());
            Matcher matcher = END_REGEX.matcher(lawText);

            if (matcher.find()) {
                lawText = lawText.substring(0, matcher.start());
            }
        }

        return Optional.of(lawText).map(String::strip);
    }

}
