package com.precognox.ceu.legislative_data_collector.hungary;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Optional;

public class JsoupPageParser {

    public LegislativeDataRecord parseStoredSource(PageSource source) {
        Document parsedPage = Jsoup.parse(source.getRawSource());
        LegislativeDataRecord record = new LegislativeDataRecord(Country.HUNGARY);

        parseTitle(parsedPage, record);

        Optional.ofNullable(parsedPage.body().selectFirst("div.irom-adat"))
                .ifPresent(div -> new IromanyokAdataiTableParser(record, div).parseVariables());

        Optional.ofNullable(parsedPage.body().selectFirst("div.szavazasok"))
                .ifPresent(div -> new VotesTableParser(record, div).parse());

        Optional.ofNullable(parsedPage.body().selectFirst("div.irom-biz"))
                .ifPresent(div -> new CommitteesTableParser(record, div).parse());

        Optional.ofNullable(parsedPage.body().selectFirst("div.irom-esemenyek"))
                .ifPresent(div -> new StagesTableParser(record, div).parse());

        return record;
    }

    private void parseTitle(Document parsedPage, LegislativeDataRecord result) {
        Element cimDiv = parsedPage.body().selectFirst("div.irom-cim");

        if (cimDiv != null) {
            String billId = Optional.ofNullable(cimDiv.selectFirst("a"))
                    .map(Element::text)
                    .map(String::trim)
                    .orElse(null);

            String billTitle = cimDiv.getElementsByTag("td").get(1).text().trim();

            result.setBillId(billId);
            result.setBillTitle(billTitle);
        }
    }

    private void downloadBillText(LegislativeDataRecord record) {
        if (record.getBillTextUrl() != null) {
            if (record.getBillTextUrl().endsWith(".htm")) {
            }
        }
    }

}
