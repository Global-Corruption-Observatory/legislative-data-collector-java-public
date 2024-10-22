package com.precognox.ceu.legislative_data_collector.hungary;

import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;

@Service
public class BillTextDownloader {

    @Autowired
    private LegislativeDataRepository repository;

    public void downloadBillTexts() {
        //handle html links first
    }

    @Nullable
    public String parseBillTextLink(String rawPageSource) {
        Document document = Jsoup.parse(rawPageSource);
        return document.body().getElementsByTag("a")
                .stream()
                .filter(link -> "A TÖRVÉNYJAVASLAT".equalsIgnoreCase(link.text())
                        || "a törvényjavaslat szövege".equalsIgnoreCase(link.text()))
                .findFirst()
                .map(element -> element.attr("href"))
                .orElse(null);
    }

}
