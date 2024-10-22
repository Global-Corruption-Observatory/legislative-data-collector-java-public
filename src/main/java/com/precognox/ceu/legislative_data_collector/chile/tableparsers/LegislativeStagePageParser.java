package com.precognox.ceu.legislative_data_collector.chile.tableparsers;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.BILL_TEXT_API;

public class LegislativeStagePageParser extends TablePageParser {
    private final static Pattern INTRODUCTION_STAGE_REGEX = Pattern.compile("Ingreso\\s*de\\s*Proyecto", Pattern.CASE_INSENSITIVE);
    private final static Pattern TEXT_DOWNLOAD_STAGE_REGEX = Pattern.compile("(mensaje|moción)", Pattern.CASE_INSENSITIVE);
    private final static Map<Integer, String> STAGE_NAMES = Map.of(
            1, "First Constitutional step in chamber 1",
            2, "Second Constitutional step in chamber 2",
            3, "Completion step in chamber of origin"
    );
    private final int dateColumnIndex;
    private final int stageColumnIndex;
    private final int subStageColumnIndex;
    private final int documentsColumnIndex;

    public LegislativeStagePageParser(Document page) throws DataCollectionException {
        super(page, 1);
        this.dateColumnIndex = getIndexOfColumn("Fecha", 2);
        this.subStageColumnIndex = getIndexOfColumn("Subetapa", 3);
        this.stageColumnIndex = getIndexOfColumn("Etapa", 4);
        this.documentsColumnIndex = getIndexOfColumn("Ver Documentos", 5);
    }

    public List<LegislativeStage> parseLegislativeStages() {
        return IntStream.range(0, STAGE_REGEXES.size())
                .mapToObj(i -> getLegislativeStage(STAGE_REGEXES.get(i), i + 1))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<LegislativeStage> getLegislativeStage(Pattern stageRegex, int stageNumber) {
        Optional<Element> stageRow = tableRows.stream()
                .filter(row -> isSearchedTableRow(row, stageRegex, stageColumnIndex))
                .findFirst();
        return stageRow.flatMap(row -> getDateFromRow(row, dateColumnIndex))
                .map(date -> new LegislativeStage(stageNumber, date, STAGE_NAMES.get(stageNumber), null));
    }

    public LocalDate getDateOfIntroduction() throws DataCollectionException {
        Optional<Element> introductionStageRow = tableRows.stream()
                .filter(row -> isSearchedTableRow(row, INTRODUCTION_STAGE_REGEX, subStageColumnIndex))
                .findFirst();
        return introductionStageRow.flatMap(row -> getDateFromRow(row, dateColumnIndex))
                .orElseThrow(() -> new DataCollectionException("Introduction Stage not found"));
    }

    public Optional<String> getBillTextUrl() {
        Optional<Element> textDownloadRow = tableRows.stream()
                .filter(row -> isSearchedTableRow(row, TEXT_DOWNLOAD_STAGE_REGEX, documentsColumnIndex))
                .findFirst();
        return textDownloadRow
                .flatMap(row -> getCellOfTableRow(row, documentsColumnIndex))
                .map(cell -> cell.selectXpath("//a").first())
                .map(link -> link.attr("href"))
                .map(linkEnd -> String.format(BILL_TEXT_API, linkEnd));
    }

    @Override
    protected String getGoal() {
        return "legislative stages";
    }
}
