package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.common.BillAndLawTextCollector;
import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.swe.SwedenCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord.BillStatus;

@Slf4j
@Service
public class SwedenBillPageParser {

    private final PageSourceRepository pageSourceRepository;
    private final PrimaryKeyGeneratingRepository recordRepository;
    private final BillAndLawTextCollector billAndLawTextCollector;

    private static final Pattern BILL_ID_PATTERN = Pattern.compile("\\d{4}/\\d+:\\d+");
    private static final Pattern LAW_REFERENCE_PATTERN = Pattern.compile("\\d{4}:\\d+");

    @Autowired
    public SwedenBillPageParser(
            PageSourceRepository pageSourceRepository, PrimaryKeyGeneratingRepository recordRepository,
            BillAndLawTextCollector billAndLawTextCollector) {
        this.pageSourceRepository = pageSourceRepository;
        this.recordRepository = recordRepository;
        this.billAndLawTextCollector = billAndLawTextCollector;
    }

    @Transactional
    public void parseAllPages() {
        log.info("Querying unprocessed pages...");

        pageSourceRepository.streamUnprocessedBillPages(Country.SWEDEN).forEach(page -> {
            LegislativeDataRecord bill = parsePage(page);
            recordRepository.save(bill);

            log.info("Saved record {}", bill.getRecordId());
        });
    }

    @Transactional
    public void reprocessAllRecords() {
        recordRepository.streamAllWithoutDateProcessed(Country.SWEDEN).forEach(record -> {
            pageSourceRepository.findFirstByPageUrl(record.getBillPageUrl()).ifPresent(pageSource -> {
                parseRecordFields(pageSource, record);
                recordRepository.mergeInNewTransaction(record);

                log.info("Updated record {}", record.getRecordId());
            });
        });
    }

    public LegislativeDataRecord parsePage(PageSource source) {
        LegislativeDataRecord record = new LegislativeDataRecord(Country.SWEDEN);

        return parseRecordFields(source, record);
    }

    @NotNull
    private LegislativeDataRecord parseRecordFields(PageSource pageSource, LegislativeDataRecord record) {
        Element page = Jsoup.parse(pageSource.getRawSource()).body();

        if (record.getSwedenCountrySpecificVariables() == null) {
            record.setSwedenCountrySpecificVariables(new SwedenCountrySpecificVariables());
            record.getSwedenCountrySpecificVariables().setLegislativeDataRecord(record);
        }

        record.setBillPageUrl(pageSource.getPageUrl());

        parseBillId(page).ifPresent(record::setBillId);
        parseBillTitle(page).ifPresent(record::setBillTitle);
        parseBillStatus(page).ifPresent(record::setBillStatus);
        parseDateIntro(page).ifPresent(record::setDateIntroduction);
        parseDatePassing(page).ifPresent(record::setDatePassing);
        parseBillTextUrl(pageSource.getPageUrl(), page).ifPresent(record::setBillTextUrl);
        parseLawTextUrl(page).ifPresent(record::setLawTextUrl);
        parseOriginType(pageSource.getPageUrl()).ifPresent(record::setOriginType);
        record.setCommittees(parseCommittee(page));
        record.setCommitteeCount(record.getCommittees().size());
        record.setOriginators(new OriginatorsParser().parseOriginators(page));
        parseOriginalLaw(page).ifPresent(record::setOriginalLaw);

        collectStages(page, record);

        Set<String> modifiedLaws = parseModifiedLaws(page);
        record.setModifiedLaws(modifiedLaws);
        record.setModifiedLawsCount(modifiedLaws.size());

        List<Amendment> amendments = parseAmendments(page);
        record.setAmendments(amendments);
        record.setAmendmentCount(amendments.size());
        record.getAmendments().forEach(am -> am.setDataRecord(record));

        parseAffectingLawsPageUrl(page).ifPresent(
                url -> record.getSwedenCountrySpecificVariables().setAffectingLawsPageUrl(url));

        parseForslagspunkterPageUrl(page).ifPresent(
                url -> record.getSwedenCountrySpecificVariables().setForslagspunkterPageUrl(url));

        record.setDateProcessed(LocalDateTime.now());

        return record;
    }

    private void collectStages(Element page, LegislativeDataRecord record) {
        List<LegislativeStage> stages = new ArrayList<>();
        parseStage1(page).ifPresent(stages::add);

        record.setStages(stages);
        record.setStagesCount(stages.size());
    }

    //needed for date_passing and date_committee
    private Optional<String> parseForslagspunkterPageUrl(Element page) {
        return getForslagspunkterDiv(page)
                .map(parentDiv -> parentDiv.getElementsByTag("a"))
                .filter(list -> !list.isEmpty())
                .map(links -> links.get(0))
                .map(link -> link.attr("href"));
    }

