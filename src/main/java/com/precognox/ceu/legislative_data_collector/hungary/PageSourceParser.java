package com.precognox.ceu.legislative_data_collector.hungary;

import com.google.common.collect.Streams;
import com.jauntium.Browser;
import com.jauntium.Document;
import com.jauntium.Element;
import com.jauntium.NotFound;
import com.jauntium.Table;
import com.precognox.ceu.legislative_data_collector.common.JauntiumBrowserFactory;
import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.PdfUtils;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord.BillStatus.ONGOING;
import static com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord.BillStatus.PASS;
import static com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord.BillStatus.REJECT;
import static com.precognox.ceu.legislative_data_collector.hungary.PageType.BILL;
import static com.precognox.ceu.legislative_data_collector.hungary.PageType.BILL_TEXT;
import static com.precognox.ceu.legislative_data_collector.hungary.PageType.DEBATE_TEXT;
import static com.precognox.ceu.legislative_data_collector.hungary.PageType.LAW_TEXT_1;
import static com.precognox.ceu.legislative_data_collector.hungary.PageType.LAW_TEXT_2;
import static com.precognox.ceu.legislative_data_collector.hungary.PageType.ULESNAP;
import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;

/**
 * Parses most of the variables from the stored page sources.
 */
@Slf4j
@Service
public class PageSourceParser {

    protected static final Map<Integer, String> LEGISLATIVE_STAGES = Map.of(
            1, "bizottság kijelölve részletes vita lefolytatására|Az illetékes bizottság kijelölve",
            2, "általános vita megkezdve",
            3, "részletesvita-szakasz megnyitva|részletes vita megkezdve",
            4, "bizottsági jelentések és az összegző módosító javaslat vitája megkezdve|bizottsági jelentés\\(ek\\) vitája megkezdve",
            5, "Köztársasági elnök aláírta"
    );

    private static final int COMMITTEE_STAGE_NUM = 1;
    private static final int PASSING_STAGE_NUM = 5;
    protected static final String LAW_TYPE_CONSTITUTION_CHANGE
            = "Alaptörvény elfogadására, illetve módosítására irányuló javaslat";

    //speed up the collection during testing
    private static final boolean SKIP_DEBATES = true;

    private final PrimaryKeyGeneratingRepository recordRepository;
    private final PageSourceRepository pageSourceRepository;
    private final JauntiumBrowserFactory browserFactory;
    private final PageSourceLoader pageSourceLoader;
    private final ModifiedLawParser modifiedLawParser;

    @Autowired
    public PageSourceParser(
            PrimaryKeyGeneratingRepository recordRepository,
            PageSourceRepository pageSourceRepository,
            JauntiumBrowserFactory browserFactory,
            PageSourceLoader pageSourceLoader,
            ModifiedLawParser modifiedLawParser) {
        this.recordRepository = recordRepository;
        this.pageSourceRepository = pageSourceRepository;
        this.browserFactory = browserFactory;
        this.pageSourceLoader = pageSourceLoader;
        this.modifiedLawParser = modifiedLawParser;
    }

    public void processStoredPages() {
        int currentBatch = 0;
        int batchSize = 5;
        int maxBatch;

        do {
            Page<PageSource> page = pageSourceRepository.findUnprocessedBills(
                    PageRequest.of(currentBatch, batchSize), Country.HUNGARY
            );

            page.forEach(storedSource -> {
                try {
                    processBillLink(storedSource);
                } catch (Exception e) {
                    log.error("Failed to process stored source: " + storedSource.getPageUrl(), e);
                }
            });

            currentBatch++;
            maxBatch = page.getTotalPages();
        } while (currentBatch <= maxBatch);

        log.info("Finished processing pages");
    }

    /**
     * Not part of the normal processing. Used for testing or to reproduce errors.
     *
     * @param pageUrl
     */
    public void processSingleBill(String pageUrl) {
        processBillLink(pageSourceRepository.findByPageTypeAndPageUrl(BILL.name(), pageUrl).get());
    }

