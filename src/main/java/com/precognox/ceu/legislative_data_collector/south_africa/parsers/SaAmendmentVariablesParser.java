package com.precognox.ceu.legislative_data_collector.south_africa.parsers;

import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

@Slf4j
@Service
@AllArgsConstructor
public class SaAmendmentVariablesParser {
    private final PrimaryKeyGeneratingRepository recordRepository;
    private final PageSourceRepository pageSourceRepository;
    private final LegislativeDataRepository legislativeDataRepository;
    private final PdfParser pdfParser;
    private TransactionTemplate transactionTemplate;

    private static final Pattern AMENDMENT_PATTERN = Pattern.compile("\\(As agreed to by the[^)]*\\)+");
    private static Pattern AMENDMENT_PATTERN_WITH_MISSING_CLOSING_PARENTHESES = Pattern.compile(
            "\\(As agreed to by the[^)]*\\)");
    private static final Pattern AMENDMENT_PATTERN_WITH_EXTRA_CLOSING_PARENTHESES = Pattern.compile(
            "\\(As agreed to by the.*\\)\\)");
    private static final Pattern AMENDMENT_COMMITTEE_PATTERN = Pattern.compile(
            "\\(As agreed to by the (.*?)\\(");
    private static final Pattern AMENDMENT_COMMITTEE_PATTERN_WITH_MISSING_PLENARY = Pattern.compile(
            "\\(As agreed to by the (.*?)\\)");
    private static final Pattern AMENDMENT_PLEANRY_PATTERN = Pattern.compile("\\((.*?)\\((.*?)\\)");

    @Transactional
    public void parseAllPages() {
        log.info("Querying unprocessed pages...");

        recordRepository.streamUnprocessedAmendments(Country.SOUTH_AFRICA)
                .forEach(record -> transactionTemplate.execute(status -> {
                    recordRepository.merge(parsePage(record));

                    log.info("Amendment data processed for bill page: {}", record.getBillPageUrl());
                    return record;
                }));
    }

    public LegislativeDataRecord parsePage(LegislativeDataRecord record) {
        PageSource source = pageSourceRepository.getByPageUrl(record.getBillPageUrl());
        Element page = Jsoup.parse(source.getRawSource()).body();

        List<Amendment> amendments = parseAmendments(page, record);
        record.setAmendmentCount(amendments.size());
        record.setAmendments(amendments);

        deleteConnectingAmendts(record);

        return record;
    }

    public List<Amendment> parseAmendments(Element page, LegislativeDataRecord record) {
        List<Amendment> amendments = new ArrayList<>();

        List<Element> potentialAmendmentElements = getPotentialAmendmentElements(page);

        potentialAmendmentElements.forEach(amendmentElement -> {
            Amendment amendment = getAmendment(amendmentElement);
            if (amendment != null) {
                amendment.setDataRecord(record);
                amendments.add(amendment);
            }
        });

        return amendments;
    }

    private List<Element> getPotentialAmendmentElements(Element page) {
        return page.select("div.tab-content").first().select("div.tab-pane")
                .stream()
                .map(tabContent -> tabContent.getElementsByTag("h4").first())
                .filter(Objects::nonNull)
                .map(Element::parent)
                .filter(Objects::nonNull)
                .filter(h4 -> !h4.text().contains("SEIAS") && !h4.text().toLowerCase().contains("act")).toList();
    }

