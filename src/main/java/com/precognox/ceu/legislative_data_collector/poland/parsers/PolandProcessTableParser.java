package com.precognox.ceu.legislative_data_collector.poland.parsers;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.poland.constants.PolishTranslations;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * This class is responsible for parsing data from legislative process "tables" (like leg. stages, final voting, etc.).
 * There are 2 totally different HTML-structured page-formats. The page url from term3 to term6 is like:
 * https://orka.sejm.gov.pl/proc3.nsf/opisy/758.htm, the page url from term7 to term9 is like:
 * https://www.sejm.gov.pl/Sejm8.nsf/PrzebiegProc.xsp?nr=2147. The parsings are very different.
 */
@Slf4j
@Service
public class PolandProcessTableParser {

    private static final String BASE_URL_FOR_COMM_REPORTS_TERM3 = "https://orka.sejm.gov.pl";
    private static final String FIRST_READING_STAGE = "I CZYTANIE NA POSIEDZENIU SEJMU";
    private static final String FIRST_READING_STAGE_ANOTHER_FORMAT = "I CZYTANIE W KOMISJACH";
    private static final String FIRST_COMMITTEE_REPORT_STAGE = "PRACA W KOMISJACH PO I CZYTANIU";
    private static final String SECOND_COMMITTEE_REPORT_STAGE = "PRACA W KOMISJACH PO II CZYTANIU";
    private static final String SECOND_READING_STAGE = "II CZYTANIE NA POSIEDZENIU SEJMU";
    private static final String THIRD_READING_STAGE = "III CZYTANIE NA POSIEDZENIU SEJMU";
    private static final String SENATE_POSITION_STAGE = "STANOWISKO SENATU";
    private static final String SENATE_COMM_REPORT_STAGE = "PRACA W KOMISJACH NAD STANOWISKIEM SENATU";
    private static final String SENATE_POS_CONSIDER_STAGE = "ROZPATRYWANIE NA FORUM SEJMU STANOWISKA SENATU";
    private static final String PRESIDENT_SIGN_STAGE_SUBSTR = "Prezydent podpisał ustawę";
    private static final String FIRST_READING_COMM_REPORT_STAGE_NAME_TERM7 = "Praca w komisjach po I czytaniu";
    private static final String SECOND_READING_COMM_REPORT_STAGE_NAME_TERM7 = "Praca w komisjach po II czytaniu";
    private static final String SENATE_COMM_REPORT_STAGE_NAME_TERM7 = "Praca w komisjach nad stanowiskiem Senatu";
    private static final int FIRST_READING_STAGE_NUMBER = 1;
    private static final int SECOND_READING_STAGE_NUMBER = 2;
    private static final int THIRD_READING_STAGE_NUMBER = 3;
    private static final int SENATE_POSITION_STAGE_NUMBER = 4;
    private static final int SENATE_COMM_REPORT_STAGE_NUMBER = 5;
    private static final int SENATE_POSITION_CONSIDER_STAGE_NUMBER = 6;
    private static final int PRESIDENT_SIGNED_STAGE_NUMBER = 7;
    private static final Pattern COMM_REPORT_PDF_URL_OLD_REGEX = Pattern.compile("^.*(Druki[456]).*$");

    private final PageSourceRepository pageSourceRepository;
    private final PdfParser pdfParser;

    @Autowired
    public PolandProcessTableParser(PageSourceRepository pageSourceRepository, PdfParser pdfParser) {
        this.pageSourceRepository = pageSourceRepository;
        this.pdfParser = pdfParser;
    }

    public void parseProcessTableData(String processUrl, LegislativeDataRecord dataRecord) {
        log.info("Processing stored process-table for {} started", dataRecord.getLawId());
        Optional<PageSource> optSource = pageSourceRepository.findByPageUrl(processUrl);
        optSource.ifPresentOrElse(source -> {
            boolean pageHasOlderDesign = source.getPageUrl().startsWith("https://orka.sejm");
            if (pageHasOlderDesign) {
                parseProcessTableDataFromTerm3(source, dataRecord);
            } else {
                parseProcessTableDataFromTerm7(source, dataRecord);
            }
            log.info("Processing stored process-table for {} finished", dataRecord.getLawId());
        }, () -> log.error("Process page-source is not found for {}", dataRecord.getLawId()));

    }

