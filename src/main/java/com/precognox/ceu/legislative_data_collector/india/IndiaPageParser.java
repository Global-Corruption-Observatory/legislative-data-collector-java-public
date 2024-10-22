package com.precognox.ceu.legislative_data_collector.india;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.india.entities.IndiaCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.precognox.ceu.legislative_data_collector.india.Constants.CHROME_OPTS;
import static com.precognox.ceu.legislative_data_collector.india.Constants.START_PAGE;

@Slf4j
@Service
public class IndiaPageParser {

    private final PageSourceRepository pageSourceRepository;
    private final PrimaryKeyGeneratingRepository billRepository;
    private final BillAndLawTextDownloader billAndLawTextDownloader;

    private static final Pattern DATE_REGEX = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");
    private static final Map<String, OriginType> ORIGIN_TYPE_MAPPING =
            Map.of("Government", OriginType.GOVERNMENT, "Private Member", OriginType.INDIVIDUAL_MP);

    private static final Map<String, LegislativeDataRecord.BillStatus> STATUS_TEXT_MAPPING = Map.of(
            "Assented", LegislativeDataRecord.BillStatus.PASS,
            "Passed", LegislativeDataRecord.BillStatus.PASS,
            "Pending", LegislativeDataRecord.BillStatus.ONGOING,
            "Negatived", LegislativeDataRecord.BillStatus.REJECT,
            "Lapsed", LegislativeDataRecord.BillStatus.REJECT,
            "Withdrawn", LegislativeDataRecord.BillStatus.REJECT
    );

    @Autowired
    public IndiaPageParser(
            PageSourceRepository pageSourceRepository,
            PrimaryKeyGeneratingRepository billRepository,
            BillAndLawTextDownloader billAndLawTextDownloader) {
        this.billRepository = billRepository;
        this.pageSourceRepository = pageSourceRepository;
        this.billAndLawTextDownloader = billAndLawTextDownloader;
    }

    @Transactional
    public void processStoredPages() {
        try (Stream<PageSource> pageStream = pageSourceRepository.streamAll(Country.INDIA)) {
            pageStream.flatMap(this::processPage)
                    .peek(record -> log.info("Processed record {}", record.getRecordId()))
                    .forEach(billRepository::save);
        }

        collectCategories();
        billAndLawTextDownloader.downloadBillAndLawTexts();
    }

    /**
     * Processes a list of bills from a stored page (which is the result of the search function on the original
     * website.
     *
     * @param pageSource A stored {@link PageSource}
     *
     * @return A stream of bills on the page.
     */
    public Stream<LegislativeDataRecord> processPage(PageSource pageSource) {
        log.info("Processing page: {}", pageSource.getMetadata());

        try {
            Document parsedPage = Jsoup.parse(pageSource.getRawSource());
            Element table = parsedPage.body().selectFirst("table#ContentPlaceHolder1_GR1");

            if (table != null) {
                Elements rows = table.getElementsByTag("tr");

                Stream<LegislativeDataRecord> bills = rows.stream()
                        .skip(3)
                        .map(this::processBill)
                        .filter(Optional::isPresent)
                        .map(Optional::get);

                return bills;
            } else {
                log.warn("Table not found on page: {}", pageSource.getMetadata());
            }
        } catch (Exception e) {
            log.error("Error processing page", e);
        }

        return Stream.empty();
    }