    @NotNull
    private static Optional<Element> getForslagspunkterDiv(Element page) {
        Element forslagspunkterHeader = page.selectFirst("h3:contains(Förslagspunkter)");

        return Optional.ofNullable(forslagspunkterHeader)
                .map(h3 -> h3.parents().get(3))
                .filter(e -> "div".equals(e.tagName()));
    }

    private Optional<String> parseAffectingLawsPageUrl(Element page) {
        return Optional.ofNullable(page.selectFirst("a:contains(SFSR (Regeringskansliet))"))
                .map(a -> a.attr("href"));
    }

    private Optional<OriginType> parseOriginType(String url) {
        if (url.contains("/dokument/motion/")) {
            return Optional.of(OriginType.INDIVIDUAL_MP);
        }

        if (url.contains("/dokument/proposition/")) {
            return Optional.of(OriginType.GOVERNMENT);
        }

        return Optional.empty();
    }

    private Optional<String> parseBillId(Element page) {
        Element billIdContainer = page.selectFirst("p[aria-roledescription='Underrubrik']");

        if (billIdContainer != null) {
            Optional<String> billId = BILL_ID_PATTERN
                    .matcher(billIdContainer.text())
                    .results()
                    .findFirst()
                    .map(MatchResult::group);

            if (billId.isPresent()) {
                return billId;
            }
        }

        //checking page text for bill ID if it's still not found
        return Optional.ofNullable(page.selectFirst("main"))
                .map(main -> main.selectFirst("a"))
                .map(link -> link.parents().get(3))
                .map(Element::text)
                .map(BILL_ID_PATTERN::matcher)
                .stream()
                .flatMap(Matcher::results)
                .findFirst()
                .map(MatchResult::group);
    }

    private Optional<String> parseBillTitle(Element page) {
        return Optional.ofNullable(page.selectFirst("h1")).map(Element::text);
    }

    private Optional<String> parseBillTextUrl(String currentUrl, Element page) {
        //get html link (same as the current page url + /html), check if exists first (returns 404 if not)
        String htmlLink = currentUrl.endsWith("/") ? currentUrl + "html/" : currentUrl + "/html/";
        HttpResponse<String> htmlResp = Unirest.head(htmlLink).asString();

        if (htmlResp.isSuccess()) {
            return Optional.of(htmlLink);
        }

        //get PDF, or DOC link otherwise
        return Optional.ofNullable(page.selectFirst("main"))
                .map(main -> main.selectFirst("a"))
                .map(firstLink -> firstLink.attr("href"));
    }

    private Optional<BillStatus> parseBillStatus(Element page) {
        Optional<Element> decisionsDiv = Optional.ofNullable(page.selectFirst("h3:contains(Yrkanden)"))
                .map(decisionsHeader -> decisionsHeader.parents().get(3));

        if (decisionsDiv.isPresent()) {
            Elements rejectionLabels = decisionsDiv.get().select("dd:contains(Avslag)");
            Elements approvalLabels = decisionsDiv.get().select("dd:contains(Bifall)");

            if (rejectionLabels.isEmpty() && approvalLabels.isEmpty()) {
                return Optional.of(BillStatus.ONGOING);
            }
        }

        boolean passed = decisionsDiv
                .map(div -> div.getElementsByTag("dd"))
                .stream()
                .flatMap(Collection::stream)
                .anyMatch(dd -> dd.text().contains("Bifall"));

        return passed ? Optional.of(BillStatus.PASS) : Optional.of(BillStatus.REJECT);
    }

    private Optional<LocalDate> parseDateIntro(Element page) {
        //get from Registrering label as the first option
        Optional<LocalDate> date = Optional.ofNullable(page.selectFirst("dt:contains(Registrering)"))
                .map(Element::nextElementSibling)
                .map(Element::text)
                .map(Utils::parseNumericDate);

        if (date.isPresent()) {
            return date;
        }

        //parse with regex from the bill text as the second option
        return Optional.ofNullable(page.selectFirst("main#content"))
                .map(Element::text)
                .map(BillTextParser.DATE_INTRO_REGEX::matcher)
                .stream()
                .flatMap(Matcher::results)
                .findFirst()
                .map(matchResult -> matchResult.group(1))
                .map(Utils::parseDateExpr);
    }

    private Optional<LocalDate> parseDatePassing(Element page) {
        return Optional.ofNullable(page.selectFirst("dt:contains(Motionstid slutar)"))
                .map(Element::nextElementSibling)
                .map(Element::text)
                .map(Utils::parseNumericDate);
    }