    // From 1997 to cca. 2012
    private void parseProcessTableDataFromTerm3(PageSource source, LegislativeDataRecord dataRecord) {
        Document processPage = Jsoup.parse(source.getRawSource());

        Optional<Element> processTableBody =
                Optional.ofNullable(processPage.getElementsByTag("tbody").first());
        processTableBody.ifPresent(tableBody ->
                parseValuesFromTerm3(tableBody, dataRecord, pdfParser));
    }

    private void parseValuesFromTerm3(Element tableBody, LegislativeDataRecord dataRecord, PdfParser pdfParser
    ) {
        List<Element> processStages = new ArrayList<>();
        List<LegislativeStage> legislativeStages = new ArrayList<>();

        // If "I czytanie odbyło się" table-row exists on page, the first reading stage's name is "I CZYTANIE W KOMISJACH".
        // If not, the name is "I CZYTANIE NA POSIEDZENIU SEJMU".
        setFirstReadingLegStage(tableBody, processStages, legislativeStages, pdfParser);
        setSecondReadingLegStage(tableBody, processStages, legislativeStages, pdfParser);
        setThirdReadingLegStages(tableBody, processStages, legislativeStages);
        setSenatePositionLegStage(tableBody, processStages, legislativeStages);
        setSenateCommitteeReportLegStage(tableBody, processStages, legislativeStages, pdfParser);
        setSenatePositionConsLegStage(tableBody, processStages, legislativeStages);
        setPresidentSignedLegStage(tableBody, processStages, legislativeStages);

        dataRecord.setStages(legislativeStages);
        dataRecord.setStagesCount(legislativeStages.size());

        parseVotingFromTerm3(tableBody, dataRecord);
    }

    // Two different HTML-structure for the first reading, like https://orka.sejm.gov.pl/proc3.nsf/opisy/2375.htm vs.
    // https://orka.sejm.gov.pl/proc3.nsf/opisy/15.htm
    private void setFirstReadingLegStage(Element tableBody, List<Element> processStages,
                                         List<LegislativeStage> legislativeStages, PdfParser pdfParser
    ) {
        Optional<Element> firstReadingStage =
                findProcessStageWithNameAndAddToList(tableBody, FIRST_READING_STAGE, processStages);
        firstReadingStage.ifPresent(element -> {
            LegislativeStage firstReadingLegStage = new LegislativeStage();
            firstReadingLegStage.setStageNumber(FIRST_READING_STAGE_NUMBER);
            firstReadingLegStage.setName(FIRST_READING_STAGE);
            parseLegDebateSizeFromTerm3(tableBody, pdfParser, FIRST_COMMITTEE_REPORT_STAGE,
                    firstReadingLegStage, PolandProcessTableParser.BASE_URL_FOR_COMM_REPORTS_TERM3);

            Element parentRow = element.parent().parent();
            String dateText = parentRow.nextElementSibling().getElementsByTag("font").first().text();
            String[] dateTextArray = dateText.split(" "); //"pos. nr 92 dn. 29-11-2000"
            List<String> dateParts = new ArrayList<>(Arrays.asList(dateTextArray));
            String stageDate = dateParts.get(dateParts.size() - 1);
            setLegStageDate(stageDate, firstReadingLegStage);
            legislativeStages.add(firstReadingLegStage);
        });

        Optional<Element> firstReadingAnotherFormat =
                findProcessStageWithNameAndAddToList(tableBody, FIRST_READING_STAGE_ANOTHER_FORMAT, processStages);
        firstReadingAnotherFormat.ifPresent(element -> {
            LegislativeStage firstReadingLegStage = new LegislativeStage();
            firstReadingLegStage.setStageNumber(FIRST_READING_STAGE_NUMBER);
            firstReadingLegStage.setName(FIRST_READING_STAGE_ANOTHER_FORMAT);
            parseLegDebateSizeFromTerm3(tableBody, pdfParser, FIRST_COMMITTEE_REPORT_STAGE, firstReadingLegStage,
                    PolandProcessTableParser.BASE_URL_FOR_COMM_REPORTS_TERM3);

            Element parentRow = element.parent().parent();
            Optional<Element> altSkierowanoProcessStage = parentRow.nextElementSibling().getElementsByTag("font").stream()
                    .filter(font -> font.text().contains("skierowano"))
                    .findFirst();
            altSkierowanoProcessStage.ifPresent(stage -> processStages.add(altSkierowanoProcessStage.get()));
            Optional<Element> dateCellPrevSibling = tableBody.getElementsByTag("font").stream()
                    .filter(Element::hasText)
                    .filter(font -> font.text().contains("I czytanie odbyło się"))
                    .findFirst();
            dateCellPrevSibling.ifPresent(elem -> {
                Element optParentRow = dateCellPrevSibling.get().parent().parent();
                String dateTextCell = optParentRow.getElementsByTag("font").get(2).text();
                //"07-11-1997, 13-11-1997" format is possible as well, we choose the first date then
                String[] dateTextRaw = dateTextCell.split(",");
                String stageDate = dateTextRaw[0];
                setLegStageDate(stageDate, firstReadingLegStage);
            });
            legislativeStages.add(firstReadingLegStage);
        });
    }

