package com.precognox.ceu.legislative_data_collector.common;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.change_detector.PageSourceDiff;
import com.precognox.ceu.legislative_data_collector.entities.change_detector.PageSourceDiffResults;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.change_detector.PageSourceDiffRepository;
import com.precognox.ceu.legislative_data_collector.repositories.change_detector.PageSourceDiffResultsRepository;
import com.precognox.ceu.legislative_data_collector.south_africa.SaPageType;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.transaction.Transactional;
import java.util.LinkedList;
import java.util.stream.Stream;

@Service
public class ChangeDetector {

    private final PageSourceRepository pageSourceRepository;
    private final PageSourceDiffRepository pageSourceDiffRepository;
    private final PageSourceDiffResultsRepository pageSourceDiffResultsRepository;
    private final TransactionTemplate transactionTemplate;
    private int sizeDiff = 0;
    private int affectedPages = 0;

    @Autowired
    public ChangeDetector(PageSourceRepository pageSourceRepository, PageSourceDiffRepository pageSourceDiffRepository, PageSourceDiffResultsRepository pageSourceDiffResultsRepository, TransactionTemplate transactionTemplate) {
        this.pageSourceRepository = pageSourceRepository;
        this.pageSourceDiffRepository = pageSourceDiffRepository;
        this.pageSourceDiffResultsRepository = pageSourceDiffResultsRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public void printUpdatedBills() {
        //filter by page type also
        //test with known changed pages

        SaPageType pageType = SaPageType.ACT;
        Country country = Country.SOUTH_AFRICA;

        Stream<PageSource> pages = pageSourceRepository.streamAllByPageType(country, pageType.name());

        pages.forEach(stored -> {
            HttpResponse<String> resp = Unirest.get(stored.getPageUrl()).asString();

            if (resp.isSuccess()) {
                System.out.println("URL: " + stored.getPageUrl());
                System.out.println("Stored size: " + stored.getSize());
                System.out.println("Actual body length: " + resp.getBody().length());
                System.out.println("Diff: " + Math.abs(stored.getSize() - resp.getBody().length()));

                Document storedParsed = Jsoup.parse(stored.getRawSource());
                Document newParsed = Jsoup.parse(resp.getBody());

                int newSize = newParsed.body().select("main#content").text().length();
                int oldSize = storedParsed.body().select("main#content").text().length();

//                int newSize = newParsed.body().text().length();
//                int oldSize = storedParsed.body().text().length();
                sizeDiff = Math.abs(newSize - oldSize);
                if (sizeDiff != 0) affectedPages++;
                System.out.println("Text size diff: " + sizeDiff);

                getTextDiff(newParsed.body().text(), storedParsed.body().text(), stored.getPageUrl(), country, pageType.name());
                System.out.println();
            }
        });
        transactionTemplate.execute(status -> pageSourceDiffResultsRepository.save(new PageSourceDiffResults(country, pageType.name(), affectedPages)));
    }

    public void getTextDiff(String newPage, String storedPage, String pageUrl, Country country, String pageType) {
        DiffMatchPatch dmp = new DiffMatchPatch();
        LinkedList<DiffMatchPatch.Diff> diffs = dmp.diffMain(storedPage, newPage, false);

        dmp.diffCleanupSemantic(diffs);

        System.out.println(diffs);
        String diffOperation;
        String diffText;

        for (int i = 0; i < diffs.size(); i++)
            if (!diffs.get(i).operation.name().equals("EQUAL")) {
                PageSourceDiff pageSourceDiff = new PageSourceDiff();
                pageSourceDiff.setCountry(country);
                pageSourceDiff.setPageType(pageType);
                pageSourceDiff.setPageUrl(pageUrl);
                diffOperation = diffs.get(i).operation.name();
                diffText = diffs.get(i).text;

//                Analyze difference patterns
                if (diffs.size() >= 3) {
//                If we can match the following pattern of diff operations 'EQUAL'+'INSERT'+'EQUAL' it means that something was added to the current page
//                which wasn't present in the stored one.
//                In order to get a more clear picture of the differences we store these cases with the prior 'EQUAL' text,
//                because it can happen that the 'INSERT' tag contains only one character
                    if (diffs.get(i - 1).operation.name().equals("EQUAL") &
                            diffs.get(i).operation.name().equals("INSERT") &
                            diffs.get(i + 1).operation.name().equals("EQUAL")) {
                        diffOperation = "INSERT";
//                        diffText = diffs.get(i - 1).text + " |-> " + diffs.get(i).text;
                        diffText = diffs.get(i).text;
                    }

//                If we can match the following pattern of diff operations 'EQUAL'+'DELETE'+'INSERT' it means that something
//                changed in the stored page.
//                In these cases we only store the value of the 'INSERT' tag with the prior 'EQUAL' tag and skip the following
//                'INSERT' tag because we already processed its value
                    if (diffs.get(i - 1).operation.name().equals("EQUAL") &
                            diffs.get(i).operation.name().equals("DELETE") &
                            diffs.get(i + 1).operation.name().equals("INSERT")) {
                        diffOperation = "INSERT_CHANGE";
//                        diffText = diffs.get(i - 1).text + " |-> " + diffs.get(i + 1).text;
                        diffText = diffs.get(i + 1).text;
                        i++;
                    }
                }
                pageSourceDiff.setDiffOperation(diffOperation);
                pageSourceDiff.setDiffText(diffText.trim());
                transactionTemplate.execute(status -> pageSourceDiffRepository.save(pageSourceDiff));
            }
    }

    public interface PageUrlAndSize {
        String getPageUrl();
        Integer getSize();
    }
}

//real possible changes:
// 8000 byte https://www.riksdagen.se/sv/dokument-och-lagar/dokument/betankande/en-ny-biobankslag_ha01sou4/
