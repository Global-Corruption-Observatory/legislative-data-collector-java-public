package com.precognox.ceu.legislative_data_collector.usa;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import com.precognox.ceu.legislative_data_collector.usa.parsers.UsaAmendmentVariablesParser;
import com.precognox.ceu.legislative_data_collector.usa.parsers.UsaBillPageParser;
import com.precognox.ceu.legislative_data_collector.usa.parsers.UsaCommitteesParser;
import com.precognox.ceu.legislative_data_collector.usa.parsers.UsaLawRelatedVariablesParser;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class UsaController implements ScrapingController {

    private final UsaAffectingLawsCalculator affectingLawsCalculator;
    private PageDownloader pageDownloader;
    private UsaCommitteesParser usaCommitteesParser;
    private UsaBillPageParser usaBillPageParser;
    private UsaLawRelatedVariablesParser usaLawRelatedVariablesParser;
    private UsaAmendmentVariablesParser usaAmendmentVariablesParser;

    @Override
    public void runScraping(List<String> args) {
        usaCommitteesParser.storeAllCommitteesPerPeriod();
        pageDownloader.saveBillPages();
        usaBillPageParser.parseAllPages();
        usaLawRelatedVariablesParser.parseAllPages();
        usaAmendmentVariablesParser.parseAllPages();
        affectingLawsCalculator.fillAffectingLaws();
    }

}