    private void setSecondReadingLegStage(Element tableBody, List<Element> processStages,
                                          List<LegislativeStage> legislativeStages, PdfParser pdfParser
    ) {
        Optional<Element> secondReadingStage =
                findProcessStageWithNameAndAddToList(tableBody, SECOND_READING_STAGE, processStages);
        secondReadingStage.ifPresent(element -> {
            LegislativeStage secondReadingLegStage = new LegislativeStage();
            secondReadingLegStage.setStageNumber(SECOND_READING_STAGE_NUMBER);
            secondReadingLegStage.setName(SECOND_READING_STAGE);
            parseLegDebateSizeFromTerm3(tableBody, pdfParser, SECOND_COMMITTEE_REPORT_STAGE, secondReadingLegStage,
                    PolandProcessTableParser.BASE_URL_FOR_COMM_REPORTS_TERM3);

            Element parentRow = element.parent().parent();
            String dateText = parentRow.nextElementSibling().getElementsByTag("font").first().text();
            String[] dateTextArray = dateText.split(" "); //"pos. nr 92 dn. 29-11-2000"
            List<String> dateParts = new ArrayList<>(Arrays.asList(dateTextArray));
            String stageDate = dateParts.get(dateParts.size() - 1);
            setLegStageDate(stageDate, secondReadingLegStage);
            legislativeStages.add(secondReadingLegStage);
        });
    }

    private void setThirdReadingLegStages(Element tableBody, List<Element> processStages,
                                          List<LegislativeStage> legislativeStages
    ) {
        // In some processes (like https://orka.sejm.gov.pl/proc3.nsf/opisy/2375.htm) there are 2 "third" stages with the
        // same name: "III CZYTANIE NA POSIEDZENIU SEJMU". For example because of postponing the final voting.
        List<Element> thirdReadingStages = tableBody.getElementsByTag("font").stream()
                .filter(Element::hasText)
                .filter(font -> font.text().equalsIgnoreCase(THIRD_READING_STAGE))
                .toList();
        for (Element thirdReadingStage : thirdReadingStages) {
            processStages.add(thirdReadingStage);
            LegislativeStage thirdReadingLegStage = new LegislativeStage();
            thirdReadingLegStage.setName(THIRD_READING_STAGE);
            thirdReadingLegStage.setStageNumber(THIRD_READING_STAGE_NUMBER);
            Element parentRow = thirdReadingStage.parent().parent();
            String dateText = parentRow.nextElementSibling().getElementsByTag("font").first().text();
            String[] dateTextArray = dateText.split(" ");
            List<String> dateParts = new ArrayList<>(Arrays.asList(dateTextArray));
            String stageDate = dateParts.get(dateParts.size() - 1);
            setLegStageDate(stageDate, thirdReadingLegStage);
            legislativeStages.add(thirdReadingLegStage);
        }
    }

    private void setSenatePositionLegStage(Element tableBody,
                                           List<Element> processStages,
                                           List<LegislativeStage> legislativeStages
    ) {
        Optional<Element> senatePositionStage =
                findProcessStageWithNameAndAddToList(tableBody, SENATE_POSITION_STAGE, processStages);
        senatePositionStage.ifPresent(element -> {
            LegislativeStage senatePositionLegStage = new LegislativeStage();
            senatePositionLegStage.setStageNumber(SENATE_POSITION_STAGE_NUMBER);
            senatePositionLegStage.setName(SENATE_POSITION_STAGE);

            // We need the date here from row "Ustawę przekazano Prezydentowi i Marszałkowi Senatu"
            // or "Ustawę przekazano Marszałkowi Senatu" (LD-218 - annotation modified by the annotator)
            Optional<Element> dateTextCell = tableBody.getElementsByTag("font").stream()
                    .filter(font -> font.text().contains("Marszałkowi Senatu"))
                    .findFirst();
            dateTextCell.ifPresent(value -> {
                String dateText = value.text();
                String[] dateTextArray = dateText.split(" ");
                List<String> dateParts = new ArrayList<>(Arrays.asList(dateTextArray));
                String stageDate = dateParts.get(dateParts.size() - 1);
                setLegStageDate(stageDate, senatePositionLegStage);
            });
            legislativeStages.add(senatePositionLegStage);
        });
    }

