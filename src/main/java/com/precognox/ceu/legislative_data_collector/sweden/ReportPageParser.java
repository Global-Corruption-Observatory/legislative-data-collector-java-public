package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class ReportPageParser {

    private final PageSourceLoader pageSourceLoader;

    @Autowired
    public ReportPageParser(PageSourceLoader pageSourceLoader) {
        this.pageSourceLoader = pageSourceLoader;
    }

    public void processReportPage(Amendment amendment, String reportPageUrl) {
        Optional<PageSource> stored = pageSourceLoader.loadFromDbOrFetchWithHttpGet(
                Country.SWEDEN, PageType.REPORT.name(), reportPageUrl
        );

        stored.ifPresent(page -> {
            parseVotes(amendment, page);

            if (amendment.getDataRecord().getPlenarySize() == null) {
                amendment.getDataRecord().setPlenarySize(parsePlenarySize(page));
            }
        });
    }

    public void parseVotes(Amendment amendment, PageSource storedPage) {
        Element page = Jsoup.parse(storedPage.getRawSource()).body();

        Optional<Element> tableCaption =
                Optional.ofNullable(page.selectFirst("caption:contains(Omröstning i sakfrågan)"));

        if (tableCaption.isPresent()) {
            String tableHeader = tableCaption.get().text();

            Optional<Triple<Integer, Integer, Integer>> rawVotes = tableCaption
                    .map(Element::parent)
                    .filter(p -> "table".equals(p.tagName()))
                    .map(table -> table.selectFirst("tfoot"))
                    .map(tfoot -> tfoot.selectFirst("tr"))
                    .map(tr -> tr.getElementsByTag("td"))
                    .map(this::parseVotes);

            if (rawVotes.isPresent()) {
                Integer against;
                Integer inFavor;
                Integer abst;

                if (tableHeader.contains("mot reservation")) {
                    //invert votes
                    against = rawVotes.get().getLeft();
                    inFavor = rawVotes.get().getMiddle();
                    abst = rawVotes.get().getRight();
                } else {
                    inFavor = rawVotes.get().getLeft();
                    against = rawVotes.get().getMiddle();
                    abst = rawVotes.get().getRight();
                }

                amendment.setVotesInFavor(inFavor);
                amendment.setVotesAgainst(against);
                amendment.setVotesAbstention(abst);
            }
        }
    }

    private Triple<Integer, Integer, Integer> parseVotes(Elements cells) {
        int inFavor = Integer.parseInt(cells.get(0).text());
        int against = Integer.parseInt(cells.get(1).text());
        int abst = Integer.parseInt(cells.get(2).text()) + Integer.parseInt(cells.get(3).text());

        return Triple.of(inFavor, against, abst);
    }

    public Integer parsePlenarySize(PageSource storedPage) {
        Element page = Jsoup.parse(storedPage.getRawSource()).body();

        return page.select("div.RGYEJ")
                .stream()
                .map(Element::text)
                .mapToInt(TextUtils::getLengthWithoutWhitespace)
                .sum();
    }

}
