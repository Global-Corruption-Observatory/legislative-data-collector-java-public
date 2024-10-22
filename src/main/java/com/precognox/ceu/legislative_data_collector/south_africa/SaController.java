package com.precognox.ceu.legislative_data_collector.south_africa;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import com.precognox.ceu.legislative_data_collector.common.BillAndLawTextCollector;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.south_africa.parsers.SaAmendmentVariablesParser;
import com.precognox.ceu.legislative_data_collector.south_africa.parsers.SaBillPageParser;
import com.precognox.ceu.legislative_data_collector.south_africa.parsers.SaCommitteeVariablesParser;
import com.precognox.ceu.legislative_data_collector.south_africa.parsers.SaImpactAssessmentVariablesParser;
import com.precognox.ceu.legislative_data_collector.south_africa.parsers.SaLawRelatedVariablesParser;
import com.precognox.ceu.legislative_data_collector.south_africa.parsers.SaOriginatorVariableParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SaController implements ScrapingController {
    @Autowired
    private SaPageCollector saPageCollector;
    @Autowired
    private SaLawIdParser saLawIdParser;
    @Autowired
    private SaBillPageParser saBillPageParser;
    @Autowired
    private BillAndLawTextCollector billAndLawTextCollector;
    @Autowired
    private SaOriginatorVariableParser saOriginatorVariableParser;
    @Autowired
    private SaLawRelatedVariablesParser saLawRelatedVariablesParser;
    @Autowired
    private SaCommitteeVariablesParser saCommitteeVariablesParser;
    @Autowired
    private SaImpactAssessmentVariablesParser saImpactAssessmentVariablesParser;
    @Autowired
    private SaAmendmentVariablesParser saAmendmentVariablesParser;

    @Override
    public void runScraping(List<String> args) {
        saPageCollector.collectPages();
        saLawIdParser.parseLawId();
        saBillPageParser.parseAllPages();
        billAndLawTextCollector.collectBillTexts(Country.SOUTH_AFRICA);
        saOriginatorVariableParser.parseAllPages();
        saLawRelatedVariablesParser.parseAllPages();
        saCommitteeVariablesParser.parseAllPages();
        saImpactAssessmentVariablesParser.parseAllPages();
        saAmendmentVariablesParser.parseAllPages();
    }
}
