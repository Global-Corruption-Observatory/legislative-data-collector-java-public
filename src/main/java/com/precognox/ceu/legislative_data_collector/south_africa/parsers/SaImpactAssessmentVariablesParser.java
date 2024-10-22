package com.precognox.ceu.legislative_data_collector.south_africa.parsers;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.ImpactAssessment;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static com.precognox.ceu.legislative_data_collector.south_africa.SaPageCollector.SOUTH_AFRICA_MAIN_URL;
import static com.precognox.ceu.legislative_data_collector.utils.DateUtils.parseSouthAfricaImpactAssessmentDate;

@Slf4j
@Service
@AllArgsConstructor
public class SaImpactAssessmentVariablesParser {
    private final PrimaryKeyGeneratingRepository recordRepository;
    private final PageSourceRepository pageSourceRepository;
    private final LegislativeDataRepository legislativeDataRepository;
    private final PdfParser pdfParser;
    private TransactionTemplate transactionTemplate;

    @Transactional
    public void parseAllPages() {
        log.info("Querying unprocessed pages...");

        recordRepository.streamUnprocessedImpactAssessments(Country.SOUTH_AFRICA)
                .forEach(record -> transactionTemplate.execute(status -> {
                    recordRepository.merge(parsePage(record));

                    log.info("Impact assessment data processed for bill page: {}", record.getBillPageUrl());
                    return record;
                }));
    }

    public LegislativeDataRecord parsePage(LegislativeDataRecord record) {
        PageSource source = pageSourceRepository.getByPageUrl(record.getBillPageUrl());
        Element page = Jsoup.parse(source.getRawSource()).body();

        Optional<String> impactAssessmentUrl = getImpactAssessmentUrl(page);
        if (impactAssessmentUrl.isPresent()) {
            record.setImpactAssessmentDone(Boolean.TRUE);
            record.setImpactAssessments(
                    parseImpactAssessments(page, impactAssessmentUrl.get(), record));
        } else {
            record.setImpactAssessmentDone(Boolean.FALSE);
        }

        deleteConnectingImpactAssessments(record);
        return record;
    }

    public Optional<String> getImpactAssessmentUrl(Element page) {

        try {
            return page.select("div.tab-content").first()
                    .select("div.tab-pane")
                    .stream()
                    .map(tabContent -> tabContent.getElementsByTag("h4").first())
                    .filter(Objects::nonNull)
                    .map(Element::parent)
                    .filter(Objects::nonNull)
                    .filter(h4 -> h4.text().contains("SEIAS"))
                    .findFirst()
                    .map(a -> a.getElementsByTag("a").first())
                    .map(url -> url.attr("href"));

        } catch (NullPointerException npe) {
            log.error("Impact assessment url not found: {}", npe.getMessage());
        }

        return Optional.empty();
    }

    public List<ImpactAssessment> parseImpactAssessments(Element page, String pdfUrl, LegislativeDataRecord record) {
        List<ImpactAssessment> impactAssessments = new ArrayList<>();

        String iaTitle = pdfUrl.replace(SOUTH_AFRICA_MAIN_URL + "/files/", "")
                .replace(".pdf", "").replace("_", " ");

        Optional<String> iaDateString = Optional.ofNullable(page.select("ul.nav-tabs").first())
                .map(a -> a.select("a:contains(SEIAS)").first())
                .map(Element::text)
                .map(extractedDate -> extractedDate.replaceAll("SEIAS .*\\|\\s*(.*)", "$1"));
        LocalDate iaDate = null;
        if (iaDateString.isPresent()) {
            try {
                iaDateString = Pattern.compile("\\b\\d{2}\\s+\\w{3}\\s+\\d{4}\\b")
                        .matcher(iaDateString.get())
                        .results()
                        .map(MatchResult::group)
                        .findFirst();

                iaDate = parseSouthAfricaImpactAssessmentDate(iaDateString.get());
            } catch (DateTimeParseException dtpe) {
                log.error("Invalid date format: ", dtpe);
            }
        }

        Optional<String> iaText = pdfParser.tryPdfTextExtraction(pdfUrl);

        if (iaText.isPresent()) {
            Integer iaTextSize = TextUtils.getLengthWithoutWhitespace(iaText.get());

            ImpactAssessment impactAssessment = new ImpactAssessment(iaTitle, iaDate, iaText.get(), iaTextSize);
            impactAssessment.setDataRecord(record);
            impactAssessments.add(impactAssessment);
        }

        return impactAssessments;
    }

    private void deleteConnectingImpactAssessments(LegislativeDataRecord record) {
        if (!record.getImpactAssessments().isEmpty()) {
            legislativeDataRepository.deleteConnectingImpactAssessments(record.getId());
        }
    }
}
