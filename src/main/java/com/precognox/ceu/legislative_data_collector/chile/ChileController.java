package com.precognox.ceu.legislative_data_collector.chile;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import com.precognox.ceu.legislative_data_collector.chile.recordbuilders.BillRecordBuilder;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ChileController implements ScrapingController {

    private ChileSourceCollector chileSourceCollector;
    private ChileDataParser chileDataParser;

    @Autowired
    public ChileController(ChileSourceCollector chileSourceCollector, ChileDataParser chileDataParser) {
        this.chileSourceCollector = chileSourceCollector;
        this.chileDataParser = chileDataParser;
    }

    @Override
    public void runScraping(List<String> args) {
        Unirest.config().socketTimeout(10000);
        chileSourceCollector.collectSources();
        chileDataParser.parseData();
        log.info("Unhandled bill status texts are: " + BillRecordBuilder.UNHANDLED_BILL_STATUSES);
    }

}
