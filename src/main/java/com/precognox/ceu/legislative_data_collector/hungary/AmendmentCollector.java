package com.precognox.ceu.legislative_data_collector.hungary;

import com.jauntium.*;
import com.precognox.ceu.legislative_data_collector.common.BrowserPool;
import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.AmendmentOriginator;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.PdfUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.precognox.ceu.legislative_data_collector.hungary.PageType.AMENDMENT_1;
import static com.precognox.ceu.legislative_data_collector.hungary.PageType.AMENDMENT_2;

/**
 * Collects the amendments for stored bills.
 */
@Slf4j
@Service
public class AmendmentCollector {

    @Autowired
    private PageSourceLoader pageSourceLoader;

    @Autowired
    private LegislativeDataRepository legislativeDataRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BrowserPool browserPool;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final Map<Integer, String> AMENDMENT_STAGE_LABELS = Map.of(
            1, "Módosító javaslat",
            2, "Összegző módosító javaslat"
    );

    @SneakyThrows
    @Transactional
    public void collectAllAmendments() {
        log.info("Collecting amendments...");
        log.info("Found {} records to process", legislativeDataRepository.countRecordsWithoutAmendments());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        legislativeDataRepository.findRecordsWithoutAmendments().forEach(
                record -> pool.submit(() -> processInTransaction(record))
        );

        pool.awaitTermination(1, TimeUnit.DAYS);

        log.info("Finished collecting amendments");
    }

    private void processInTransaction(LegislativeDataRecord record) {
        processAmendments(record);

        transactionTemplate.execute(status -> entityManager.merge(record));
    }

    @SneakyThrows
    public void processAmendments(LegislativeDataRecord record) {
        log.info("Collecting amendments for record {}: {}", record.getRecordId(), record.getBillPageUrl());

        record.setAmendments(new ArrayList<>());
        Browser browser = browserPool.get();

        try {
            Document billDetailsPage =
                    pageSourceLoader.fetchParsedDocument(browser, PageType.BILL.name(), record.getBillPageUrl());

            Element nemOnalloIromanyokTableHeader = billDetailsPage.findFirst("<th>nem önálló iromány");
            Table nemOnalloIromanyokTable = new Table(nemOnalloIromanyokTableHeader.getParent().getParent().getParent());

            int[] firstStageAmendmentsLinkCell =
                    nemOnalloIromanyokTable.getCellCoord("Módosító javaslat|módosító javaslat");

            if (firstStageAmendmentsLinkCell != null) {
                String amendmentsPageLink = nemOnalloIromanyokTable
                        .getCell(firstStageAmendmentsLinkCell[0], firstStageAmendmentsLinkCell[1])
                        .getFirst("<a>")
                        .getAttribute("href");

                tryProcessAmendmentsPage(amendmentsPageLink, 1, record);
            }

            record.setAmendmentCount(record.getAmendments().size());

            int[] secondStageAmendmentLinkCell =
                    nemOnalloIromanyokTable.getCellCoord("Összegző módosító javaslat");

            if (secondStageAmendmentLinkCell != null) {
                String amendmentsPageLink = nemOnalloIromanyokTable
                        .getCell(secondStageAmendmentLinkCell[0], secondStageAmendmentLinkCell[1])
                        .getFirst("<a>")
                        .getAttribute("href");

                tryProcessAmendmentsPage(amendmentsPageLink, 2, record);
            }
        } catch (NotFound e) {
            log.error("Expected element not found on page: {} - {}", record.getBillPageUrl(), e.toString());
            record.setAmendmentCount(0);
        } finally {
            browserPool.returnToPool(browser);
        }
    }

    private void tryProcessAmendmentsPage(String amendmentsPageLink, int stage, LegislativeDataRecord record) {
        Browser newWindow = browserPool.get();

        try {
            processAmendmentsPage(newWindow, amendmentsPageLink, stage, record);
        } catch (Exception e) {
            log.error("Couldn't process amendments page: " + amendmentsPageLink, e);
            record.getErrors().add("Couldn't process amendments page - " + e);
        } finally {
            browserPool.returnToPool(newWindow);
        }
    }