    private void setSenateCommitteeReportLegStage(Element tableBody, List<Element> processStages,
                                                  List<LegislativeStage> legislativeStages, PdfParser pdfParser
    ) {
        Optional<Element> senateCommReportStage =
                findProcessStageWithNameAndAddToList(tableBody, SENATE_COMM_REPORT_STAGE, processStages);
        senateCommReportStage.ifPresent(element -> {
            LegislativeStage senateCommReportLegStage = new LegislativeStage();
            senateCommReportLegStage.setStageNumber(SENATE_COMM_REPORT_STAGE_NUMBER);
            senateCommReportLegStage.setName(SENATE_COMM_REPORT_STAGE);
            parseLegDebateSizeFromTerm3(tableBody, pdfParser, SENATE_COMM_REPORT_STAGE, senateCommReportLegStage,
                    PolandProcessTableParser.BASE_URL_FOR_COMM_REPORTS_TERM3);

            Element parentRow = element.parent().parent();
            Optional<Element> dateTextCell = parentRow.nextElementSibling().getElementsByTag("font").stream()
                    .filter(font -> font.text().contains("dn. "))
                    .findFirst();
            if (dateTextCell.isPresent()) {
                String dateText = dateTextCell.get().text();
                String[] dateTextArray = dateText.split(" ");
                List<String> dateParts = new ArrayList<>(Arrays.asList(dateTextArray));
                String stageDate = dateParts.get(dateParts.size() - 1);
                setLegStageDate(stageDate, senateCommReportLegStage);
            }
            legislativeStages.add(senateCommReportLegStage);
        });
    }

    private void setSenatePositionConsLegStage(Element tableBody,
                                               List<Element> processStages,
                                               List<LegislativeStage> legislativeStages
    ) {
        Optional<Element> senatePositionConsiderationStage = tableBody.getElementsByTag("font").stream()
                .filter(Element::hasText)
                .filter(font -> font.text().contains(SENATE_POS_CONSIDER_STAGE))
                .findFirst();
        senatePositionConsiderationStage.ifPresent(element -> {
            processStages.add(senatePositionConsiderationStage.get());
            LegislativeStage senatePositionConsLegStage = new LegislativeStage();
            senatePositionConsLegStage.setStageNumber(SENATE_POSITION_CONSIDER_STAGE_NUMBER);
            senatePositionConsLegStage.setName(SENATE_POS_CONSIDER_STAGE);
            Element parentRow = element.parent().parent();
            String dateText = parentRow.nextElementSibling().getElementsByTag("font").first().text();
            String[] dateTextArray = dateText.split(" "); //"pos. nr 92 dn. 29-11-2000"
            List<String> dateParts = new ArrayList<>(Arrays.asList(dateTextArray));
            String stageDate = dateParts.get(dateParts.size() - 1);
            setLegStageDate(stageDate, senatePositionConsLegStage);
            legislativeStages.add(senatePositionConsLegStage);
        });
    }

    private void setPresidentSignedLegStage(Element tableBody, List<Element> processStages,
                                            List<LegislativeStage> legislativeStages
    ) {
        Optional<Element> presidentSignedStage = tableBody.getElementsByTag("font").stream()
                .filter(Element::hasText)
                .filter(font -> font.text().contains(PRESIDENT_SIGN_STAGE_SUBSTR))
                .findFirst();
        presidentSignedStage.ifPresent(element -> {
            processStages.add(presidentSignedStage.get());
            LegislativeStage presidentSignedLegStage = new LegislativeStage();
            presidentSignedLegStage.setStageNumber(PRESIDENT_SIGNED_STAGE_NUMBER);
            presidentSignedLegStage.setName(PRESIDENT_SIGN_STAGE_SUBSTR);
            // We need the date from phrase like "Dnia 29-12-2000 Prezydent podpisał ustawę"
            String[] dateTextArray = element.text().split(" ");
            String stageDate = dateTextArray[1];
            setLegStageDate(stageDate, presidentSignedLegStage);
            legislativeStages.add(presidentSignedLegStage);
        });
    }

