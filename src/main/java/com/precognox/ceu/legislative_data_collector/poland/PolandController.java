package com.precognox.ceu.legislative_data_collector.poland;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class PolandController implements ScrapingController {
    private final PolandDataCollector polandDataCollector;
    private final PolandRecordBuilder polandRecordBuilder;

    @Autowired
    public PolandController(PolandDataCollector polandDataCollector, PolandRecordBuilder polandRecordBuilder) {
        this.polandDataCollector = polandDataCollector;
        this.polandRecordBuilder = polandRecordBuilder;
    }

    @Override
    public void runScraping(List<String> args) {
        polandDataCollector.runCollection();
        polandRecordBuilder.buildDataRecords();
        polandRecordBuilder.parseBillTextsAndOriginators();
    }
}