    private Optional<LegislativeDataRecord> processBill(Element tableRow) {
        Elements cells = tableRow.getElementsByTag("td");
        String year = cells.get(0).text();
        String billNo = cells.get(1).text();
        String billId = year + "/" + billNo;
        String title = cells.get(2).selectFirst("a").text();

        if (billRepository.existsByCountryAndBillIdAndBillTitle(Country.INDIA, billId, title)) {
            log.info("Skipping already processed bill: {} - {}", billId, title);
            return Optional.empty();
        }

        LegislativeDataRecord result = new LegislativeDataRecord();
        result.setCountry(Country.INDIA);

        String originTypeStr = cells.get(3).text();
        String dateIntroStr = cells.get(5).text();
        String datePassingStr = cells.size() > 9 ? cells.get(9).text() : null;

        result.setBillId(billId);
        result.setBillTitle(title);
        result.setOriginType(ORIGIN_TYPE_MAPPING.get(originTypeStr));
        parseDateFromCell(dateIntroStr).ifPresent(result::setDateIntroduction);
        parseDateFromCell(datePassingStr).ifPresent(result::setDatePassing);
        parseOriginator(cells).ifPresent(originator -> result.setOriginators(List.of(originator)));

        collectStageDebates(cells, result);
        collectCountrySpecVars(cells, result);

        if (!result.getStages().isEmpty()) {
            result.setStagesCount(result.getStages().size());
        }

        result.setOriginalLaw(isOriginalLaw(result));

        //bill title cell
        Elements pdfLinks = cells.get(2).getElementsByTag("a");
        pdfLinks.stream()
                .filter(a -> "As introduced".equalsIgnoreCase(a.text()))
                .map(a -> a.attr("href"))
                .findAny()
                .ifPresent(result::setBillTextUrl);

        if (result.getBillTextUrl() != null) {
            result.setBillPageUrl(result.getBillTextUrl());
        }

        pdfLinks.stream()
                .filter(a -> "As Passed by both Houses".equalsIgnoreCase(a.text().trim()))
                .map(a -> a.attr("href"))
                .findAny()
                .ifPresent(result::setLawTextUrl);

        if (result.getLawTextUrl() == null) {
            //"assent date" cell
            pdfLinks = cells.get(9).getElementsByTag("a");

            if (!pdfLinks.isEmpty()) {
                Optional<String> pdfLink = pdfLinks.stream()
                        .map(e -> e.attr("href"))
                        .filter(link -> link.endsWith(".pdf"))
                        .findFirst();

                pdfLink.ifPresent(result::setLawTextUrl);
            }
        }

        log.info("Processed bill: {}", result.getBillId());

        return Optional.of(result);
    }

    private void collectStageDebates(Elements cells, LegislativeDataRecord result) {
        if (cells.size() > 6) {
            String dateStage1aText = cells.get(6).text();
            String dateStage1aDebateLink = cells.get(6).getElementsByTag("a").attr("href");

            parseDateFromCell(dateStage1aText).ifPresent(
                    date -> {
                        LegislativeStage stg = new LegislativeStage(1, date, "Debate/Passed in LS");
                        collectStageDebateSizes(dateStage1aDebateLink).ifPresent(stg::setDebateSize);
                        result.getStages().add(stg);
                    });
        }

        if (cells.size() > 7) {
            String dateStage1bText = cells.get(7).text();
            String dateStage1bDebateLink = cells.get(7).getElementsByTag("a").attr("href");

            parseDateFromCell(dateStage1bText).ifPresent(
                    date -> {
                        LegislativeStage stg = new LegislativeStage(2, date, "Debate/Passed in RS");
                        collectStageDebateSizes(dateStage1bDebateLink).ifPresent(stg::setDebateSize);
                        result.getStages().add(stg);
                    });
        }
    }

    private void collectCountrySpecVars(Elements cells, LegislativeDataRecord result) {
        if (cells.size() > 11) {
            String status = cells.get(11).text();
            result.setBillStatus(STATUS_TEXT_MAPPING.getOrDefault(status, null));

            IndiaCountrySpecificVariables specVars = getSpecVars(status);
            specVars.setLegislativeDataRecord(result);
            result.setIndiaCountrySpecificVariables(specVars);
        }
    }

    private boolean isOriginalLaw(LegislativeDataRecord result) {
        return !result.getBillTitle().toLowerCase().contains("amendment");
    }