    private void parseLegDebateSizeFromTerm3(Element tableBody, PdfParser pdfParser, String stageName,
                                             LegislativeStage legStage, String baseUrlForCommReports) {
        tableBody.getElementsByTag("tr").stream()
                .filter(row -> !row.getElementsByTag("font").isEmpty())
                .filter(row -> row.getElementsByTag("font").text().equalsIgnoreCase(stageName))
                .findFirst()
                .ifPresent(elem -> {
                    Optional<Element> element = elem.nextElementSiblings().stream()
                            .filter(row -> row.getElementsByTag("td").size() == 4)
                            .filter(row -> row.getElementsByTag("td").get(1).text().equalsIgnoreCase("sprawozdanie komisji")
                                    || row.getElementsByTag("td").get(1).text().equalsIgnoreCase("sprawozdanie"))
                            .findFirst();
                    element.ifPresent(value -> {
                        String commReportPdfUrl = getCommitteeReportPdfUrl(value);
                        Optional<String> commReportText = pdfParser.tryPdfTextExtraction(commReportPdfUrl);
                        commReportText.ifPresent(text -> {
                            boolean isValidText = !commReportText.get().equals(PdfParser.SCANNED_LABEL)
                                    && !commReportText.get().equals(PdfParser.ERROR_LABEL);
                            if (isValidText) {
                                legStage.setDebateSize(TextUtils.getLengthWithoutWhitespace(text));
                            }
                        });
                    });
                });
    }

    @NotNull
    // like https://orka.sejm.gov.pl/Druki6ka.nsf/wgdruku/2708
    // Reports could be available only for the 1st, 2nd and 5th legislative stages.
    private String getCommitteeReportPdfUrl(Element elem) {
        String commReportPdfUrl = "";
        String commReportUrl = elem.getElementsByTag("a").first().attr("href");
        if (commReportUrl.matches(COMM_REPORT_PDF_URL_OLD_REGEX.pattern())) {
            Document commReportPage = null;
            try {
                commReportPage = Jsoup.connect(commReportUrl).get();
            } catch (UnsupportedMimeTypeException e) {
                return commReportUrl;
            } catch (IOException e) {
                log.error("Cannot find the url for committee report text for {}", elem);
            }
            Optional<Element> prNumberText = commReportPage.getElementsByTag("td").stream()
                    .filter(Element::hasText)
                    .filter(td -> td.text().startsWith("Druk nr"))
                    .findFirst();

            if (prNumberText.isPresent()) {
                String[] prNumberArray = prNumberText.get().text().split(" ");
                String prNumber = prNumberArray[2];
                Element aTag = commReportPage.getElementsByTag("a").stream()
                        .filter(Element::hasText)
                        .filter(a -> a.text().contains(prNumber + ".pdf") || a.text().contains(prNumber + ".PDF")
                                || a.text().contains(".pdf"))
                        .findFirst()
                        .get();
                commReportPdfUrl = aTag.attr("href");
            }
        } else {
            commReportPdfUrl = commReportUrl;
        }
        return commReportPdfUrl.startsWith("https") ? commReportPdfUrl : BASE_URL_FOR_COMM_REPORTS_TERM3 + commReportPdfUrl;
    }

    @NotNull
    private Optional<Element> findProcessStageWithNameAndAddToList(Element tableBody, String stageName,
                                                                   List<Element> processStages
    ) {
        Optional<Element> processStage = tableBody.getElementsByTag("font").stream()
                .filter(Element::hasText)
                .filter(font -> font.text().trim().equalsIgnoreCase(stageName))
                .findFirst();
        processStage.ifPresent(stage -> processStages.add(processStage.get()));
        return processStage;
    }

    private void setLegStageDate(String stageDate, LegislativeStage stage) {
        String[] date = stageDate.split("-");
        int year = Integer.parseInt(date[2]);
        int month = Integer.parseInt(date[1]);
        int day = Integer.parseInt(date[0]);
        stage.setDate(LocalDate.of(year, month, day));
    }

