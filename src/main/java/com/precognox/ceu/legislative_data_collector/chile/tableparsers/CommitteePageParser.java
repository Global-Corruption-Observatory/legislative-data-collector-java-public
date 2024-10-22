package com.precognox.ceu.legislative_data_collector.chile.tableparsers;

import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CommitteePageParser extends TablePageParser {

    private final static Pattern COMMITTEE_NAME_REGEX =
            Pattern.compile("informe\\s*de\\s*(comisión.*)", Pattern.CASE_INSENSITIVE);

    private final int dateColumnIndex;
    private final int nameColumnIndex;
    private final int stageColumnIndex;

    public CommitteePageParser(Document page) throws DataCollectionException {
        super(page, 1);
        this.dateColumnIndex = getIndexOfColumn("Fecha", 1);
        this.nameColumnIndex = getIndexOfColumn("Informe", 2);
        this.stageColumnIndex = getIndexOfColumn("Etapa", 3);
    }

    public Integer getHearingCount() {
        return tableRows.size() - 1;
    }

    public List<Committee> getCommittees() {
        List<Committee> allCommittees = tableRows.stream()
                .map(this::buildCommitteeFromRow)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        Map<String, List<Committee>> groupsByName =
                allCommittees.stream().collect(Collectors.groupingBy(Committee::getName));

        //keep the earliest of every group
        List<Committee> distinctCommittees = groupsByName.values().stream()
                .map(list -> list.stream().min(Comparator.comparing(Committee::getDate)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        return distinctCommittees;
    }

    private Optional<Committee> buildCommitteeFromRow(Element row) {
        Optional<Committee> committee = getContentOfCellOfRow(row, nameColumnIndex)
                .map(COMMITTEE_NAME_REGEX::matcher)
                .filter(Matcher::find)
                .map(nameMatcher -> nameMatcher.group(1))
                .map(name -> name.replace("CERTIFICADO", ""))
                .map(String::trim)
                .map(name -> new Committee(name, null));

        if (committee.isPresent()) {
            Optional<LocalDate> date = getDateFromRow(row, dateColumnIndex);
            date.ifPresent(localDate -> committee.get().setDate(localDate));
        }

        return committee;
    }

    @Override
    protected String getGoal() {
        return "committees";
    }
}
