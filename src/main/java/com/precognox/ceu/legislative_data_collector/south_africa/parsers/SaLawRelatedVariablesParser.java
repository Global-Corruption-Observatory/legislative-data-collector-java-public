package com.precognox.ceu.legislative_data_collector.south_africa.parsers;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.sa.SouthAfricaCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.south_africa.SaPageCollector;
import com.precognox.ceu.legislative_data_collector.south_africa.SaPageType;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.precognox.ceu.legislative_data_collector.utils.DateUtils.parseSouthAfricaAffectingLawDate;
import static com.precognox.ceu.legislative_data_collector.south_africa.SaPageCollector.SOUTH_AFRICA_GOVERNMENT_PAGE_URL;

@Slf4j
@Service
@AllArgsConstructor
public class SaLawRelatedVariablesParser {
    private static final Pattern LAW_ID_PATTERN = Pattern.compile("Act \\d{1,3} of \\d{4}");
    private static final Pattern PDF_LAW_ID_PATTERN = Pattern.compile("Act No. \\d{1,3} of \\d{4}");
    private static final Pattern AFFECTING_LAW_FIRST_DATE_PATTERN = Pattern.compile("\\b\\d{1,2}\\s+\\w+\\s+\\d{4}\\b");
    private static Optional<PageSource> governmentLawPageSource = Optional.empty();
    private final SaPageCollector saPageCollector;
    private final PageSourceRepository pageSourceRepository;
    private final PrimaryKeyGeneratingRepository recordRepository;
    private final PdfParser pdfParser;

    private static String recoverGovernmentWebsiteUrl(String url) {
        if (!url.startsWith("https://www.gov.za/documents") && !url.contains("sites/default/files")) {
            log.warn("Invalid government website url: {}", url);

            Pattern GOVERNMENT_URL_PATTERN = Pattern.compile("/documents(.*)");
            Matcher matcher = GOVERNMENT_URL_PATTERN.matcher(url);

            if (matcher.find()) {
                url = SOUTH_AFRICA_GOVERNMENT_PAGE_URL + matcher.group(0);
            } else {
                throw new MalformedParameterizedTypeException("Unable to recover government website url: " + url);
            }
        }
        return url;
    }

    @Transactional
    public void parseAllPages() {
        log.info("Querying unprocessed pages...");

        recordRepository.streamUnprocessedLaws(Country.SOUTH_AFRICA)
                .forEach(record -> {
                    log.info("Processing law data for bill page: " + record.getBillPageUrl());
                    recordRepository.mergeInNewTransaction(parsePage(record));
                });
    }

    public LegislativeDataRecord parsePage(LegislativeDataRecord record) {
        PageSource source = pageSourceRepository.getByPageUrl(record.getBillPageUrl());
        Element page = Jsoup.parse(source.getRawSource()).body();

        SouthAfricaCountrySpecificVariables southAfricaCountrySpecificVariables =
                new SouthAfricaCountrySpecificVariables();

        LegislativeDataRecord.BillStatus billStatus = parseBillStatus(page);
        record.setBillStatus(billStatus);

        if (LegislativeDataRecord.BillStatus.PASS.equals(billStatus)) {
            record.setDatePassing(parseDatePassing(page));
            record.setDateEnteringIntoForce(parseDateEnteringIntoForce(page));

            record.setLawId(fixLaxIdFormat(parseLawId(page)));

            Optional<String> lawTextUrl = parseLawTextUrl(record.getLawId(), page);
            if (lawTextUrl.isPresent()) {
                record.setLawTextUrl(lawTextUrl.get());
                parseLawText(pdfParser, lawTextUrl.get())
                        .ifPresent(record::setLawText);
            }

            if (record.getLawId() == null && record.getLawText() != null) {
                String lawId = Optional.ofNullable(alternativeParseLawId(
                                record.getLawText(),
                                "Act No. \\d{1,3} of \\d{4}"))
                        .orElse(alternativeParseLawId(
                                record.getLawText(),
                                "No(?:\\.? \\d{1,3} of \\d{4}| \\d{1,3}, \\d{4})"));

                record.setLawId(fixLaxIdFormat(lawId));
            }

            List<String> affectingLaws = parseAffectingLaws(record.getLawId());
            if (!affectingLaws.isEmpty()) {
                record.setAffectingLawsCount(affectingLaws.size());
                record.setAffectingLawsFirstDate(
                        parseAffectingLawFirstDate(affectingLaws));
            }

//          It can happen that the law_id collected from the bill page as described in the annotation is incorrect.
//          It is indicated by the affecting_laws_first_date is earlier than the date_introduction.
//          This case can only be determined after collecting the affecting_laws with the incorrect law_id.
//          Solution: Re-parse the law_id from the law text and re-collect the affecting_laws.
            if (record.getAffectingLawsFirstDate() != null && record.getDateIntroduction() != null &&
                    record.getAffectingLawsFirstDate().isBefore(record.getDateIntroduction())) {
                record.setLawId(fixLaxIdFormat(
                        alternativeParseLawId(record.getLawText(),
                                "No(?:\\.? \\d{1,3} of \\d{4}| \\d{1,3}, \\d{4})")));

                affectingLaws = parseAffectingLaws(record.getLawId());
                if (!affectingLaws.isEmpty()) {
                    record.setAffectingLawsCount(affectingLaws.size());
                    record.setAffectingLawsFirstDate(
                            parseAffectingLawFirstDate(affectingLaws));
                }
            }

            parseGovPageUrl(record.getLawId())
                    .ifPresent(southAfricaCountrySpecificVariables::setGovPageUrl);

            parseLawTitle(record.getLawId())
                    .ifPresent(southAfricaCountrySpecificVariables::setLawTitle);

            Set<String> modifiedLaws = parseModifiedLaws(record.getLawId());

            record.setModifiedLaws(modifiedLaws);
            record.setModifiedLawsCount(modifiedLaws.size());
        }
        record.setSouthAfricaCountrySpecificVariables(southAfricaCountrySpecificVariables);

        return record;
    }