    public void processBillLink(PageSource storedSource) {
        if (recordRepository.existsByBillPageUrl(storedSource.getPageUrl())) {
            log.info("Skipping processed bill: {}", storedSource.getPageUrl());
            return;
        }

        log.debug("Processing bill page: {}", storedSource.getPageUrl());

        Browser browser = browserFactory.create();

        try {
            Document billPage = pageSourceLoader.loadCode(browser, storedSource.getRawSource());
            LegislativeDataRecord record = buildRecord(billPage);
            record.setBillPageUrl(storedSource.getPageUrl());

            saveRecord(record);
        } catch (Exception e) {
            log.error("Failed to process bill: " + storedSource.getPageUrl(), e);
        } finally {
            browser.close();
        }
    }

    public LegislativeDataRecord buildRecord(Document billDetailsPage) {
        LegislativeDataRecord record = new LegislativeDataRecord();
        record.setCountry(Country.HUNGARY);

        try {
            Element iromCimDiv = billDetailsPage.findFirst("<div class=\"irom-cim\">");
            String billId = iromCimDiv.findFirst("<a>").getText();
            String billTitle = iromCimDiv.findEvery("<td>").getElement(1).getText();

            record.setBillId(billId);
            record.setBillTitle(billTitle);
        } catch (NotFound e) {
            record.getErrors().add("'irom-cim' div not found");
        }

        extractBillDetails(billDetailsPage, record);
        extractStages(billDetailsPage, record);
        extractCommittees(billDetailsPage, record);
        extractFinalVotes(billDetailsPage, record);
        extractLawText(billDetailsPage, record);

        return record;
    }

    private void extractBillDetails(Document billDetailsPage, LegislativeDataRecord record) {
        try {
            Table iromanyAdataiTable = new Table(
                    billDetailsPage.findFirst("<th>Iromány adatai").getParent().getParent().getParent()
            );

            String billNatureTerm = iromanyAdataiTable.getTextFromRow("Jelleg").get(1);

            Boolean isOriginalLaw = switch (billNatureTerm) {
                case "új" -> true;
                case "módosító" -> false;
                default -> null;
            };

            record.setOriginalLaw(isOriginalLaw);
            String dateIntroduction = iromanyAdataiTable.getTextFromRow("Benyújtva").get(1);
            record.setDateIntroduction(DateUtils.parseHungaryDate(dateIntroduction));

            record.setBillId(record.getDateIntroduction().getYear() + "/" + record.getBillId());

            String lawType = iromanyAdataiTable.getTextFromRow("Típus").get(1);
            record.setBillType(lawType);
            record.setTypeOfLawEng(Translations.LAW_TYPE_TRANSLATIONS.get(lawType.toLowerCase()));

            try {
                String actId = iromanyAdataiTable.getTextFromRow("Kihirdetés száma").get(1);

                if (!actId.isBlank()) {
                    record.setLawId(actId);
                }
            } catch (NotFound notFound) {
                record.getErrors().add("Act ID missing");
            }

            try {
                String dateEnteringForce = iromanyAdataiTable.getTextFromRow("Kihirdetés dátuma").get(1);
                record.setDateEnteringIntoForce(DateUtils.parseHungaryDate(dateEnteringForce));
            } catch (NotFound notFound) {
                record.getErrors().add("Date entering force missing");
            }

            String status = iromanyAdataiTable.getTextFromRow("Állapot").get(1);

            switch (status) {
                case "kihirdetve" -> record.setBillStatus(PASS);
                case "Országgyűlés nem tárgyalja",
                        "elutasítva",
                        "az OGY nem tárgyalja",
                        "tárgyalása lezárva",
                        "visszavonva",
                        "visszautasítva" -> record.setBillStatus(REJECT);
                default -> record.setBillStatus(ONGOING);
            }

            String procTypeNatTerm = iromanyAdataiTable.getTextFromRow("Tárgyalási mód").get(1);
            record.setProcedureTypeNational(procTypeNatTerm);
            record.setProcedureTypeEng(Translations.PROCEDURE_TYPE_TRANSLATIONS.get(procTypeNatTerm.toLowerCase()));

            if (containsIgnoreCase(procTypeNatTerm, "normál")) {
                record.setProcedureTypeStandard(LegislativeDataRecord.ProcedureType.REGULAR);
            } else if (containsIgnoreCase(procTypeNatTerm, "sürgős") || containsIgnoreCase(procTypeNatTerm, "kivételes")) {
                record.setProcedureTypeStandard(LegislativeDataRecord.ProcedureType.EXCEPTIONAL);
            }

            String originator = iromanyAdataiTable.getTextFromRow("Benyújtó\\(k\\)").get(1);

            if (originator.contains("kormány")) {
                record.setOriginType(OriginType.GOVERNMENT);
                record.setOriginators(List.of(new Originator(originator)));
            } else if (originator.contains("bizottság")) {
                record.setOriginType(OriginType.GOVERNMENT);
                record.setOriginators(List.of(new Originator(originator)));
            } else {
                record.setOriginType(OriginType.INDIVIDUAL_MP);
                List<Originator> originators = parseOriginatorList(originator);

                record.setOriginators(originators);
            }

            int[] cellCoord = iromanyAdataiTable.getCellCoord("Irományszöveg");
            Element billTextLinkCell = iromanyAdataiTable.getCell(cellCoord[0] + 1, cellCoord[1]);
            List<String> billTextLinks = billTextLinkCell.findElements(By.tagName("a"))
                    .stream()
                    .map(aTag -> aTag.getAttribute("href"))
                    .toList();

            Optional<String> pdfLink = billTextLinks.stream().filter(l -> l.endsWith(".pdf")).findFirst();

            if (pdfLink.isPresent()) {
                record.setBillTextUrl(pdfLink.get());
                PdfUtils.tryPdfTextExtraction(pdfLink.get()).ifPresent(record::setBillText);
            } else if (!billTextLinks.isEmpty()) {
                record.setBillTextUrl(billTextLinks.get(0));
                getBillTextFromHtml(record);
            }

            TextUtils.removeGeneralJustification(record);
        } catch (NotFound e) {
            log.error("Iromány adatai table not found", e);
        }
    }

