package com.precognox.ceu.legislative_data_collector.india;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import com.precognox.ceu.legislative_data_collector.india.new_website.ApiBillParser;
import com.precognox.ceu.legislative_data_collector.india.new_website.ApiResponseDownloader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class IndiaController implements ScrapingController {

    private final ApiResponseDownloader newWebsiteDownloader;
    private final ApiBillParser apiBillParser;
    private final IndiaAffectingLawsCalculator affectingLawsCalculator;
    private final BillAndLawTextDownloader billAndLawTextDownloader;
    private final IndiaDatasetTester indiaDatasetTester;

    @Autowired
    public IndiaController(
            ApiResponseDownloader newWebsiteDownloader,
            BillAndLawTextDownloader billAndLawTextDownloader,
            ApiBillParser apiBillParser,
            IndiaAffectingLawsCalculator affectingLawsCalculator,
            IndiaDatasetTester indiaDatasetTester) {
        this.indiaDatasetTester = indiaDatasetTester;
        this.newWebsiteDownloader = newWebsiteDownloader;
        this.billAndLawTextDownloader = billAndLawTextDownloader;
        this.apiBillParser = apiBillParser;
        this.affectingLawsCalculator = affectingLawsCalculator;
    }

    @Override
    public void runScraping(List<String> args) {
        newWebsiteDownloader.downloadPages();
        apiBillParser.parseAllBills();
        affectingLawsCalculator.collectAllAffectingLaws();
        billAndLawTextDownloader.downloadBillAndLawTexts();
        indiaDatasetTester.runTests();
    }

}