    public LegislativeDataRecord.BillStatus parseBillStatus(Element page) {
        Element billStatusElement = Optional.ofNullable(page.select("ul[class*=bill-versions] + p").first())
                .orElse(page.select("ul[class*=bill-versions] + div[class*=bill-status-container] + p")
                        .first());

        Optional<String> billStatus = Optional.ofNullable(billStatusElement)
                .map(Element::text);

        if (billStatus.isPresent()) {
            switch (billStatus.get()) {
                case "The bill has been signed into law",
                     "The bill has been signed into law.",
                     "Act commenced", "Act-partly-commenced",
                     "Act partially commenced" -> {
                    return LegislativeDataRecord.BillStatus.PASS;
                }
                case "Withdrawn", "Lapsed", "Rejected" -> {
                    return LegislativeDataRecord.BillStatus.REJECT;
                }
                default -> {
                    return LegislativeDataRecord.BillStatus.ONGOING;
                }
            }
        } else {
            log.warn("Bill status not found");
            return null;
        }
    }

    public LocalDate parseDatePassing(Element page) {

        Optional<Element> lastBillStatusSection = Optional.ofNullable(page.select("div.NCOP, div.NA").last());
        Optional<String> datePassing = getDatePassing(lastBillStatusSection);

        return datePassing.map(DateUtils::parseSouthAfricaDate).orElse(null);
    }

    private Optional<String> getDatePassing(Optional<Element> lastBillStatusSection) {
        return lastBillStatusSection
                .map(div -> div.select("div:contains(Bill passed)").last())
                .map(Element::previousElementSibling)
                .map(Element::text);
    }

    public LocalDate parseDateEnteringIntoForce(Element page) {
        Optional<String> dateEnteringIntoForce;

        dateEnteringIntoForce = Optional.ofNullable(page.select("div.president").first())
                .map(div -> div.select("div:contains(Act commenced)").last())
                .map(Element::previousElementSibling)
                .map(Element::text);

        if (dateEnteringIntoForce.isEmpty()) {
            dateEnteringIntoForce = page.select("div.bill-version-content").first().
                    getElementsByTag("a")
                    .stream()
                    .map(Element::text)
                    .filter(act -> act.toLowerCase().contains("act"))
                    .findFirst()
                    .map(date -> date.replaceAll(".*\\|\\s*", ""));
        }

        return dateEnteringIntoForce.map(DateUtils::parseSouthAfricaDate).orElse(null);
    }

