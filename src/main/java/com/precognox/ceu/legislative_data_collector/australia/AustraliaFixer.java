package com.precognox.ceu.legislative_data_collector.australia;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.australia.AuCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.DocumentDownloader;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import kong.unirest.ContentType;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class AustraliaFixer {

    private final PrimaryKeyGeneratingRepository recordRepository;
    private final PageSourceLoader pageSourceLoader;
    private final PdfParser pdfParser;
    private final EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private WebDriver driver;
    @Autowired
    private DocumentDownloader documentDownloader;

    @Autowired
    public AustraliaFixer(PrimaryKeyGeneratingRepository recordRepository, PageSourceLoader pageSourceLoader,
                          PdfParser pdfParser, EntityManager entityManager) {
        this.recordRepository = recordRepository;
        this.pageSourceLoader = pageSourceLoader;
        this.pdfParser = pdfParser;
        this.entityManager = entityManager;
    }

    @Transactional
    public void fixAustraliaWebsite1Collection() {
        fixStages();
        fixCommittees();
        fixOriginators();
        fixBillSizes();
        parseActNumber();
        parseBillTexts();
//      Run the update query in the database to fix the date_introduction column
//        fixDateIntros();
    }

    @Transactional
    public void fixLawTexts() {
        recordRepository.streamAllWithLawTextError(Country.AUSTRALIA).forEach(this::fixLawText);
    }

    public void fixLawText(LegislativeDataRecord record) {
        try {
            String lawTextUrl = parseLawTextUrl(record.getBillPageUrl());

            if (lawTextUrl != null) {
                record.setLawTextUrl(lawTextUrl);
                recordRepository.mergeInNewTransaction(record);

                log.info("Updated record {} with URL {}", record.getRecordId(), lawTextUrl);
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }

    public String parseLawTextUrl(String billPageUrl) {
        HttpResponse<String> billPageResp = Unirest.get(billPageUrl).asString();

        if (billPageResp.isSuccess()) {
            Document parsed = Jsoup.parse(billPageResp.getBody());
            Element asPassedLink = parsed.body().selectFirst("a:contains(As passed by both)");

            if (asPassedLink != null) {
                Element tr = asPassedLink.parents().get(3);
                Elements links = tr.select("a");

                if (links.size() == 3) {
                    String lastLink = links.get(2).attr("href");
                    HttpResponse pdfPageResp = Unirest.head(lastLink).asEmpty();

                    if (pdfPageResp.isSuccess()) {
                        String cType = pdfPageResp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);

                        if ("application/pdf".equals(cType)) {
                            return lastLink;
                        } else if (ContentType.TEXT_HTML.getMimeType().equals(cType)) {
                            HttpResponse<String> secondPageResp = Unirest.get(links.get(0).attr("href")).asString();

                            if (secondPageResp.isSuccess()) {
                                Document parsed2 = Jsoup.parse(secondPageResp.getBody());
                                Element pdfLink = parsed2.body().selectFirst("a:contains(Download PDF)");

                                if (pdfLink != null) {
                                    String href = pdfLink.attr("href");

                                    if (!href.startsWith("http")) {
                                        href = "https://parlinfo.aph.gov.au" + href;
                                    }

                                    return href;
                                }
                            }
                        }
                    }
                }
            }
        } else {
            log.error("Error response for: {}", billPageUrl);
        }

        log.info("URL not found for bill: {}", billPageUrl);

        return null;
    }

    @Transactional
    public void fixStages() {
        log.info("Fixing stages...");

        recordRepository.streamAll(Country.AUSTRALIA).forEach(bill -> {
            int startingStageCount = bill.getStages().size();

            fixStages(bill);

            //only update if new stage was added
            if (startingStageCount < bill.getStages().size()) {
                bill.setStagesCount(bill.getStages().size());

                recordRepository.mergeInNewTransaction(bill);
                log.info("Updated record {}", bill.getRecordId());
            } else {
                log.info("No new stages for record {}", bill.getRecordId());
            }
        });
    }

    private final Pattern dateRegex = Pattern.compile("\\d{2} \\w{3} \\d{4}");
    private final DateTimeFormatter dateParser = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public void fixStages(LegislativeDataRecord bill) {
        //key: the stage number, second value: the stored stage name, third value: the label on the page
        Map<Integer, List<String>> houseStages = Map.of(
                1, List.of("House of Representatives - First reading", "read a first time"),
                2, List.of("House of Representatives - Second reading", "Second reading"),
                3, List.of("House of Representatives - Third reading", "Third reading")
        );

        Map<Integer, List<String>> senateStages = Map.of(
                4, List.of("Senate - First reading", "read a first time"),
                5, List.of("Senate - Second reading", "Second reading"),
                6, List.of("Senate - Third reading", "Third reading")
        );

        HttpResponse<String> billPageResp = Unirest.get(bill.getBillPageUrl()).asString();

        if (billPageResp.isSuccess()) {
            Element page = Jsoup.parse(billPageResp.getBody()).body();

            processStagesTable(bill, page, "House of Representatives", houseStages);
            processStagesTable(bill, page, "Senate", senateStages);
        }
    }

    private void processStagesTable(
            LegislativeDataRecord record,
            Element page,
            String tableHeader,
            Map<Integer, List<String>> stagesToCheck) {
        Element senateStagesHeader = page.selectFirst("th:contains(" + tableHeader + ")");

        if (senateStagesHeader != null) {
            Element senateStagesTable = senateStagesHeader.parents().get(2);

            if (senateStagesTable.tagName().equals("table")) {
                Elements rows = senateStagesTable.select("tr");

                stagesToCheck.forEach((stageNum, stageNameAndLabel) -> {
                    //filter the stages that are already there, by stage number
                    boolean alreadyStored = record.getStages().stream()
                            .map(LegislativeStage::getStageNumber)
                            .anyMatch(num -> num.equals(stageNum));

                    if (!alreadyStored) {
                        String stageName = stageNameAndLabel.get(0);
                        String label = stageNameAndLabel.get(1);

                        Optional<String> matchingRow = rows.stream()
                                .map(Element::text)
                                .filter(text -> text.contains(label))
                                .findFirst();

                        if (matchingRow.isPresent()) {
                            //text example: Third reading moved 01 Dec 2009
                            Optional<LocalDate> date = dateRegex.matcher(matchingRow.get())
                                    .results()
                                    .findFirst()
                                    .map(MatchResult::group)
                                    .map(text -> LocalDate.parse(text, dateParser));

                            if (date.isPresent()) {
                                LegislativeStage stg =
                                        new LegislativeStage(stageNum, date.get(), stageName);

                                record.getStages().add(stg);
                            }
                        }
                    }
                });
            }
        }
    }

    @Transactional
    public void fixCommittees() {
        log.info("Fixing committees...");
        //filter for committees which have null data, then get records from that
        String qlString = "select r from LegislativeDataRecord r " +
                "left join fetch r.committees c " +
                "where c.name is null";

        Stream<LegislativeDataRecord> records =
                entityManager.createQuery(qlString, LegislativeDataRecord.class).getResultStream();

        records.forEach(this::fixCommittees);
    }

    public void fixCommittees(LegislativeDataRecord record) {
        Pattern commRegex = Pattern.compile("Referred to Committee \\((.+)\\): (.+)");

        HttpResponse<String> resp = Unirest.get(record.getBillPageUrl()).asString();

        if (resp.isSuccess()) {
            Element page = Jsoup.parse(resp.getBody()).body();

            List<Committee> comms =
                    Optional.ofNullable(page.selectFirst("h3:contains(Notes)"))
                            .map(Element::nextElementSibling)
                            .filter(next -> "ul".equals(next.tagName()))
                            .map(ul -> ul.select("li"))
                            .stream()
                            .flatMap(Elements::stream)
                            .map(Element::text)
                            .map(commRegex::matcher)
                            .flatMap(Matcher::results)
                            .map(this::parseCommitteeLine)
                            .toList();

            if (!comms.isEmpty()) {
                record.setCommittees(comms);
                record.setCommitteeCount(comms.size());
                recordRepository.mergeInNewTransaction(record);

                log.info("Updated record {} with {} committees", record.getRecordId(), comms.size());
            } else {
                log.info("No committees found for record: {}", record.getBillPageUrl());
            }
        }
    }

    private Committee parseCommitteeLine(MatchResult matchResult) {
        String date = matchResult.group(1);
        String name = matchResult.group(2);

        LocalDate d = LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        return new Committee(name, null, d);
    }

    @Transactional
    public void fixOriginators() {
        log.info("Fixing originators...");

        String qlString = "select r from LegislativeDataRecord r where r.originators is empty";

        Stream<LegislativeDataRecord> records =
                entityManager.createQuery(qlString, LegislativeDataRecord.class).getResultStream();

        records.forEach(this::fixOriginators);
    }

    public void fixOriginators(LegislativeDataRecord record) {
        String textStart = "(Circulated by authority of";

        Optional<PageSource> source = pageSourceLoader.loadFromDbOrFetchWithHttpGet(
                Country.AUSTRALIA,
                "BILL",
                record.getBillPageUrl()
        );

        if (source.isPresent()) {
            Element page = Jsoup.parse(source.get().getRawSource()).body();
            Element emHeader = page.selectFirst("h3:contains(Explanatory memoranda)");

            if (emHeader != null) {
                Element table = emHeader.nextElementSibling();

                if (table != null && "table".equals(table.tagName())) {
                    Elements links = table.select("a");

                    Optional<Element> emLink = links.stream()
                            .filter(a -> "Explanatory memorandum".equalsIgnoreCase(a.text().trim()))
                            .findFirst();

                    if (emLink.isPresent()) {
                        Optional<PageSource> dlPageResp =
                                pageSourceLoader.loadFromDbOrFetchWithHttpGet(
                                        Country.AUSTRALIA, "EM_DOWNLOAD_PAGE", emLink.get().attr("href"));

                        if (dlPageResp.isPresent()) {
                            Document parsed = Jsoup.parse(dlPageResp.get().getRawSource());
                            Element pdfLink = parsed.body().selectFirst("a:contains(Download PDF)");

                            if (pdfLink != null) {
                                String pdfUrl = "https://parlinfo.aph.gov.au/" + pdfLink.attr("href");
                                Optional<String> text = pdfParser.tryPdfTextExtraction(pdfUrl);

                                if (text.isPresent()) {
                                    int startIndex = text.get().indexOf(textStart);

                                    if (startIndex > 0) {
                                        String part = text.get().substring(startIndex);
                                        int endIndex = part.indexOf(")");

                                        if (endIndex > 0) {
                                            String origMention = part.substring(0, endIndex + 1);
                                            String normalized = origMention
                                                    .replace("Honourable", "Hon")
                                                    .replace("Hon.", "Hon")
                                                    .replace("\n", " ")
                                                    .replaceAll(" {2,}", " ");

                                            Optional<Originator> originator = parseOriginator(normalized);

                                            originator.ifPresentOrElse(orig -> {
                                                record.setOriginators(List.of(orig));

                                                log.info("Set originator for record {} - {}", record.getRecordId(),
                                                        orig);
                                                recordRepository.mergeInNewTransaction(record);
                                            }, () -> log.info("Originator string not recognized for record {}: {}",
                                                    record.getRecordId(), normalized));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private Optional<Originator> parseOriginator(String normalized) {
        Pattern ogNameRegex = Pattern.compile("(the)? Hon .+?(MP|QC|,|\\))");

        return ogNameRegex.matcher(normalized)
                .results()
                .findFirst()
                .map(nameMatch -> {
                    String name = nameMatch.group()
                            .replace("the", "").replace("Hon", "").replace(",", "").replace(")", "").trim();

                    String affil = normalized
                            .replace(nameMatch.group(), "")
                            .replace("the", "")
                            .replace("Senator", "")
                            .replace("Circulated by authority of", "")
                            .replaceAll(" {2,}", " ")
                            .replace("(", "")
                            .replace(")", "")
                            .trim();

                    return new Originator(name, affil);
                });
    }

    public void fixDateIntros() {
        //example:
        // https://www.aph.gov.au/Parliamentary_Business/Bills_Legislation/Bills_Search_Results/Result?bId=s1146
        // https://www.aph.gov.au/Parliamentary_Business/Bills_Legislation/Bills_Search_Results/Result?bId=s905

        /*
        Fixed with this:
            update legislative_data_au_new.bill_main_table bmt
            set date_introduction = q.min
            from (select id, min(ls.date)
                  from legislative_data_au_new.bill_main_table
                           join legislative_data_au_new.legislative_stages ls on bill_main_table.id = ls.record_id
                  where ls.date is not null
                  group by id) q
            where bmt.date_introduction is null
              and bmt.id = q.id;
         */
    }

    @Transactional
    public void fixBillSizes() {
        log.info("Fixing bill sizes...");

        String qlString = "select r from LegislativeDataRecord r where r.billSize is null and r.billText is not null";

        entityManager.createQuery(qlString, LegislativeDataRecord.class)
                .getResultStream()
                .forEach(bill -> {
                    int len = TextUtils.getLengthWithoutWhitespace(bill.getBillText());
                    bill.setBillSize(len);
                    recordRepository.mergeInNewTransaction(bill);

                    log.info("Updated bill {} with size {}", bill.getRecordId(), len);
                });
    }

    @Transactional
    public void parseActNumber() {
        log.info("Parsing act numbers");

        //filter for committees which have null data, then get records from that
        String qlString = "select r from LegislativeDataRecord r "
                + "where r.billStatus = 'PASS' and r.auCountrySpecificVariables.actNumber is null";

        Stream<LegislativeDataRecord> records =
                entityManager.createQuery(qlString, LegislativeDataRecord.class).getResultStream();

        try {
            driver = new ChromeDriver();
            records.forEach(this::getActNumber);
        } finally {
            driver.quit();
        }
    }

    private void getActNumber(LegislativeDataRecord record) {
        log.info("Getting act number for record {}", record.getBillPageUrl());

        Optional<WebElement> assentTable = Optional.empty();
        Optional<String> actNo = Optional.empty();
        Optional<String> year = Optional.empty();

        driver.get(record.getBillPageUrl());

        try {
            assentTable = Optional.ofNullable(driver.findElement(By.xpath("//table//span[contains(text(), 'Assent')]")))
                    .map(span -> span.findElement(By.xpath("..")));
        } catch (NoSuchElementException e) {
            log.error("No assent date found for record {}", record.getBillPageUrl());
        }

        try {
            if (assentTable.isPresent()) {
                actNo = getActNumberInfo(assentTable, "Act no");
                year = getActNumberInfo(assentTable, "Year");
            } else {
                log.warn("Unable to parse act number for record {}", record.getBillPageUrl());
            }
        } catch (NoSuchElementException e) {
            log.error("Problematic record: {}", record.getBillPageUrl());
        }

        if (actNo.isPresent() && year.isPresent()) {
            String actNumber = actNo.get() + " of " + year.get();

            AuCountrySpecificVariables auCountrySpecificVariables = record.getAuCountrySpecificVariables();
            auCountrySpecificVariables.setActNumber(actNumber);

            record.setAuCountrySpecificVariables(auCountrySpecificVariables);
            log.info("Act number {} saved for record: {}", actNumber, record.getRecordId());

            transactionTemplate.execute(status -> {
                recordRepository.merge(record);
                return record;
            });
        }
    }

    private Optional<String> getActNumberInfo(Optional<WebElement> assentTable, String actNumberComponent) {
        return assentTable.map(
                        table -> table.findElement(By.xpath("//span[contains(text(), '" + actNumberComponent + "')]")))
                .map(span -> span.findElement(By.xpath("following-sibling::*[1]")).getText());
    }

    private void parseBillTexts() {
        recordRepository.streamAllWithMissingBillTexts(Country.AUSTRALIA).forEach(record -> {
            log.info("Parsing bill text for record {}", record.getBillPageUrl());
            recordRepository.mergeInNewTransaction(parseBillText(record));
            log.info("Bill text parsed for record {}", record.getRecordId());
        });
    }

    private LegislativeDataRecord parseBillText(LegislativeDataRecord record) {
        HttpResponse<String> response = Unirest.get(record.getBillPageUrl()).asString();
        Document doc = Jsoup.parse(response.getBody());

        Optional<String> billPageUrl = Optional.ofNullable(
                        doc.getElementById("main_0_textOfBillReadingControl_readingItemRepeater_trFirstReading1_0"))
                .map(element -> element.getElementsByAttributeValue("src", "/images/template/icons/doc-pdf.png")
                        .first())
                .map(imgElement -> imgElement.parent().attr("href"));

        if (billPageUrl.isPresent()) {
            record.setBillTextUrl(billPageUrl.get());
            documentDownloader.processWithBrowser(billPageUrl.get()).ifPresent(record::setBillText);
            log.info("Bill text parsed for record {}", record.getBillPageUrl());
        } else {
            log.warn("Bill text not found");
        }


        return record;
    }

}