    private IndiaCountrySpecificVariables getSpecVars(String status) {
        IndiaCountrySpecificVariables specVars = new IndiaCountrySpecificVariables();

        if ("Withdrawn".equals(status)) {
            specVars.setWithdrawn(Boolean.TRUE);
        } else if (StringUtils.isNotEmpty(status)) {
            specVars.setWithdrawn(Boolean.FALSE);
        }

        if ("Lapsed".equals(status)) {
            specVars.setLapsed(Boolean.TRUE);
        } else if (StringUtils.isNotEmpty(status)) {
            specVars.setLapsed(Boolean.FALSE);
        }

        return specVars;
    }

    private Optional<LocalDate> parseDateFromCell(String cellContent) {
        if (cellContent == null) {
            return Optional.empty();
        }

        return DATE_REGEX
                .matcher(cellContent)
                .results()
                .findFirst()
                .map(MatchResult::group)
                .map(dateStr -> {
                    String[] parts = dateStr.split("/");

                    return LocalDate.of(
                            Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0])
                    );
                });
    }

    /**
     * Parses originators using the filter on the website.
     */
    private void collectOriginatorsOld() {
        ChromeDriver br = new ChromeDriver(CHROME_OPTS);

        try {
            br.get(START_PAGE);
            br.findElement(By.cssSelector("input[value='both']")).click();
            br.findElement(By.cssSelector("input[value='Private Member']")).click();
            br.findElement(By.cssSelector("input[value='6']")).click();

            Select memberSelector = new Select(br.findElement(By.id("ContentPlaceHolder1_ddlMember")));

            List<String> members = memberSelector.getOptions()
                    .stream()
                    .map(WebElement::getText)
                    .toList();

            log.info("Found {} originators", members.size());

            //skip first option
            for (int i = 1; i < members.size(); i++) {
                memberSelector.selectByIndex(i);
                Originator currentOriginator = new Originator(members.get(i));
                br.findElement(By.cssSelector("input[value='Submit']")).click();

                try {
                    List<WebElement> rows = br.findElement(By.cssSelector("table#ContentPlaceHolder1_GR1"))
                            .findElements(By.tagName("tr"));

                    rows.stream()
                            .skip(1)
                            .map(this::parseBillIdFromRow)
                            .filter(Optional::isPresent)
                            .map(billId -> billRepository.findByCountryAndBillId(Country.INDIA, billId.get()))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .peek(storedBill -> storedBill.getOriginators().add(currentOriginator))
                            .peek(storedBill -> log.info("Stored originator for record: {}", storedBill.getRecordId()))
                            .forEach(billRepository::merge);
                } catch (NoSuchElementException e) {
                    log.warn("Bill table missing for originator: {}", currentOriginator.getName());
                }

                //avoid stale element ref.
                memberSelector = new Select(br.findElement(By.id("ContentPlaceHolder1_ddlMember")));
            }
        } finally {
            br.close();
        }
    }

    private Optional<String> parseBillIdFromRow(WebElement row) {
        List<WebElement> cells = row.findElements(By.tagName("td"));

        if (cells.isEmpty() || "head-style".equals(row.getAttribute("class"))) {
            return Optional.empty();
        }

        return Optional.of(cells.get(0).getText() + "/" + cells.get(1).getText());
    }

    @Transactional
    public void collectCategories() {
        try (Stream<PageSource> storedPages = pageSourceRepository.streamByCountryAndPageType(
                Country.INDIA, PageType.BILL_CATEGORY_EXPORT.name())) {
            storedPages.forEach(page -> {
                log.info("Processing page for category: {}", page.getMetadata());

                Document parsedPage = Jsoup.parse(page.getRawSource());
                Elements rows = parsedPage.body().getElementsByTag("tr");

                rows.stream()
                        .skip(2)
                        .map(row -> row.getElementsByTag("td"))
                        .map(cells -> cells.get(0).text() + "/" + cells.get(1).text())
                        .map(billId -> billRepository.findByCountryAndBillId(Country.INDIA, billId))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .peek(storedBill -> storedBill.setBillType(page.getMetadata()))
                        .forEach(billRepository::merge);
            });
        }
    }

    @Transactional
    public void collectAffectingLawsOld() {
        log.info("Collecting affecting laws...");

        Map<String, Integer> affectingLawCounts = new HashMap<>();
        Map<String, LocalDate> affectingLawFirstDates = new HashMap<>();

        billRepository.streamAll(Country.INDIA)
                .filter(bill -> !bill.getOriginalLaw())
                .filter(bill -> bill.getLawText() != null || bill.getBillText() != null)
                .forEach(bill -> {
                    String textToCheck = bill.getLawText() != null ? bill.getLawText() : bill.getBillText();
                    textToCheck = textToCheck.length() > 1000 ? textToCheck.substring(0, 1000) : textToCheck;

                    List<MatchResult> modifiedLawMatches =
                            amendedLawReferenceRegex.matcher(textToCheck).results().toList();

                    if (modifiedLawMatches.size() == 1) {
                        //find candidates by bill title and date_passing
                        String matchingBillTitle = modifiedLawMatches.get(0).group(1).trim().toLowerCase();
                        int matchingBillYear = Integer.parseInt(modifiedLawMatches.get(0).group(2).trim());

                        List<LegislativeDataRecord> candidates =
                                billRepository.findByCountryAndBillNamePart(Country.INDIA, matchingBillTitle)
                                .stream()
                                .filter(LegislativeDataRecord::getOriginalLaw)
                                .filter(candidate -> candidate.getDatePassing() != null)
                                .filter(candidate -> candidate.getDatePassing().getYear() == matchingBillYear)
                                .toList();

                        if (candidates.size() == 1) {
                            LegislativeDataRecord modifiedBill = candidates.get(0);
                            bill.getModifiedLaws().add(modifiedBill.getBillId());

                            affectingLawCounts.merge(modifiedBill.getBillTitle(), 1, Integer::sum);

                            if (bill.getDatePassing() != null) {
                                affectingLawFirstDates
                                        .merge(modifiedBill.getBillTitle(), bill.getDatePassing(), ObjectUtils::min);
                            }
                        }
                    }
                });

        affectingLawCounts.forEach((billTitle, affLawsCount) -> {
            List<LegislativeDataRecord> matchingBills =
                    billRepository.findByCountryAndBillTitle(Country.INDIA, billTitle);

            if (matchingBills.size() == 1) {
                matchingBills.get(0).setAffectingLawsCount(affLawsCount);
                matchingBills.get(0).setAffectingLawsFirstDate(affectingLawFirstDates.get(billTitle));
            } else if (matchingBills.size() > 1) {
                log.warn("Bills with identical title: {}", billTitle);
            } else {
                log.warn("Bill not found with title: {}", billTitle);
            }
        });
    }

    private static final Pattern amendedLawReferenceRegex = Pattern.compile("to amend the(.+?)Act, (\\d\\d\\d\\d)");

    private Optional<Integer> collectStageDebateSizes(String url) {
        if (StringUtils.isEmpty(url)) {
            return Optional.empty();
        }

        try {
            Document page = Jsoup.connect(url).get();
            Element textDiv = page.body().getElementById("pnlDiv1");

            if (textDiv != null) {
                return Optional.of(TextUtils.getLengthWithoutWhitespace(textDiv.text()));
            }
        } catch (IOException e) {
            log.error("Can not get debate size, failed to connect to URL: " + url, e);
        }

        return Optional.empty();
    }

    private Optional<Originator> parseOriginator(Elements cells) {
        if (cells.size() > 3) {
            String origType = cells.get(3).text().trim();

            if (origType.contains("Private Member")) {
                String origName = cells.get(4).text().trim();

                Originator originator = new Originator();
                originator.setName(origName);

                return Optional.of(originator);
            }
        }

        return Optional.empty();
    }

}