    public Optional<Element> getLawIdElement(Element page) {
        Optional<Element> tabContentDiv = Optional.ofNullable(page.select("div.tab-content").first());
        if (tabContentDiv.isPresent()) {
            return tabContentDiv
                    .stream()
                    .map(tabContent -> tabContent.getElementsByTag("h4").first())
                    .filter(Objects::nonNull)
                    .map(Element::parent)
                    .filter(Objects::nonNull)
                    .filter(text -> text.text().toLowerCase().contains("act"))
                    .findFirst();

        } else {
            log.error("Law id element not found");
        }

        return Optional.empty();
    }

    public String parseLawId(Element page) {
        Optional<Element> lawIdElement = getLawIdElement(page);
        String lawId = null;
        Optional<String> formattedLawId = Optional.empty();

        if (lawIdElement.isPresent()) {
            lawId = lawIdElement.get().getElementsByTag("h4").first().text();

            formattedLawId = LAW_ID_PATTERN
                    .matcher(lawId)
                    .results()
                    .map(MatchResult::group)
                    .findFirst();
        }

        if (formattedLawId.isEmpty()) {
//            Law id should be parsed from the office of the president section
            Optional<Element> presidentOfficeSectionElement = Optional.ofNullable(
                    page.select("div[class*=bill-location president]").last());

            if (presidentOfficeSectionElement.isPresent()) {
                lawId = presidentOfficeSectionElement.get().text();
            }

            formattedLawId = Optional.of(LAW_ID_PATTERN
                            .matcher(lawId)
                            .results()
                            .map(MatchResult::group)
                            .findFirst())
                    .orElse(PDF_LAW_ID_PATTERN
                            .matcher(lawId)
                            .results()
                            .map(MatchResult::group)
                            .findFirst());
        }

        return formattedLawId.map(s -> s.replace("Act ", "").replace("No. ", ""))
                .orElse(null);
    }

    public String alternativeParseLawId(String lawText, String pattern) {
        Optional<String> lawId = Pattern.compile(pattern)
                .matcher(lawText)
                .results()
                .map(MatchResult::group)
                .findFirst();

        return lawId.map(s -> s.replace("Act No. ", "")
                .replace("No. ", "")
                .replace("No ", "")
                .replace(", ", " of ")).orElse(null);
    }