    // Results are in a table-row like: "wynik głosowania	:	412 za, 0 przeciw, 0 wstrzymało się (głos. nr 7)"
    private void parseVotingFromTerm3(Element table, LegislativeDataRecord dataRecord) {
        List<Element> votingResultCells = table.getElementsByTag("font").stream()
                .filter(Element::hasText)
                .filter(cell -> cell.text().contains("wynik głosowania"))
                .toList();

        if (!votingResultCells.isEmpty()) {
            // In some cases 1st or 2nd reading stages could contain voting results, but the result of final voting is
            // needed from the 3rd stage.
            Element finalVotingRow = votingResultCells.get(votingResultCells.size() - 1).parent().parent();
            String votingElement = finalVotingRow.getElementsByTag("td").get(3).text();
            String[] votingResults = votingElement.split(" "); //"399 za, 1 przeciw, 5 wstrzymało się (głos. nr 22)"
            if (votingResults.length < 10) {
                if (votingResults[1].equalsIgnoreCase("za") && votingResults.length == 5) {
                    dataRecord.setFinalVoteFor(Integer.parseInt(votingResults[0]));
                    dataRecord.setFinalVoteAgainst(0);
                    dataRecord.setFinalVoteAbst(0);
                } else if (votingResults[1].equalsIgnoreCase("przeciw") && votingResults.length == 5) {
                    dataRecord.setFinalVoteFor(0);
                    dataRecord.setFinalVoteAgainst(Integer.parseInt(votingResults[2]));
                    dataRecord.setFinalVoteAbst(0);
                }
            } else {
                dataRecord.setFinalVoteFor(Integer.parseInt(votingResults[0]));
                dataRecord.setFinalVoteAgainst(Integer.parseInt(votingResults[2]));
                dataRecord.setFinalVoteAbst(Integer.parseInt(votingResults[4]));
            }
        }
    }

    // From 2012 to 2023, like: https://www.sejm.gov.pl/Sejm8.nsf/PrzebiegProc.xsp?nr=2147
    private void parseProcessTableDataFromTerm7(PageSource source, LegislativeDataRecord dataRecord) {
        Document processPage = Jsoup.parse(source.getRawSource());
        String baseUrlForCommReports = source.getPageUrl().substring(0, 34);
        Optional<Element> processTable =
                Optional.ofNullable(processPage.selectXpath("//ul[@class='proces zakonczony']").first());
        processTable.ifPresent(table -> parseValuesFromTerm7(table, dataRecord, baseUrlForCommReports));
    }

    private void parseValuesFromTerm7(Element table, LegislativeDataRecord dataRecord, String baseUrlForCommReports) {
        List<Element> processStagesFromTerm7 = table.getElementsByTag("li").stream()
                .filter(row -> row.text().length() > 4) // filter out rows indicating just the year itself
                .filter(row -> !row.getElementsByTag("h3").isEmpty())
                .filter(row -> StringUtils.isNotBlank((row.getElementsByTag("h3").first().text().trim())))
                .filter(row -> !row.getElementsByTag("span").isEmpty())
                .toList();

        List<LegislativeStage> legislativeStages = new ArrayList<>();
        // Sometimes final voting is postponed and two stages' name start with "III czytanie".
        List<Element> possibleThirdReadings = processStagesFromTerm7.stream()
                .filter(row -> row.getElementsByTag("h3").first().text().startsWith("III czytanie"))
                .toList();
        if (!possibleThirdReadings.isEmpty()) {
            parseVotingFromTerm7(dataRecord, possibleThirdReadings);
        }
        setLegislativeStagesFromTerm7(possibleThirdReadings, processStagesFromTerm7, legislativeStages, baseUrlForCommReports);
        dataRecord.setStages(legislativeStages);
        dataRecord.setStagesCount(legislativeStages.size());
    }

    private void parseVotingFromTerm7(LegislativeDataRecord dataRecord, List<Element> possibleThirdReadings) {
        Optional<Element> realVoting = possibleThirdReadings.stream()
                .filter(element -> !element.getElementsByTag("h3").isEmpty())
                .filter(element -> element.getElementsByTag("p").text().contains("Wynik:"))
                .findFirst();
        realVoting.ifPresent(rv -> {
            Optional<Element> votingParagraph =
                    realVoting.get().getElementsByTag("div").first().getElementsByTag("p").stream()
                            .filter(p -> !p.getElementsByTag("a").isEmpty())
                            .findFirst(); // "Wynik: 240 za, 202 przeciw, 4 wstrzymało się (głos. nr 39)"
            votingParagraph.ifPresent(num -> {
                String[] votes = votingParagraph.get().text().split(" ");
                if (votes.length < 11) {
                    if (votes[2].equalsIgnoreCase("za") && votes.length == 6) { // only "for" votes
                        dataRecord.setFinalVoteFor(Integer.parseInt(votes[1]));
                        dataRecord.setFinalVoteAgainst(0);
                        dataRecord.setFinalVoteAbst(0);
                    } else if (votes[2].equalsIgnoreCase("przeciw") && votes.length == 6) { // only "against" votes
                        dataRecord.setFinalVoteFor(0);
                        dataRecord.setFinalVoteAgainst(Integer.parseInt(votes[1]));
                        dataRecord.setFinalVoteAbst(0);
                    }
                } else {
                    dataRecord.setFinalVoteFor(Integer.parseInt(votes[1]));
                    dataRecord.setFinalVoteAgainst(Integer.parseInt(votes[3]));
                    dataRecord.setFinalVoteAbst(Integer.parseInt(votes[5]));
                }
            });
        });
    }

