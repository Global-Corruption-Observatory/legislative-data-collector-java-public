package com.precognox.ceu.legislative_data_collector.hungary;

import com.jauntium.Browser;
import com.jauntium.Document;
import com.jauntium.Element;
import com.jauntium.NotFound;
import com.jauntium.Table;
import com.precognox.ceu.legislative_data_collector.common.JauntiumBrowserFactory;
import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.precognox.ceu.legislative_data_collector.hungary.PageSourceParser.LEGISLATIVE_STAGES;
import static com.precognox.ceu.legislative_data_collector.hungary.PageType.DEBATE_TEXT;
import static com.precognox.ceu.legislative_data_collector.hungary.PageType.ULESNAP;
import static com.precognox.ceu.legislative_data_collector.hungary.Translations.LEGISLATIVE_STAGES_TRANSLATIONS;

/**
 * Scrapes the debates for the stored bills and calculates the stage sizes & the plenary_size variable. Run after
 * {@link PageSourceParser} is finished.
 */
@Slf4j
@Service
public class DebateSizeCollector {

    @Autowired
    private LegislativeDataRepository legislativeDataRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JauntiumBrowserFactory browserFactory;

    @Autowired
    private PageSourceLoader pageSourceLoader;

    @Transactional
    public void collectDebateSizes() {
        log.info("Collecting debates...");
        log.info("Found {} records to process", legislativeDataRepository.countRecordsWithoutPlenarySize());

        legislativeDataRepository.findRecordsWithoutPlenarySize().forEach(this::processInTransaction);
    }

    private void processInTransaction(LegislativeDataRecord record) {
        try {
            collectDebateSize(record);
            transactionTemplate.execute(status -> entityManager.merge(record));
        } catch (Exception e) {
            log.error("Failed to process record: {}", record.getBillPageUrl());
            log.error("", e);
        }
    }

    @SneakyThrows
    public LegislativeDataRecord collectDebateSize(LegislativeDataRecord record) {
        Browser browser = browserFactory.create();
        Browser newWindow = browserFactory.create();

        try {
            Document billPage =
                    pageSourceLoader.fetchParsedDocument(browser, PageType.BILL.name(), record.getBillPageUrl());

            Element iromanyEsemenyekTableHeader = billPage.findFirst("<th>Iromány események");
            Table iromanyEsemenyekTable = new Table(iromanyEsemenyekTableHeader.getParent().getParent().getParent());

            LEGISLATIVE_STAGES.forEach((stageNum, stageNameRegex) -> {
                Set<String> felszolalasLinks = new HashSet<>();

                try {
                    //iterate on iromanyEsemenyekTable rows, find matching stage name, get felszólalás links
                    int[] currentStageCoords = iromanyEsemenyekTable.getCellCoord(stageNameRegex);

                    if (currentStageCoords != null) {
                        String matchedStageName = iromanyEsemenyekTable
                                .getCell(currentStageCoords[0], currentStageCoords[1])
                                .getText();

                        String stageDate = iromanyEsemenyekTable
                                .getCell(currentStageCoords[0] - 1, currentStageCoords[1])
                                .getText();

                        //check if we're not at the last stage - either we check until the end of the table,
                        // or to the row of the next stage
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

                        Set<String> ulesnapLinks = felszolalasLinks
                                .stream()
                                .map(link -> {
                                    try {
                                        Document felszolalasPage = pageSourceLoader.fetchParsedDocument(
                                                newWindow, DEBATE_TEXT.name(), link, "pair-content"
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

                        //set debate length for current stage...
                        String translatedStageName = LEGISLATIVE_STAGES_TRANSLATIONS.get(matchedStageName);

                        Optional<LegislativeStage> currentStage = record.getStages().stream()
                                .filter(stage -> translatedStageName.equals(stage.getName()))
                                .findFirst();

                        if (currentStage.isPresent()) {
                            currentStage.get().setDebateSize(stageDebateLength);
                        } else {
                            LegislativeStage newStage = new LegislativeStage(
                                    stageNum,
                                    DateUtils.parseHungaryDate(stageDate),
                                    translatedStageName,
                                    stageDebateLength
                            );

                            record.getStages().add(newStage);
                        }

                        log.info("Set stage debate size: {} for record {}", stageDebateLength, record.getRecordId());
                    }
                } catch (NotFound e) {
                    log.error("Expected element not found", e);
                }
            });
        } catch (NotFound e) {
            log.warn(e.toString()); //can be normal
        } finally {
            browser.close();
            newWindow.close();
        }

        int sizeOfDebatesAccumulated = record.getStages()
                .stream()
                .filter(stg -> stg.getDebateSize() != null)
                .mapToInt(LegislativeStage::getDebateSize)
                .sum();

        record.setPlenarySize(sizeOfDebatesAccumulated);
        log.info("Set plenary size: {} for record: {}", sizeOfDebatesAccumulated, record.getRecordId());

        return record;
    }

    @SneakyThrows
    private int getCommentsLengthFromUlesnapLink(LegislativeDataRecord record, Browser newWindow, String ulesnapLink) {
        try {
            Document ulesnapPage =
                    pageSourceLoader.fetchParsedDocument(newWindow, ULESNAP.name(), ulesnapLink, "pair-content");

            Element relevantBillTable = ulesnapPage.findFirst("<a>" + getRawBillId(record.getBillId()))
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
                        .map(url -> fetchCommentsPage(url, felszolalasWindow))
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

    private Document fetchCommentsPage(String url, Browser felszolalasWindow) {
        return pageSourceLoader.fetchParsedDocument(
                felszolalasWindow, DEBATE_TEXT.name(), url, "felsz_szovege");
    }

    /**
     * Removes the year from the bill ID.
     *
     * @param billId
     * @return
     */
    private String getRawBillId(String billId) {
        String[] parts = billId.split("/");

        if (parts.length == 3) {
            return parts[1] + "/" + parts[2];
        }

        return billId;
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

}
