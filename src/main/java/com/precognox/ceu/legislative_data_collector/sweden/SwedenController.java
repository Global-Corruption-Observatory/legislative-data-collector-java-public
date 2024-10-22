package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import com.precognox.ceu.legislative_data_collector.common.BillAndLawTextCollector;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Contains the main steps of the processing.
 */
@Slf4j
@Service
public class SwedenController implements ScrapingController {

    @Autowired private SwedenBillUrlCollector billUrlCollector;
    @Autowired private SwedenBillPageCollector billPageCollector;
    @Autowired private SwedenBillPageParser billPageParser;
    @Autowired private SuggestionsPageParser suggestionsPageParser;
    @Autowired private AmendmentPageParser amendmentPageParser;
    @Autowired private AffectingLawsParser affectingLawsParser;
    @Autowired private BillAndLawTextCollector billAndLawTextCollector;
    @Autowired private BillTextParser billTextParser;
    @Autowired private LawTextCollector lawTextCollector;
    @Autowired private IaTextParser iaTextParser;
    @Autowired private AmendmentTextCollector amendmentTextCollector;
    @Autowired private DatasetTester datasetTester;
    @Autowired private LawPageParser lawPageParser;

    @Override
    public void runScraping(List<String> args) {
        billAndLawTextCollector.setCollectionMode(BillAndLawTextCollector.PdfCollectionMode.HTTP_GET);

        if (args.contains("billUrlCollector")) billUrlCollector.collectLinks();
        if (args.contains("billPageCollector")) billPageCollector.collectAllPages();
        if (args.contains("billPageParser")) billPageParser.parseAllPages();
        if (args.contains("reprocessAllRecords")) billPageParser.reprocessAllRecords();
        if (args.contains("lawPageParser")) lawPageParser.parseAllPages();
        if (args.contains("suggestionsPageParser")) suggestionsPageParser.processRecords();
        if (args.contains("amendmentPageParser")) amendmentPageParser.processAllRecords();
        if (args.contains("affectingLawsParser")) affectingLawsParser.processRecords();
        if (args.contains("billAndLawTextCollector")) billAndLawTextCollector.collectBillTexts(Country.SWEDEN);
        if (args.contains("billTextParser")) billTextParser.processAllRecords();
        if (args.contains("iaTextParser")) iaTextParser.processAllRecords();
        if (args.contains("lawTextCollector")) lawTextCollector.processAllRecords();
        if (args.contains("amendmentTextCollector")) amendmentTextCollector.processAllRecords();
        if (args.contains("datasetTester")) datasetTester.runSwedenChecks();

        if (args.contains("reprocessOriginators")) billPageParser.reprocessOriginators();
    }

}