    private void setLegislativeStagesFromTerm7(List<Element> possibleThirdReadingVoting, List<Element> processStagesFromTerm7,
                                               List<LegislativeStage> legislativeStages, String baseUrlForCommReports
    ) {
        List<Element> legStageElements = new ArrayList<>();
        for (Element stage : processStagesFromTerm7) {
            boolean stageIsLegStage = stage.getElementsByTag("h3").first().text().trim().equalsIgnoreCase(FIRST_READING_STAGE)
                    || stage.getElementsByTag("h3").first().text().trim().equalsIgnoreCase(FIRST_READING_STAGE_ANOTHER_FORMAT)
                    || stage.getElementsByTag("h3").first().text().trim().equalsIgnoreCase(SECOND_READING_STAGE)
                    || stage.getElementsByTag("h3").first().text().trim().equalsIgnoreCase(THIRD_READING_STAGE)
                    || stage.getElementsByTag("h3").first().text().trim().equalsIgnoreCase(SENATE_POSITION_STAGE)
                    || stage.getElementsByTag("h3").text().trim().equalsIgnoreCase(SENATE_COMM_REPORT_STAGE_NAME_TERM7)
                    || stage.getElementsByTag("h3").first().text().trim().equalsIgnoreCase(SENATE_POS_CONSIDER_STAGE)
                    || stage.getElementsByTag("h3").first().text().trim().equalsIgnoreCase(PRESIDENT_SIGN_STAGE_SUBSTR);
            if (stageIsLegStage) {
                legStageElements.add(stage);
            }
        }
        setLegislativeStage(legStageElements, "I czytanie", FIRST_READING_STAGE_NUMBER, legislativeStages);
        setLegislativeStage(legStageElements, "II czytanie", SECOND_READING_STAGE_NUMBER, legislativeStages);
        for (int repeat = 0; repeat < possibleThirdReadingVoting.size(); repeat++) {
            setLegislativeStage(legStageElements, "III czytanie", THIRD_READING_STAGE_NUMBER, legislativeStages);
        }
        setLegislativeStage(legStageElements, "Stanowisko Senatu", SENATE_POSITION_STAGE_NUMBER, legislativeStages);
        // we need the date here from row "Ustawę przekazano Prezydentowi i Marszałkowi Senatu" or "Ustawę przekazano Marszałkowi Senatu" (LD-218)
        Optional<Element> senatePositionDateText = processStagesFromTerm7.stream()
                .filter(stage -> stage.getElementsByTag("h3").text().contains("Marszałkowi Senatu"))
                .findFirst();
        senatePositionDateText.ifPresent(st -> legislativeStages.get(legislativeStages.size() - 1)
                .setDate(setLegislativeDate(senatePositionDateText.get().getElementsByTag("span").first().text())));

        // we need the last commReport in the timeline (from the given two or three or any)
        Optional<Element> elementAfterCommReport = legStageElements.stream()
                .filter(row -> row.getElementsByTag("h3").first().text().startsWith("Rozpatrywanie"))
                .findFirst();
        elementAfterCommReport.ifPresent(
                element -> setCommitteeReportStage(element, legislativeStages));
        setLegislativeStage(legStageElements, "Rozpatrywanie na forum Sejmu", SENATE_POSITION_CONSIDER_STAGE_NUMBER, legislativeStages);
        setLegislativeStage(legStageElements, PRESIDENT_SIGN_STAGE_SUBSTR, PRESIDENT_SIGNED_STAGE_NUMBER, legislativeStages);

        setCommitteeReportsDebateSize(processStagesFromTerm7, legislativeStages, baseUrlForCommReports);
    }