    private List<Originator> parseOriginatorList(String originator) {
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

    private Originator parseOriginator(String origString) {
        if (origString.contains("(")) {
            int affNameStart = origString.indexOf("(");
            int affNameEnd = origString.contains(")") ? origString.indexOf(")") : origString.length() - 1;

            String name = origString.substring(0, affNameStart).trim();
            String affiliation = origString.substring(affNameStart + 1, affNameEnd);

            if ("független".equals(affiliation)) {
                affiliation = "independent";
            }

            return new Originator(name, affiliation);
        } else {
            return new Originator(origString);
        }
    }

    private void getBillTextFromHtml(LegislativeDataRecord record) {
        if (record.getBillTextUrl().endsWith(".htm") || record.getBillTextUrl().endsWith(".html")) {
            String error = null;
            Browser newWindow = browserFactory.create();

            try {
                Document page = pageSourceLoader.fetchParsedDocument(
                        newWindow, BILL_TEXT.name(), record.getBillTextUrl());

                if (isPlaintextInHtml(page)) {
                    record.setBillText(page.getText());
                } else {
                    Optional<WebElement> billTextLink = page.findElements(By.tagName("a"))
                            .stream()
                            .filter(link -> "a törvényjavaslat".equalsIgnoreCase(link.getText())
                                    || "a törvényjavaslat szövege".equalsIgnoreCase(link.getText()))
                            .findFirst();

                    if (billTextLink.isPresent()) {
                        String textPageUrl = billTextLink.get().getAttribute("href");

                        if (textPageUrl.endsWith(".pdf")) {
                            PdfUtils.tryPdfTextExtraction(textPageUrl).ifPresent(record::setBillText);
                        } else {
                            //handle as plaintext page
                            Document billTextPage =
                                    pageSourceLoader.fetchParsedDocument(newWindow, BILL_TEXT.name(), textPageUrl);
                            record.setBillText(billTextPage.getText());
                        }
                    } else {
                        //handle as plaintext page
                        record.setBillText(page.getText());
                    }
                }
            } catch (Exception e) {
                error = "Failed to get bill text from page: " + record.getBillTextUrl() + " - error: " + e;
            } finally {
                newWindow.close();
            }

            if (error != null) {
                log.error(error);
                record.getErrors().add(error);
            }
        }
    }

    private boolean isPlaintextInHtml(Document page) {
        return page.findElements(By.tagName("a"))
                .stream()
                .noneMatch(link -> link.getAttribute("href") != null);
    }

    private void extractStages(Document billDetailsPage, LegislativeDataRecord record) {
        try {
            Element iromanyEsemenyekTableHeader = billDetailsPage.findFirst("<th>Iromány események");
            Table iromanyEsemenyekTable = new Table(iromanyEsemenyekTableHeader.getParent().getParent().getParent());

            LEGISLATIVE_STAGES.forEach((stageNum, stageNameRegex) -> {
                Set<String> felszolalasLinks = new HashSet<>();

                try {
                    int[] currentStageCoords = iromanyEsemenyekTable.getCellCoord(stageNameRegex);

                    if (currentStageCoords != null) {
                        String matchedStageName = iromanyEsemenyekTable
                                .getCell(currentStageCoords[0], currentStageCoords[1])
                                .getText();

                        String stageDate = iromanyEsemenyekTable
                                .getCell(currentStageCoords[0] - 1, currentStageCoords[1])
                                .getText();

                        if (LEGISLATIVE_STAGES.containsKey(stageNum + 1)) {
                            int[] nextStageCoords = iromanyEsemenyekTable.getCellCoord(LEGISLATIVE_STAGES.get(stageNum + 1));

                            int nextStageRowIndex;

                            if (nextStageCoords != null) {
                                nextStageRowIndex = nextStageCoords[1];
                            } else {
                                //check until end of table
                                nextStageRowIndex =
                                        iromanyEsemenyekTable.tableElement.findElements(By.tagName("tr")).size() - 1;
                            }

                            for (int row = currentStageCoords[1]; row < nextStageRowIndex; row++) {
                                Element felszolalasCell = iromanyEsemenyekTable.getCell(4, row);

                                if ((felszolalasCell.getChildElements().size() > 0)) {
                                    felszolalasLinks.add(felszolalasCell.getChildElements().get(0).getAttribute("href"));
                                }
                            }
                        }

                        int stageDebateLength = 0;

                        if (!SKIP_DEBATES) {
                            Browser newWindow = browserFactory.create();

                            try {
                                Set<String> ulesnapLinks = felszolalasLinks
                                        .stream()
                                        .map(link -> {
                                            try {
                                                Document felszolalasPage = pageSourceLoader.fetchParsedDocument(
                                                        newWindow, DEBATE_TEXT.name(), link
                                                );

                                                return felszolalasPage.findFirst("<a>Ülésnap adatai").getAttribute("href");
                                            } catch (NotFound e) {
                                                return null;
                                            }
                                        }).filter(Objects::nonNull)
                                        .collect(Collectors.toSet());

                                stageDebateLength = ulesnapLinks
                                        .stream()
                                        .mapToInt(ulesnapLink -> getCommentsLengthFromUlesnapLink(record, newWindow, ulesnapLink))
                                        .sum();
                            } finally {
                                newWindow.close();
                            }
                        }

                        String translatedStageName = Translations.LEGISLATIVE_STAGES_TRANSLATIONS.get(matchedStageName);

                        LegislativeStage stageEntity = new LegislativeStage(
                                stageNum,
                                DateUtils.parseHungaryDate(stageDate),
                                translatedStageName != null ? translatedStageName : matchedStageName,
                                stageDebateLength
                        );

                        record.getStages().add(stageEntity);
                    }
                } catch (NotFound e) {
                    log.debug(stageNameRegex + " legislative stage date not found");
                }
            });

            //add 1st stage if missing
            if (!record.getStages().isEmpty()
                    && record.getStages().stream().noneMatch(stg -> Objects.equals(stg.getStageNumber(), 1))) {
                String firstStageLabel = Translations.LEGISLATIVE_STAGES_TRANSLATIONS.get(
                        "bizottság kijelölve részletes vita lefolytatására"
                );

                LegislativeStage stg = new LegislativeStage();
                stg.setStageNumber(1);
                stg.setName(firstStageLabel);
                record.getStages().add(stg);
            }

            record.setStagesCount(record.getStages().size());

            record.getStages().stream()
                    .filter(stg -> stg.getStageNumber().equals(COMMITTEE_STAGE_NUM))
                    .findFirst()
                    .ifPresent(stg -> record.setCommitteeDate(stg.getDate()));

            record.getStages().stream()
                    .filter(stg -> stg.getStageNumber().equals(PASSING_STAGE_NUM))
                    .findFirst()
                    .ifPresent(stg -> record.setDatePassing(stg.getDate()));

            int committeesHearings = iromanyEsemenyekTable.getElement()
                    .findEach("<td>^bizottság bejelentette részletes vita lefolytatását$")
                    .size();

            if (committeesHearings == 0) {
                committeesHearings = iromanyEsemenyekTable.getElement()
                        .findEach("<td>^bizottság kijelölve részletes vita lefolytatására$")
                        .size();

                if (committeesHearings == 0) {
                    committeesHearings = iromanyEsemenyekTable.getElement()
                            .findEach("<td>^Az illetékes bizottság kijelölve$")
                            .size();
                }
            }

            record.setCommitteeHearingCount(committeesHearings);

            int sizeOfDebatesAccumulated = record.getStages()
                    .stream()
                    .filter(stg -> stg.getDebateSize() != null)
                    .mapToInt(LegislativeStage::getDebateSize)
                    .sum();

            record.setPlenarySize(sizeOfDebatesAccumulated);
        } catch (NotFound e) {
            record.getErrors().add("Iromány események table not found");
        }
    }

    private int getCommentsLengthFromUlesnapLink(LegislativeDataRecord record, Browser newWindow, String ulesnapLink) {
        try {
            Document ulesnapPage = pageSourceLoader.fetchParsedDocument(newWindow, ULESNAP.name(), ulesnapLink);

            Element relevantBillTable = ulesnapPage.findFirst("<a>" + record.getBillId())
                    .getParent()
                    .getParent()
                    .getParent()
                    .getParent();

            List<String> commentLinks = relevantBillTable.findElements(By.tagName("tr"))
                    .stream()
                    .skip(2)
                    .map(tr -> tr.findElement(By.tagName("td")))
                    .map(td -> td.findElement(By.tagName("a")))
                    .map(a -> a.getAttribute("href"))
                    .toList();

            Browser felszolalasWindow = browserFactory.create();

            try {
                int commentsLength = commentLinks.stream()
                        .map(url -> pageSourceLoader.fetchParsedDocument(felszolalasWindow, DEBATE_TEXT.name(), url, "felsz_szovege"))
                        .map(page -> getCharCount(felszolalasWindow.driver))
                        .filter(Optional::isPresent)
                        .mapToInt(Optional::get)
                        .sum();

                return commentsLength;
            } finally {
                felszolalasWindow.close();
            }
        } catch (NotFound e) {
            log.error(e.toString());
        }

        return 0;
    }

    private Optional<Integer> getCharCount(WebDriver felszolalasPageDriver) {
        List<WebElement> felszSzovegeDiv = felszolalasPageDriver.findElements(By.className("felsz_szovege"));

        if (felszSzovegeDiv.size() == 1) {
            String text = felszSzovegeDiv.get(0)
                    .getText()
                    .replaceAll("A felszólalás szövege:\n", "");

            return Optional.of(TextUtils.getLengthWithoutWhitespace(text));
        }

        return Optional.empty();
    }

    private void extractCommittees(Document billDetailsPage, LegislativeDataRecord record) {
        try {
            Element bizottsagokTableHeader = billDetailsPage.findFirst("<th>^Irományt tárgyaló bizottság$");
            Table bizottsagokTable = new Table(bizottsagokTableHeader.getParent().getParent().getParent());

            List<String> bizottsagokCol = bizottsagokTable.getTextFromColumn("Bizottság");
            List<String> rolesCol = bizottsagokTable.getTextFromColumn("Tárgyalási szerepkör")
                    .stream()
                    .map(Translations.COMMITTEE_ROLE_TRANSLATIONS::get)
                    .toList();

            List<Committee> committees = Streams.zip(bizottsagokCol.stream(), rolesCol.stream(), Committee::new)
                    .skip(2)
                    .toList();

            record.setCommittees(committees);
            record.setCommitteeCount(committees.size());
        } catch (NotFound notFound) {
            record.getErrors().add("Committees data not found");
        }
    }

    private void extractFinalVotes(Document billDetailsPage, LegislativeDataRecord record) {
        try {
            Element votesTableHeader = billDetailsPage.findFirst("<th>Szavazások az irományról");
            Element tableElement = votesTableHeader.getParent().getParent().getParent();

            Optional<WebElement> finalVoteRow = Streams.findLast(tableElement.findElements(By.tagName("tr"))
                    .stream()
                    .filter(this::isFinalVoteRow));

            finalVoteRow.ifPresent(row -> {
                List<String> finalVoteRowContents = row
                        .findElements(By.tagName("td"))
                        .stream()
                        .map(WebElement::getText)
                        .toList();

                Integer yesVotes = Integer.parseInt(finalVoteRowContents.get(2));
                Integer noVotes = Integer.parseInt(finalVoteRowContents.get(3));
                Integer abstention = Integer.parseInt(finalVoteRowContents.get(4));

                record.setFinalVoteFor(yesVotes);
                record.setFinalVoteAgainst(noVotes);
                record.setFinalVoteAbst(abstention);
            });
        } catch (NotFound notFound) {
            record.getErrors().add("Final votes data not found: " + notFound);
        } catch (NumberFormatException e) {
            log.debug("Invalid value for number of votes: ", e);
        }
    }

    private boolean isFinalVoteRow(WebElement element) {
        List<WebElement> cells = element.findElements(By.tagName("td"));

        if (cells.size() != 6)
            return false;

        List<String> finalVoteLabels = List.of(
                "önálló indítvány elfogadva",
                "önálló indítvány elutasítva",
                "önálló indítvány egyszerű többséget igénylő része elfogadva",
                "önálló indítvány egyszerű többséget igénylő része elutasítva"
        );

        return finalVoteLabels.contains(cells.get(1).getText());
    }

    private void extractLawText(Document billDetailsPage, LegislativeDataRecord record) {
        if (record.getBillStatus() != null && record.getBillStatus() == PASS) {
            try {
                Element nemOnalloIromanyokTableHeader = billDetailsPage.findFirst("<th>nem önálló iromány");
                Table nemOnalloIromanyokTable = new Table(nemOnalloIromanyokTableHeader.getParent().getParent().getParent());
                int[] lawTextLinkCell = nemOnalloIromanyokTable.getCellCoord(
                        "^Köztársasági elnöknek aláírásra megküldött törvény szövege$"
                );

                if (lawTextLinkCell != null) {
                    try {
                        String lawTextPageLink = nemOnalloIromanyokTable.getCell(lawTextLinkCell[0], lawTextLinkCell[1])
                                .getFirst("<a>")
                                .getAttribute("href");

                        Browser newWindow = browserFactory.create();

                        try {
                            Document lawTextLinksPage = pageSourceLoader.fetchParsedDocument(
                                    newWindow, LAW_TEXT_1.name(), lawTextPageLink
                            );

                            Element table = lawTextLinksPage.findFirst(
                                    "<th>Nem önálló irományok").getParent().getParent().getParent();

                            String nextPageLink = table.findFirst("<a>").getAttribute("href");
                            Document lawTextDownloadPage =
                                    pageSourceLoader.fetchParsedDocument(newWindow, LAW_TEXT_2.name(), nextPageLink);

                            Element pdfLink = lawTextDownloadPage.findFirst("<a href=\".*?\\.pdf\">szöveges PDF");
                            String pdfUrl = pdfLink.getAttribute("href");
                            record.setLawTextUrl(pdfUrl);
                            Optional<String> lawText = PdfUtils.tryPdfTextExtraction(pdfUrl);

                            lawText.ifPresent(text -> {
                                record.setLawText(text);
                                record.setLawSize(TextUtils.getLengthWithoutWhitespace(text));
                            });
                        } finally {
                            newWindow.close();
                        }
                    } catch (Exception e) {
                        log.error("", e);
                        record.getErrors().add("Failed to get law text - " + e);
                    }
                } else {
                    record.getErrors().add("Failed to get law text, expected element not found");
                }
            } catch (NotFound e) {
                record.getErrors().add("Failed to get law text, expected element not found");
            }
        }
    }

    private void saveRecord(LegislativeDataRecord rec) {
        try {
            recordRepository.save(rec);
            log.info("Saved bill {} - {} with title: {}", rec.getRecordId(), rec.getBillId(), rec.getBillTitle());
        } catch (Exception e) {
            log.error("Failed to save bill", e);
        }
    }

    //temporary code for one bugfix (to recollect the modified laws only)
    @Transactional
    public void recollectModifiedLaws() {
        recordRepository.streamAll(Country.HUNGARY)
                .peek(modifiedLawParser::parseModifiedLaws)
                .peek(record -> log.info("Found {} modified laws for record {}", record.getModifiedLawsCount(), record.getRecordId()))
                .forEach(recordRepository::mergeInNewTransaction);
    }

}
