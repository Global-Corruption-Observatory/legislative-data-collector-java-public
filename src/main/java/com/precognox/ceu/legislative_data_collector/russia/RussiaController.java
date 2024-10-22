package com.precognox.ceu.legislative_data_collector.russia;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RussiaController implements ScrapingController {

    private RussiaSourceUpdater russiaSourceUpdater;
    private RussiaFixer russiaFixer;
    private RussiaDataCollector russiaDataCollector;

    @Autowired
    public RussiaController(RussiaSourceUpdater russiaSourceUpdater, RussiaFixer russiaFixer, RussiaDataCollector russiaDataCollector) {
        this.russiaDataCollector = russiaDataCollector;
        this.russiaSourceUpdater = russiaSourceUpdater;
        this.russiaFixer = russiaFixer;
    }

    @Override
    public void runScraping(List<String> args) {
        russiaDataCollector.runCollectionAndParsing();
        russiaFixer.removeDuplicateRecords();
        russiaSourceUpdater.updateSources();
        russiaFixer.reprocessAllRecords();
        russiaFixer.fixAllBillTexts();
        russiaFixer.fixAllLawTexts();
        russiaFixer.collectAmendmentTexts();
    }

}