    private Amendment getAmendment(Element amendmentElement) {
        Amendment amendment = new Amendment();

        Element amendmentPdfElement = amendmentElement.getElementsByTag("a").first();
        String amendmentId = amendmentElement.getElementsByTag("h4").first().text();
        String amendmentTextUrl;
        Optional<String> amendmentText;

        amendmentTextUrl = amendmentPdfElement.attr("href");
        amendmentText = getAmendmentText(amendmentTextUrl);

        if (amendmentText.isPresent()) {
            if (amendmentText.get().length() >= 200 &&
                    amendmentText.get().substring(0, 200).toLowerCase().replaceAll("\\s", "")
                            .contains("amendmentsto")) {

                amendment.setAmendmentId(amendmentId);
                amendment.setTextSourceUrl(amendmentTextUrl);
                amendment.setAmendmentText(amendmentText.get());

                Optional<String> amendmentCommitteeAndPlenaryString = getAmendmentCommitteeAndPlenaryString(
                        amendmentText.get());
                if (amendmentCommitteeAndPlenaryString.isPresent()) {
                    getAmendmentCommittee(amendmentCommitteeAndPlenaryString.get())
                            .ifPresent(amendment::setCommitteeName);
                    amendment.setPlenary(getAmendmentPlenary(amendmentCommitteeAndPlenaryString.get()));
                }

                return amendment;
            }
        } else {
            log.error("Pdf text is too short to process: {}", amendmentTextUrl);
        }
        return null;
    }

    private Optional<String> getAmendmentText(String amendmentTextUrl) {
        return pdfParser.tryPdfTextExtraction(amendmentTextUrl);
    }

    private Optional<String> getAmendmentCommitteeAndPlenaryString(String amendmentText) {
        Optional<String> amendmentCommitteeAndPlenary = AMENDMENT_PATTERN_WITH_EXTRA_CLOSING_PARENTHESES
                .matcher(amendmentText.replace("\n", " "))
                .results()
                .map(MatchResult::group)
                .findFirst();

        if (amendmentCommitteeAndPlenary.isEmpty()) {
            amendmentCommitteeAndPlenary = AMENDMENT_PATTERN
                    .matcher(amendmentText.replace("\n", " "))
                    .results()
                    .map(MatchResult::group)
                    .findFirst();
        }

        if (amendmentCommitteeAndPlenary.isEmpty()) {
            amendmentCommitteeAndPlenary = AMENDMENT_PATTERN_WITH_MISSING_CLOSING_PARENTHESES
                    .matcher(amendmentText.replace("\n", " "))
                    .results()
                    .map(MatchResult::group)
                    .findFirst();
        }

        return amendmentCommitteeAndPlenary;
    }

    private Optional<String> getAmendmentCommittee(String amendmentCommitteeAndPlenaryString) {
        Optional<String> amendmentCommitteeName = AMENDMENT_COMMITTEE_PATTERN
                .matcher(amendmentCommitteeAndPlenaryString)
                .results()
                .map(committeeName -> committeeName.group(1))
                .findFirst()
                .map(a -> a.trim().replace(")", ""));

//        In this case the opening parentheses of the plenary is missing, so we can only collect committee name
        if (amendmentCommitteeName.isEmpty()) {
            amendmentCommitteeName = AMENDMENT_COMMITTEE_PATTERN_WITH_MISSING_PLENARY
                    .matcher(amendmentCommitteeAndPlenaryString)
                    .results()
                    .map(committeeName -> committeeName.group(1))
                    .findFirst();
        }

        return amendmentCommitteeName;
    }

    private Amendment.Plenary getAmendmentPlenary(String amendmentCommitteeAndPlenaryString) {
        Optional<String> amendmentPlenary;

        amendmentPlenary = AMENDMENT_PLEANRY_PATTERN
                .matcher(amendmentCommitteeAndPlenaryString)
                .results()
                .map(plenary -> plenary.group(2))
                .findFirst();

        // The plenary string can be found between parentheses
        String plenaryPattern = "\\((.*?)\\)";
        if (amendmentPlenary.isPresent()) {
            if (amendmentPlenary.get().replaceAll(plenaryPattern, "$1")
                    .equalsIgnoreCase("National Assembly")) {
                return Amendment.Plenary.LOWER;
            } else if (amendmentPlenary.get().replaceAll(plenaryPattern, "$1")
                    .equalsIgnoreCase("National Council of Provinces")) {
                return Amendment.Plenary.UPPER;
            }
        }
        return null;
    }

    private void deleteConnectingAmendts(LegislativeDataRecord record) {
        if (!record.getAmendments().isEmpty()) {
            legislativeDataRepository.deleteConnectingAmendments(record.getId());
        }
    }
}
