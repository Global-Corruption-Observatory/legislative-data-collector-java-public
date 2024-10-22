package com.precognox.ceu.legislative_data_collector.bulgaria;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class BulgariaController implements ScrapingController {

    private BillLinkCollector bgBillLinkCollector;
    private BillDownloader bgBillDownloader;
    private BillParser bgBillParser;

    @Autowired
    public BulgariaController(
            BillLinkCollector bgBillLinkCollector,
            BillDownloader bgBillDownloader,
            BillParser bgBillParser) {
        this.bgBillLinkCollector = bgBillLinkCollector;
        this.bgBillDownloader = bgBillDownloader;
        this.bgBillParser = bgBillParser;
    }

    @Override
    public void runScraping(List<String> args) {
        bgBillLinkCollector.downloadBillList();
        bgBillDownloader.downloadBillPages();
        bgBillParser.processStoredPages();
    }

}
