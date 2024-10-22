package com.precognox.ceu.legislative_data_collector.chile.tableparsers;

import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The bill's stages, originators, procedure type and committee information are all in the same structure on their respective pages, with a table. This class gives a baseline to traverse their information
 */
public abstract class TablePageParser {

    private final static Pattern FIRST_STAGE_REGEX = Pattern.compile("Primer\\s*trámite", Pattern.CASE_INSENSITIVE);
    private final static Pattern SECOND_STAGE_REGEX = Pattern.compile("Segundo\\s*trámite", Pattern.CASE_INSENSITIVE);
    private final static Pattern THIRD_STAGE_REGEX = Pattern.compile("(Trámite\\s*finalización|Tercer\\s*trámite)", Pattern.CASE_INSENSITIVE);
    protected final static List<Pattern> STAGE_REGEXES = List.of(FIRST_STAGE_REGEX, SECOND_STAGE_REGEX, THIRD_STAGE_REGEX);
    protected Elements tableRows;
    private List<String> headerCells;

    public TablePageParser(Document page, int requiredRows) throws DataCollectionException {
        this.tableRows = page.selectXpath("/html/body/div[1]/div/div/div[2]/div/div/div/table[1]/tbody/tr");
        if (tableRows.size() <= requiredRows) {
            throw new DataCollectionException(String.format("No information about the %s on the page!", getGoal()));
        }
        Element headerRow = tableRows.first();
        this.headerCells = headerRow.selectXpath("//td").stream().map(Element::text).map(String::trim).toList();
    }

    protected int getIndexOfColumn(String columnName, int defaultValue) {
        return getIndexOfColumn(columnName).orElse(defaultValue);
    }

    private Optional<Integer> getIndexOfColumn(String columnName) {
        if (headerCells.contains(columnName)) {
            return Optional.of(headerCells.indexOf(columnName) + 1);
        } else {
            return Optional.empty();
        }
    }

    protected Boolean isSearchedTableRow(Element row, Pattern searchRegex, int searchedColumnIndex) {
        return getContentOfCellOfRow(row, searchedColumnIndex)
                .map(searchRegex::matcher)
                .map(Matcher::find).orElse(Boolean.FALSE);
    }

    protected Optional<String> getContentOfCellOfRow(Element row, int searchedColumnIndex) {
        return getCellOfTableRow(row, searchedColumnIndex)
                .map(Element::text)
                .map(String::trim);
    }

    protected Optional<Element> getCellOfTableRow(Element row, int columnIndex) {
        String xpathQuery = String.format("//td[%d]", columnIndex);
        return Optional.ofNullable(row.selectXpath(xpathQuery).first());
    }

    protected Optional<LocalDate> getDateFromRow(Element row, int dateColumnIndex) {
        return getContentOfCellOfRow(row, dateColumnIndex)
                .map(DateUtils::parseChileDate);
    }

    protected abstract String getGoal();

}
