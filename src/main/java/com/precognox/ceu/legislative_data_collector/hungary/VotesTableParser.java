package com.precognox.ceu.legislative_data_collector.hungary;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.Optional;

public class VotesTableParser {

    private final LegislativeDataRecord result;
    private final Element szavazasokDiv;

    public VotesTableParser(LegislativeDataRecord result, Element votesDiv) {
        this.result = result;
        this.szavazasokDiv = votesDiv;
    }

    public void parse() {
        Optional.ofNullable(szavazasokDiv.selectFirst("table"))
                .map(table -> table.getElementsByTag("tr").last())
                .filter(this::isFinalVoteRow)
                .map(row -> row.getElementsByTag("td"))
                .ifPresent(cells -> {
                    Integer yesVotes = Integer.parseInt(cells.get(2).text());
                    Integer noVotes = Integer.parseInt(cells.get(3).text());
                    Integer abstention = Integer.parseInt(cells.get(4).text());

                    result.setFinalVoteFor(yesVotes);
                    result.setFinalVoteAgainst(noVotes);
                    result.setFinalVoteAbst(abstention);
                });
    }

    private boolean isFinalVoteRow(Element tableRow) {
        Elements cells = tableRow.getElementsByTag("td");

        if (cells.size() != 6) {
            return false;
        }

        String voteLabel = cells.get(1).text().toLowerCase().trim();

        List<String> finalVoteLabels = List.of(
                "önálló indítvány elfogadva",
                "önálló indítvány elutasítva",
                "önálló indítvány egyszerű többséget igénylő része elfogadva",
                "önálló indítvány egyszerű többséget igénylő része elutasítva"
        );

        return finalVoteLabels.contains(voteLabel);
    }

}