    public List<String> parseAffectingLaws(String lawId) {
        Optional<PageSource> pageSource = getPageSource(lawId);
        List<String> affectingLaws = new ArrayList<>();

        if (pageSource.isPresent()) {
            Element page = Jsoup.parse(pageSource.get().getRawSource()).body();

            Optional<String> affectingLawsList = Optional.ofNullable(
                            page.select("h2:contains(Amendments), h3:contains(Amendments)").first())
                    .map(Element::nextElementSibling)
                    .map(Element::nodeName)
                    .filter(ul -> ul.equals("ul"));

            if (affectingLawsList.isPresent()) {
                affectingLaws = page.select("a:contains(Amended by)").stream()
                        .filter(Objects::nonNull)
                        .filter(a -> a.parentNode().nodeName().equals("li"))
                        .map(a -> a.attr("href")).toList();
            } else {
                affectingLaws = page.select("a:contains(Amended by)").stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.attr("href")).toList();
            }
        }
        return affectingLaws;
    }

    public LocalDate parseAffectingLawFirstDate(List<String> affectingLaws) {
        String firstAffectingLawUrl = affectingLaws.get(affectingLaws.size() - 1);
        if (!firstAffectingLawUrl.contains(SOUTH_AFRICA_GOVERNMENT_PAGE_URL)) {
            firstAffectingLawUrl = SOUTH_AFRICA_GOVERNMENT_PAGE_URL + firstAffectingLawUrl;
        }

        Optional<Element> page = getPageOrDownload(firstAffectingLawUrl);

        return page.map(this::getAffectingLawFirstDate).orElse(null);
    }

    private LocalDate getAffectingLawFirstDate(Element page) {
        Optional<Element> affectingLawFirstDateElement = Optional.ofNullable(
                page.select("h2:contains(Commencement), h3:contains(Commencement)").first());

        Optional<String> affectingLawFirstDateString = affectingLawFirstDateElement
                .map(Element::nextElementSibling)
                .map(Element::text);

        if (affectingLawFirstDateElement.isPresent()) {
            Optional<String> affectingLawFirstDate = AFFECTING_LAW_FIRST_DATE_PATTERN
                    .matcher(affectingLawFirstDateString.get())
                    .results()
                    .map(MatchResult::group)
                    .findFirst();

            if (affectingLawFirstDate.isPresent()) {
                return parseSouthAfricaAffectingLawDate(affectingLawFirstDate.get());
            }
        }

        return null;
    }

    public Optional<String> parseLawTitle(String lawId) {
        governmentLawPageSource = getPageSource(lawId);

        if (governmentLawPageSource.isPresent()) {
            Element page = Jsoup.parse(governmentLawPageSource.get().getRawSource()).body();
            return Optional.of(page.getElementsByTag("h1").text().replaceAll("\\(.*?\\)", ""));
        }
        return Optional.empty();
    }

    public Optional<String> parseGovPageUrl(String lawId) {
        return pageSourceRepository.findPageSourceByLawId(SaPageType.ACT.name(), lawId)
                .map(PageSource::getPageUrl);
    }

    public Optional<String> parseLawTextUrl(String lawId, Element page) {
        Optional<String> lawTextUrl = Optional.empty();
        if (lawId != null) {
            governmentLawPageSource = getPageSource(lawId);

            if (governmentLawPageSource.isPresent()) {
                page = Jsoup.parse(governmentLawPageSource.get().getRawSource()).body();

                lawTextUrl = Optional.ofNullable(page.getElementsByTag("table").first())
                        .filter(th -> th.getElementsByTag("th").first().text().equals("Attachment"))
                        .map(td -> td.getElementsByTag("td").first())
                        .map(a -> a.getElementsByTag("a").first().attr("href"));
            }
        } else {
            lawTextUrl = getLawIdElement(page).map(element -> element.getElementsByTag("a").first())
                    .map(url -> url.attr("href"));
        }

        lawTextUrl = lawTextUrl.map(url -> {
            if (url.startsWith("/sites/default/files/")) {
                return "https://www.gov.za/" + url;
            }
            return url;
        });

        return lawTextUrl;
    }

    public Optional<String> parseLawText(PdfParser pdfParser, String lawTextUrl) {
        return pdfParser.tryPdfTextExtraction(lawTextUrl);
    }

    private Optional<PageSource> getPageSource(String lawId) {
        return pageSourceRepository.findPageSourceByLawId(SaPageType.ACT.name(), lawId);
    }

    public Set<String> parseModifiedLaws(String lawId) {
        Set<String> modifiedLaws = new HashSet<>();
        if (governmentLawPageSource.isEmpty()) {
            governmentLawPageSource = getPageSource(lawId);
        }

        if (governmentLawPageSource.isPresent()) {
            Element page = Jsoup.parse(governmentLawPageSource.get().getRawSource()).body();
            page.select("li:contains(to amend the)").stream()
                    .map(li -> li.getElementsByTag("a").first())
                    .filter(Objects::nonNull)
                    .map(a -> a.attr("href")).toList()
                    .forEach(url -> {
                        if (url != null && !url.contains("http://") && !url.contains(".pdf")) {
                            String modifiedLawId = parseModifiedLawId(url);
                            if (modifiedLawId != null) {
                                modifiedLaws.add(modifiedLawId);
                            }
                        }
                    });
        }
        return modifiedLaws;
    }

    private String parseModifiedLawId(String url) {
        url = recoverGovernmentWebsiteUrl(url);

        return Optional.of(url)
                .map(this::getPageOrDownload)
                .filter(Optional::isPresent)
                .map(page -> page.get().getElementsByTag("h1").text())
                .map(LAW_ID_PATTERN::matcher)
                .stream()
                .flatMap(Matcher::results)
                .findFirst()
                .map(MatchResult::group)
                .orElse(null);
    }

    private Optional<Element> getPageOrDownload(String firstAffectingLawUrl) {
        Optional<PageSource> pageSource = pageSourceRepository.findByPageUrl(firstAffectingLawUrl);

        if (pageSource.isEmpty()) {
            saPageCollector.downloadPage(firstAffectingLawUrl, SaPageType.ACT);
            pageSource = pageSourceRepository.findByPageUrl(firstAffectingLawUrl);
        }

        if (pageSource.isPresent()) {
            return Optional.of(Jsoup.parse(pageSource.get().getRawSource()).body());
        } else {
            log.error("Page not found: {}", firstAffectingLawUrl);
            return Optional.empty();
        }
    }

    private String fixLaxIdFormat(String lawId) {
        // The law id may be null if it is not found on the page or in the law text
        // The law id should be stored in Act XXX of YYYY format
        if (lawId != null && !lawId.toLowerCase().contains("act ")) {
            return "Act " + lawId;
        }
        return lawId;
    }
}