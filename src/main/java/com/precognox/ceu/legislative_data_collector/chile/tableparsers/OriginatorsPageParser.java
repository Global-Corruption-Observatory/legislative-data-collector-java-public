package com.precognox.ceu.legislative_data_collector.chile.tableparsers;

import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import org.jsoup.nodes.Document;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class OriginatorsPageParser extends TablePageParser {
    private final static String COLUMN_HEADER = "Autor";
    private final int nameColumnIndex;

    public OriginatorsPageParser(Document page) throws DataCollectionException {
        super(page, 1);
        this.nameColumnIndex = getIndexOfColumn(COLUMN_HEADER, 2);
    }

    public List<Originator> getOriginators() {
        return tableRows.stream()
                .map(row -> getContentOfCellOfRow(row, nameColumnIndex))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(text -> !text.equalsIgnoreCase(COLUMN_HEADER))
                .map(Originator::new)
                .collect(Collectors.toList());
    }

    @Override
    protected String getGoal() {
        return "bill originators";
    }
}