    private void setLegislativeStage(List<Element> legStageProcesses, String stageNameStart, int stageNumber,
                                     List<LegislativeStage> stages
    ) {
        Optional<Element> stageElement = legStageProcesses.stream()
                .filter(row -> row.getElementsByTag("h3").first().text().startsWith(stageNameStart))
                .findFirst();
        stageElement.ifPresent(fr -> {
            LegislativeStage stage = new LegislativeStage();
            stage.setStageNumber(stageNumber);
            stage.setName(stageElement.get().getElementsByTag("h3").first().text());
            stage.setDate(setLegislativeDate(stageElement.get().getElementsByTag("span").first().text()));
            stages.add(stage);
        });
    }

    private void setCommitteeReportStage(Element elementAfterCommReport, List<LegislativeStage> legislativeStages) {
        // Filter out false stages, like here: https://www.sejm.gov.pl/Sejm7.nsf/PrzebiegProc.xsp?nr=759
        Element commReportStageCandidate = elementAfterCommReport.previousElementSibling().text().length() < 5 ?
                elementAfterCommReport.previousElementSibling().previousElementSibling()
                : elementAfterCommReport.previousElementSibling();
        if (!commReportStageCandidate.getElementsByTag("h4").isEmpty()) {
            String commReportStageDate = commReportStageCandidate.getElementsByTag("span").first().text();
            String commReportStageName = commReportStageCandidate.getElementsByTag("h4").first().text();
            LegislativeStage stage = new LegislativeStage();
            stage.setName(commReportStageName);
            stage.setDate(setLegislativeDate(commReportStageDate));
            stage.setStageNumber(SENATE_COMM_REPORT_STAGE_NUMBER);
            legislativeStages.add(stage);
        }
    }

    private LocalDate setLegislativeDate(String dateText) {
        String[] date = dateText.split(" ");
        int year = Integer.parseInt(date[2]);
        int month = PolishTranslations.MONTHS_TRANSLATIONS.get(date[1]);
        int day = Integer.parseInt(date[0]);

        return LocalDate.of(year, month, day);
    }

    private void setCommitteeReportsDebateSize(List<Element> processStages, List<LegislativeStage> legislativeStages,
                                               String baseUrlForCommReports
    ) {
        List<Element> commReportProcessStages = processStages.stream()
                .filter(element -> element.getElementsByTag("h3").text().contains("Praca w komisjach"))
                .toList();
        for (Element stage : commReportProcessStages) {
            setDebSizeFromTerm7(stage, FIRST_READING_COMM_REPORT_STAGE_NAME_TERM7,
                    baseUrlForCommReports, legislativeStages, 1);
            setDebSizeFromTerm7(stage, SECOND_READING_COMM_REPORT_STAGE_NAME_TERM7,
                    baseUrlForCommReports, legislativeStages, 2);
            setDebSizeFromTerm7(stage, SENATE_COMM_REPORT_STAGE_NAME_TERM7,
                    baseUrlForCommReports, legislativeStages, 5);
        }
    }

    private void setDebSizeFromTerm7(Element committeeReportProcessStage, String stageName, String baseUrl,
                                     List<LegislativeStage> legStages, int stageNumber
    ) {
        if (committeeReportProcessStage.getElementsByTag("h3").text().equalsIgnoreCase(stageName)) {
            String commReportTextPdfUrl = getCommitteeReportUrl(baseUrl, committeeReportProcessStage);
            Optional<String> committeeReportText = pdfParser.tryPdfTextExtraction(commReportTextPdfUrl);
            committeeReportText.ifPresent(text -> {
                boolean isValidText = !committeeReportText.get().equals(PdfParser.SCANNED_LABEL)
                        && !committeeReportText.get().equals(PdfParser.ERROR_LABEL);
                legStages.stream()
                        .filter(stage -> stage.getStageNumber() == stageNumber)
                        .findFirst()
                        .ifPresent(stage ->
                                stage.setDebateSize(isValidText ? TextUtils.getLengthWithoutWhitespace(text) : null));
            });
        }
    }

    private String getCommitteeReportUrl(String baseUrl, Element commReportProcessStage) {
        String pdfUrl = "";
        String url = commReportProcessStage.getElementsByTag("a").first().attr("href");
        String commReportPageUrl = url.startsWith("https") ? url : baseUrl + url;
        HttpResponse<String> httpResponse = Unirest.get(commReportPageUrl).asString();
        Document commReportPage = Jsoup.parse(httpResponse.getBody());
        List<Element> pdfUrls = commReportPage.getElementsByTag("a").stream()
                .filter(li -> li.hasClass("pdf"))
                .toList();
        if (!pdfUrls.isEmpty()) {
            pdfUrl = pdfUrls.get(0).attr("href").trim();
        }
        return pdfUrl;
    }
}