    private List<Committee> parseCommittee(Element page) {
        return Optional.ofNullable(page.selectFirst("dt:contains(Tilldelat)"))
                .map(Element::nextElementSibling)
                .filter(e -> "dd".equals(e.tagName()))
                .map(Element::text)
                .map(commName -> new Committee(commName, null))
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    @Nullable
    private Optional<Boolean> parseOriginalLaw(Element page) {
        Optional<String> text = getForslagspunkterDiv(page).map(Element::text);

        if (text.isPresent()) {
            if (text.get().contains("förslag till lag om ändring")) {
                return Optional.of(false);
            }

            if (text.get().contains("förslag till lag")) {
                return Optional.of(true);
            }
        }

        return Optional.empty();
    }

    private Optional<String> parseLawTextUrl(Element page) {
        return Optional.ofNullable(page.selectFirst("span:contains(Fulltext)"))
                .map(Element::parent)
                .map(link -> link.attr("href"));
    }

    private Optional<LegislativeStage> parseStage1(Element page) {
        return Optional.ofNullable(page.selectFirst("dt:contains(Inlämnad)"))
                .map(Element::nextElementSibling)
                .map(Element::text)
                .map(Utils::parseNumericDate)
                .map(date -> new LegislativeStage(1, date, "Inlämnad"));
    }

    private Set<String> parseModifiedLaws(Element page) {
        return getForslagspunkterDiv(page)
                .map(div -> div.getElementsByTag("li"))
                .stream()
                .flatMap(Collection::stream)
                .map(Element::text)
                .flatMap(text -> LAW_REFERENCE_PATTERN.matcher(text).results())
                .map(MatchResult::group)
                .collect(Collectors.toSet());
    }

    private List<Amendment> parseAmendments(Element page) {
        return Optional.ofNullable(page.selectFirst("h3:contains(Följdmotioner)"))
                .map(h3 -> h3.parents().get(3))
                .map(div -> div.getElementsByTag("a"))
                .stream()
                .flatMap(Elements::stream)
                .map(this::parseAmendmentObj)
                .toList();
    }

    private Amendment parseAmendmentObj(Element link) {
        Amendment amendment = new Amendment();
        amendment.setPageUrl(link.attr("href"));
        amendment.setTitle(link.text());

        Optional.ofNullable(link.nextElementSibling())
                .filter(e -> "span".equals(e.tagName()))
                .map(e -> BILL_ID_PATTERN.matcher(e.text()))
                .stream()
                .flatMap(Matcher::results)
                .findFirst()
                .map(MatchResult::group)
                .ifPresent(amendment::setAmendmentId);

        return amendment;
    }

    /**
     * Single-use method for one bugfix.
     */
    @Transactional
    public void print404BillTextUrls() {
        recordRepository.streamAllWithBillTextUrl(Country.SWEDEN).forEach(bill -> {
            try {
                HttpResponse resp = Unirest.head(bill.getBillTextUrl()).asEmpty();

                if (!resp.isSuccess()) {
                    System.out.println(
                            resp.getStatus() + " - " + bill.getBillTextUrl() + " - " + bill.getBillPageUrl());

                    HttpResponse<String> billPageResp = Unirest.get(bill.getBillPageUrl()).asString();

                    Optional<String> newUrl =
                            parseBillTextUrl(bill.getBillPageUrl(), Jsoup.parse(billPageResp.getBody()));

                    if (newUrl.isPresent() && !newUrl.get().equals(bill.getBillTextUrl())) {
                        System.out.println("Updating URL for bill: " + bill.getRecordId());

                        bill.setBillTextUrl(newUrl.get());
                        recordRepository.mergeInNewTransaction(bill);

                        billAndLawTextCollector.downloadBillText(bill);
                    } else {
                        System.out.println("URL not changed for bill: " + bill.getBillPageUrl());
                    }
                } else {
                    System.out.println("Checked bill: " + bill.getRecordId());
                }
            } catch (Exception e) {
                log.error("Error for URL: " + bill.getBillTextUrl());
                log.error(e.toString());
            }
        });
    }

    //for one bugfix
    @Transactional
    public void reprocessOriginators() {
        recordRepository.streamAllWithoutOriginators(Country.SWEDEN).forEach(record -> {
            pageSourceRepository.findFirstByPageUrl(record.getBillPageUrl()).ifPresent(pageSource -> {
                Element parsed = Jsoup.parse(pageSource.getRawSource()).body();
                List<Originator> origs = new OriginatorsParser().parseOriginators(parsed);

                if (!origs.isEmpty()) {
                    record.setOriginators(origs);
                    recordRepository.mergeInNewTransaction(record);
                    log.info("Updated record {} with {} originators", record.getRecordId(), origs.size());
                } else {
                    log.info("No originators found for record {}", record.getRecordId());
                }
            });
        });
    }

}
