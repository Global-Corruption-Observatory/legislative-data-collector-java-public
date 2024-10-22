package com.precognox.ceu.legislative_data_collector.chile.tableparsers;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord.ProcedureType;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import org.jsoup.nodes.Document;

import java.util.Optional;
import java.util.regex.Pattern;

public class ProcedurePageParser extends TablePageParser {
    private final static Pattern NOT_EXCEPTIONAL_REGEX = Pattern.compile("CADUCAD[OA]");
    private final static String COLUMN_HEADER = "N° Mensaje Retiro";

    public ProcedurePageParser(Document page) throws DataCollectionException {
        super(page, 0);
    }

    public ProcedureType getProcedureType() {
        if (tableRows.size() <= 1) {
            return LegislativeDataRecord.ProcedureType.REGULAR;
        }

        int infoColumnIndex = getIndexOfColumn(COLUMN_HEADER, 5);

        boolean exceptional = tableRows.stream()
                .map(row -> getContentOfCellOfRow(row, infoColumnIndex))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(text -> !text.equals(COLUMN_HEADER))
                .anyMatch(text -> !NOT_EXCEPTIONAL_REGEX.matcher(text).find()); //if there is any w/o the CAUCADO/A text it is exceptional

        if (exceptional) {
            return LegislativeDataRecord.ProcedureType.EXCEPTIONAL;
        }

        return LegislativeDataRecord.ProcedureType.REGULAR;
    }

    @Override
    protected String getGoal() {
        return "procedure type";
    }
}
