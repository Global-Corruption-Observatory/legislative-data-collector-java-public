package com.precognox.ceu.legislative_data_collector.australia;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class AustraliaController implements ScrapingController {

    private AustraliaCollector australiaCollector;
    private AustraliaFixer australiaFixer;
    private AustraliaWeb2PageCollector australiaWeb2PageCollector;
    private AustraliaWeb2Parser australiaWeb2Parser;


    @Override
    public void runScraping(List<String> args) {
        australiaCollector.collectAndParseWithPythonDownloader();
        australiaFixer.fixAustraliaWebsite1Collection();
        australiaWeb2PageCollector.collectWeb2PageSources("/search/collection(act)/status(inforce)");
        australiaWeb2PageCollector.collectWeb2PageSources("/search/collection(act)/status(notinforce)");
        australiaWeb2Parser.parseAustraliaWeb2Pages();
    }
}
