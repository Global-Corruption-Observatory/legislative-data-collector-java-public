package com.precognox.ceu.legislative_data_collector.colombia;

import com.precognox.ceu.legislative_data_collector.colombia.affecting.DuplicateLawIdHandler;
import com.precognox.ceu.legislative_data_collector.colombia.constants.PageType;
import com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.GazetteWebpageHandler;
import com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.OriginatorInformationCollector;
import com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.RecordBuilder;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.repositories.DownloadedFileRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.repositories.TextSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.ReadDatabaseService;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * This class handles transactions to build the records and the tidy up work after those are saved
 */
@Slf4j
@Service
public class ColombiaDataParser {
    private static final int PROCESSED_PAGES_SIZE = 5;
    public static List<Document> votePages;

    private final DuplicateLawIdHandler duplicateLawIdHandler;
    private final ReadDatabaseService readService;
    private final PageSourceRepository pageRepository;
    private final PrimaryKeyGeneratingRepository dataRepository;
    private final TextSourceRepository textRepository;

    private final DownloadedFileRepository fileRepository;

    private final PlatformTransactionManager transactionManager;

    @Autowired
    public ColombiaDataParser(
            DuplicateLawIdHandler duplicateLawIdHandler,
            ReadDatabaseService readService,
            PageSourceRepository pageRepository,
            PrimaryKeyGeneratingRepository dataRepository,
            TextSourceRepository textRepository,
            DownloadedFileRepository fileRepository,
            PlatformTransactionManager transactionManager) {
        this.duplicateLawIdHandler = duplicateLawIdHandler;
        this.readService = readService;
        this.pageRepository = pageRepository;
        this.dataRepository = dataRepository;
        this.textRepository = textRepository;
        this.fileRepository = fileRepository;
        this.transactionManager = transactionManager;
    }

    // Only for testing logic before processing all bills
    public void processOneBillPage(WebDriverWrapper browser) throws DataCollectionException {
        votePages = pageRepository.getPageSourcesByPageTypeAndCountry(PageType.VOTES.label, Country.COLOMBIA)
                .stream()
                .map(PageSource::getRawSource)
                .map(Jsoup::parse)
                .toList();
        String url =
                "https://congresovisible.uniandes.edu.co/proyectos-de-ley/ppor-medio-de-la-cual-se-permite-el-divorcio-por-la-sola-voluntad-de-cualquiera-de-los-dos-conyuges-y-se-dictan-otras-disposiciones-divorcio-por-voluntad-de-cualquiera-de-las-partes/13131";
        Optional<PageSource> optSource = pageRepository.findByPageTypeAndPageUrl(PageType.BILL.label, url);
        PageSource pageSource = optSource.get();
        RecordBuilder recordBuilder = new RecordBuilder(pageSource, readService);
        recordBuilder.buildRecord(browser);
        LegislativeDataRecord dataRecord = recordBuilder.getDataRecord();
        log.info("Data record with bill ID -{}- is processed successfully", dataRecord.getBillId());
    }

    public void parseBillPages(WebDriverWrapper browser) throws DataCollectionException {
        votePages = pageRepository.getPageSourcesByPageTypeAndCountry(PageType.VOTES.label, Country.COLOMBIA)
                .stream()
                .map(PageSource::getRawSource)
                .map(Jsoup::parse)
                .toList();
        if (votePages.isEmpty()) {
            log.error("No vote pages in database!");
        }

        Pageable paging = PageRequest.of(0, PROCESSED_PAGES_SIZE, Sort.by("id").ascending());
        Page<PageSource> billPages;
        do {
            billPages = pageRepository.findUnprocessedBillsColombia(paging, Country.COLOMBIA);
            billPages.forEach(billPage -> {
                try {
                    processBillPage(billPage, browser);
                } catch (DataCollectionException e) {
                    log.error("Bill -{}- did not parsed successfully. See log for the error: ", billPage.getId(), e);
                }
            });

            log.info("Collection done for {} of {}. Saving pages", (paging.getPageNumber() + 1) * paging.getPageSize(),
                    billPages.getTotalElements());
            OriginatorInformationCollector.savePagesToDatabase(pageRepository);
            GazetteWebpageHandler.savePagesToDatabase(textRepository, fileRepository);
            paging = billPages.nextPageable();
            log.info("Bill pages processed and saved");

        } while (billPages.hasNext());

        log.info("Dealing with duplicate law numbers - start");
        duplicateLawIdHandler.deleteDuplicatedLawIds(Country.COLOMBIA);
        log.info("Dealing with duplicate law numbers - finished");
    }

    private void processBillPage(PageSource billPage, WebDriverWrapper browser) throws DataCollectionException {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                try {
                    RecordBuilder builder = new RecordBuilder(billPage, readService);
                    builder.buildRecord(browser);
                    if (Objects.nonNull(builder.getDataRecord())) {
                        dataRepository.save(builder.getDataRecord());
                    }
                    pageRepository.saveAll(builder.getSources());
                    return status;
                } catch (Exception ex) {
                    log.error(ex.getMessage());
                    return null;
                }
            });
        } catch (Exception ex) {
            throw new DataCollectionException("Error in transaction", ex);
        }
    }
}