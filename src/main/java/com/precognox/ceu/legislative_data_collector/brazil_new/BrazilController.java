package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import com.precognox.ceu.legislative_data_collector.common.BillAndLawTextCollector;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class BrazilController implements ScrapingController {

    private final LexMlBillListCollector lexMlBillListCollector;
    private final LexMlBillCollector lexMlBillCollector;
    private final LexMlPageParser lexMlPageParser;
    private final CamaraPageDownloader camaraPageDownloader;
    private final CamaraPageParser camaraPageParser;
    private final StagesPageParser stagesPageParser;
    private final SenadoPageParser senadoPageParser;
    private final BrAmendmentCollector amendmentCollector;
    private final BrAmendmentTextDownloader amendmentTextDownloader;
    private final BrLawTextCollector lawTextCollector;
    private final BillAndLawTextCollector billAndLawTextCollector;
    private final IaCollector iaCollector;
    private final FinalVotesCollector finalVotesCollector;

    @Autowired
    public BrazilController(
            LexMlBillListCollector lexMlBillListCollector,
            LexMlBillCollector lexMlBillCollector,
            LexMlPageParser lexMlPageParser,
            CamaraPageDownloader camaraPageDownloader,
            CamaraPageParser camaraPageParser,
            StagesPageParser stagesPageParser,
            SenadoPageParser senadoPageParser,
            BrAmendmentCollector amendmentCollector,
            BrAmendmentTextDownloader amendmentTextDownloader,
            BrLawTextCollector lawTextCollector,
            BillAndLawTextCollector billAndLawTextCollector,
            IaCollector iaCollector,
            FinalVotesCollector finalVotesCollector) {
        this.lexMlBillListCollector = lexMlBillListCollector;
        this.lexMlBillCollector = lexMlBillCollector;
        this.lexMlPageParser = lexMlPageParser;
        this.camaraPageDownloader = camaraPageDownloader;
        this.camaraPageParser = camaraPageParser;
        this.stagesPageParser = stagesPageParser;
        this.senadoPageParser = senadoPageParser;
        this.amendmentCollector = amendmentCollector;
        this.amendmentTextDownloader = amendmentTextDownloader;
        this.lawTextCollector = lawTextCollector;
        this.billAndLawTextCollector = billAndLawTextCollector;
        this.iaCollector = iaCollector;
        this.finalVotesCollector = finalVotesCollector;
    }

    @Override
    public void runScraping(List<String> args) {
        Unirest.config().verifySsl(false); //needed to download law texts in a later step

        if (args.contains("lexMlBillListCollector")) lexMlBillListCollector.collectLinks();
        if (args.contains("lexMlBillCollector")) lexMlBillCollector.downloadAll();
        if (args.contains("lexMlPageParser")) lexMlPageParser.processAll();
        if (args.contains("camaraPageDownloader")) camaraPageDownloader.downloadAll();
        if (args.contains("camaraPageParser")) camaraPageParser.processAll();
        if (args.contains("amendmentCollector")) amendmentCollector.processAll();
        if (args.contains("senadoPageParser")) senadoPageParser.processAll();
        if (args.contains("amendmentTextDownloader")) amendmentTextDownloader.processAmendmentTexts();
        if (args.contains("stagesPageParser")) stagesPageParser.parseForAllBills();
        if (args.contains("lawTextCollector")) lawTextCollector.collectAll();
        if (args.contains("billAndLawTextCollector")) billAndLawTextCollector.collectBillTexts(Country.BRAZIL);
        if (args.contains("iaCollector")) iaCollector.collectAll();
        if (args.contains("finalVotesCollector")) finalVotesCollector.collectForAllBills();
    }

}
