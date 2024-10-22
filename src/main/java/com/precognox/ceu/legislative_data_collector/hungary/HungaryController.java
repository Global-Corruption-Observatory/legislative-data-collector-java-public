package com.precognox.ceu.legislative_data_collector.hungary;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import com.precognox.ceu.legislative_data_collector.hungary.tests.HungaryDatasetTester;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class HungaryController implements ScrapingController {

    private final AffectingLawsCalculator affectingLawsCalculator;
    private final BillUrlCollector billUrlCollector;
    private final PageSourceDownloader pageSourceDownloader;
    private final PageSourceParser pageSourceParser;
    private final AmendmentCollector amendmentCollector;
    private final DebateSizeCollector debateSizeCollector;
    private final HungaryDatasetTester huDatasetTester;
    private final ModifiedLawParser modifiedLawParser;

    @Autowired
    public HungaryController(
            AffectingLawsCalculator affectingLawsCalculator,
            BillUrlCollector billUrlCollector,
            PageSourceDownloader pageSourceDownloader,
            PageSourceParser pageSourceParser,
            AmendmentCollector amendmentCollector,
            DebateSizeCollector debateSizeCollector,
            HungaryDatasetTester huDatasetTester,
            ModifiedLawParser modifiedLawParser) {
        this.affectingLawsCalculator = affectingLawsCalculator;
        this.billUrlCollector = billUrlCollector;
        this.pageSourceDownloader = pageSourceDownloader;
        this.pageSourceParser = pageSourceParser;
        this.amendmentCollector = amendmentCollector;
        this.debateSizeCollector = debateSizeCollector;
        this.huDatasetTester = huDatasetTester;
        this.modifiedLawParser = modifiedLawParser;
    }

    @Override
    public void runScraping(List<String> args) {
        billUrlCollector.collectLinks();
        pageSourceDownloader.downloadPages();
        pageSourceParser.processStoredPages();
        amendmentCollector.collectAllAmendments();
        modifiedLawParser.processAllRecords();
        affectingLawsCalculator.fillAffectingLaws();
        debateSizeCollector.collectDebateSizes();
        huDatasetTester.runTests();
    }

}
