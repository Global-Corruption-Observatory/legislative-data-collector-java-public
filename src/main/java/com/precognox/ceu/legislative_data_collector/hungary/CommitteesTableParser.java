package com.precognox.ceu.legislative_data_collector.hungary;

import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jsoup.nodes.Element;

import java.util.List;

public class CommitteesTableParser {

    private final LegislativeDataRecord result;
    private final Element votesDiv;

    public CommitteesTableParser(LegislativeDataRecord result, Element votesDiv) {
        this.result = result;
        this.votesDiv = votesDiv;
    }

    public void parse() {
        Element votesTable = votesDiv.selectFirst("table");

        if (votesTable != null) {
            List<Committee> committees = votesTable.getElementsByTag("tr")
                    .stream()
                    .skip(2)
                    .map(row -> row.getElementsByTag("td"))
                    .map(cells -> new ImmutablePair<>(cells.get(0).text().trim(), cells.get(1).text().trim()))
                    .map(pair -> new Committee(pair.left, Translations.COMMITTEE_ROLE_TRANSLATIONS.get(pair.right)))
                    .toList();

            result.setCommittees(committees);
            result.setCommitteeCount(committees.size());
        }
    }
}