    private void processAmendmentsPage(
            Browser newWindow, String amendmentsPageLink, int stage, LegislativeDataRecord record) {
        PageSource storedPage = pageSourceLoader.loadFromDbOrFetchWithBrowser(
                AMENDMENT_1.name().toUpperCase(), amendmentsPageLink, "table-bordered");

        Document amendmentListPage = pageSourceLoader.loadCode(newWindow, storedPage.getRawSource());

        Table amendmentsTable =
                new Table(newWindow.wrap(amendmentListPage.findElement(By.className("table-bordered"))));

        //get all links to a list
        List<String> singleAmendmentLinks = amendmentsTable
                .getElement()
                .findElements(By.tagName("tr"))
                .stream()
                .skip(2)
                .map(tr -> tr.findElement(By.tagName("td")))
                .map(td -> td.findElement(By.tagName("a")))
                .map(a -> a.getAttribute("href"))
                .toList();

        singleAmendmentLinks.forEach(link -> {
            try {
                Document amendmentPage = pageSourceLoader.fetchParsedDocument(newWindow, AMENDMENT_1.name(), link);

                try {
                    //go to page showing votes if the link exists (also contains all other variables)
                    String nextLink = amendmentPage.findFirst("<a>\\d szavazás az irományról").getAttribute("href");
                    amendmentPage = pageSourceLoader.fetchParsedDocument(newWindow, AMENDMENT_2.name(), nextLink);
                } catch (NotFound e) {
                    //normal, stay on same page
                }

                List<WebElement> tables = amendmentPage.findElements(By.className("table-bordered"));
                Table detailsTable = new Table(newWindow.wrap(tables.get(0)));
                String dateString = detailsTable.getTextFromRow("Benyújtva").get(1);
                String originatorString = detailsTable.getTextFromRow("Benyújtó\\(k\\)").get(1);
                String committee = null;

                try {
                    committee = detailsTable.getTextFromRow("Nem önálló irományt tárgyaló bizottság").get(1);
                } catch (NotFound e) {
                    //normal
                }

                String amendmentId = null;
                String tableHeader = detailsTable.getElement().findElement(By.tagName("th")).getText();
                Matcher idMatcher = Pattern.compile("\\d+/\\d+").matcher(tableHeader);
                if (idMatcher.find()) {
                    amendmentId = idMatcher.group();
                }

                Amendment entity = new Amendment();
                entity.setDataRecord(record);
                entity.setAmendmentId(amendmentId);
                entity.setDate(DateUtils.parseHungaryDate(dateString));
                entity.setStageNumber(stage);
                entity.setStageName(AMENDMENT_STAGE_LABELS.get(stage));
                entity.setCommitteeName(committee);
                entity.setOriginators(parseOriginatorList(originatorString));

                try {
                    String pdfLink = amendmentPage
                            .findFirst("<a href=\".*?\\.pdf\">szöveges PDF")
                            .getAttribute("href");

                    Optional<String> text = PdfUtils.tryPdfTextExtraction(pdfLink);
                    text.ifPresent(entity::setAmendmentText);
                    entity.setTextSourceUrl(pdfLink);
                } catch (NotFound e) {
                    //normal
                }

                try {
                    if (tables.size() > 1 && tables.get(1).findElement(By.tagName("th")).getText().contains("Szavazások")) {
                        Table votesTable = new Table(newWindow.wrap(tables.get(1)));

                        int yesVotes;
                        int noVotes;
                        int abstention;
                        String outcomeString;

                        if (votesTable.tableElement.findElements(By.tagName("tr")).size() == 4) {
                            yesVotes = Integer.parseInt(votesTable.getTextFromColumn("Igen").get(2));
                            noVotes = Integer.parseInt(votesTable.getTextFromColumn("Nem").get(2));
                            abstention = Integer.parseInt(votesTable.getTextFromColumn("Tart.").get(2));
                            outcomeString = votesTable.getTextFromColumn("Eredmény").get(2);
                        } else {
                            List<String> simpleMajorityRowText = votesTable.getTextFromRow(".+?egyszerű többséget.+?");
                            yesVotes = Integer.parseInt(simpleMajorityRowText.get(3));
                            noVotes = Integer.parseInt(simpleMajorityRowText.get(4));
                            abstention = Integer.parseInt(simpleMajorityRowText.get(5));
                            outcomeString = simpleMajorityRowText.get(6);
                        }

                        Amendment.Outcome outcome = switch (outcomeString) {
                            case "Elfogadott" -> Amendment.Outcome.APPROVED;
                            case "Elvetett" -> Amendment.Outcome.REJECTED;
                            default -> null;
                        };

                        entity.setVotesInFavor(yesVotes);
                        entity.setVotesAgainst(noVotes);
                        entity.setVotesAbstention(abstention);
                        entity.setOutcome(outcome);
                    }
                } catch (NotFound e) {
                    //todo ???
                }

                record.getAmendments().add(entity);
            } catch (NotFound e) {
                record.getErrors().add("Failed to process amendment page: " + e);
            }
        });
    }

    private List<AmendmentOriginator> parseOriginatorList(String originator) {
        String result = originator
                .replaceAll("\\d", "")
                .replaceAll(",", "\n")
                .replaceAll(":", "")
                .replaceAll("\\.{2,}", "");

        return Arrays.stream(result.split("\n"))
                .map(String::trim)
                .map(this::parseOriginator)
                .toList();
    }

    private AmendmentOriginator parseOriginator(String origString) {
        if (origString.contains("(")) {
            int affNameStart = origString.indexOf("(");
            int affNameEnd = origString.contains(")") ? origString.indexOf(")") : origString.length() - 1;

            String name = origString.substring(0, affNameStart).trim();
            String affiliation = origString.substring(affNameStart + 1, affNameEnd);

            if ("független".equals(affiliation)) {
                affiliation = "independent";
            }

            return new AmendmentOriginator(name, affiliation);
        } else {
            return new AmendmentOriginator(origString);
        }
    }
}
