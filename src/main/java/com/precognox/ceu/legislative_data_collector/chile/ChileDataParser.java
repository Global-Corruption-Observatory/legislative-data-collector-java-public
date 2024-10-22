package com.precognox.ceu.legislative_data_collector.chile;

import com.precognox.ceu.legislative_data_collector.chile.recordbuilders.RecordBuilder;
import com.precognox.ceu.legislative_data_collector.chile.utils.AffectingLawsCalculatorChile;
import com.precognox.ceu.legislative_data_collector.chile.utils.OriginalLawCalculator;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.ChileDuplicateLawIdHandler;
import com.precognox.ceu.legislative_data_collector.utils.ReadDatabaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.JSON_TYPE_LAW;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_BILL;

@Slf4j
@Service
public class ChileDataParser {

    public static final boolean RECOLLECT_EVERYTHING = false;
    private static final int PROCESSING_PAGE_SIZE = 128;
    private PageSourceRepository pageRepository;
    private PrimaryKeyGeneratingRepository recordRepository;
    private AffectingLawsCalculatorChile affectingLawsCalculator;
    private ChileDuplicateLawIdHandler duplicateLawIdHandler;
    private ReadDatabaseService readService;

    @Autowired
    public ChileDataParser(
            PageSourceRepository pageRepository,
            PrimaryKeyGeneratingRepository recordRepository,
            AffectingLawsCalculatorChile affectingLawsCalculator,
            ChileDuplicateLawIdHandler duplicateLawIdHandler,
            ReadDatabaseService readService) {
        this.pageRepository = pageRepository;
        this.recordRepository = recordRepository;
        this.affectingLawsCalculator = affectingLawsCalculator;
        this.duplicateLawIdHandler = duplicateLawIdHandler;
        this.readService = readService;
    }

    public void parseData() {
        log.info("Reprocessing of records already in the database is {}", (RECOLLECT_EVERYTHING) ? "ENABLED" : "DISABLED");

        //the order of the steps is important here, the laws must be processed before the bills
        parseLaws();
        parseBills();
        processAffectingLaws();
    }

    private void parseLaws() {
        log.info("Processing laws...");
        runDataParsingForPageType(JSON_TYPE_LAW, RecordBuilder.LAW_BUILDER_CODE);
        duplicateLawIdHandler.handleDuplicateLawIds(Country.CHILE);
    }

    private void parseBills() {
        log.info("Processing bills...");
        runDataParsingForPageType(PAGE_TYPE_BILL, RecordBuilder.BILL_BUILDER_CODE);
    }

    private void runDataParsingForPageType(String pageType, int builderCode) {
        Pageable paging = PageRequest.of(0, PROCESSING_PAGE_SIZE, Sort.by("id").ascending());
        Page<PageSource> sources;

        do {
            sources = pageRepository.findByPageTypeAndCountry(pageType, Country.CHILE, paging);
            log.info("Processing {}-{} pages out of {}", paging.getPageNumber() * PROCESSING_PAGE_SIZE, (paging.getPageNumber() + 1) * PROCESSING_PAGE_SIZE, sources.getTotalElements());
            processPageSourceBatch(sources.getContent(), builderCode);
            paging = sources.nextPageable();
        } while (sources.hasNext());
    }

    private void processPageSourceBatch(List<PageSource> sources, int builderType) {
        sources.stream()
                .filter(source -> RECOLLECT_EVERYTHING || !recordRepository.existsByBillPageUrl(source.getPageUrl()))
                .peek(source -> log.info("Processing {}", source.getPageUrl()))
                .forEach(source -> processPageSource(source, builderType));
    }

    private void processPageSource(PageSource source, int builderType) {
        try {
            RecordBuilder builder = RecordBuilder.getRecordBuilder(builderType);
            builder.setReadService(readService);
            builder.tryBuildingRecord(source);

            recordRepository.save(builder.getRecord());
        } catch (DataCollectionException ex) {
            log.error(ex.getMessage());
        }
    }

    private void processAffectingLaws() {
        log.info("Processing law modifications...");

        Page<LegislativeDataRecord> records;
        Pageable paging = PageRequest.of(0, PROCESSING_PAGE_SIZE, Sort.by("id").ascending());

        do {
            records = affectingLawsCalculator.parseAffectingLaws(paging);
            log.info("Affecting records processed for {} of {}", (paging.getPageNumber() + 1) * paging.getPageSize(), records.getTotalElements());
            paging = records.nextPageable();
        } while (records.hasNext());

        log.info("Saving affecting records");
//        affectingLawsCalculator.calculateModifiedLaws();
    }

    @Transactional
    public void processOriginalLawVariableOnly() {
        OriginalLawCalculator originalLawCalculator = new OriginalLawCalculator();
        List<LegislativeDataRecord> bills = recordRepository.findByCountry(Country.CHILE);

        bills.stream()
                .peek(bill -> log.info("Setting original_law for record {}", bill.getRecordId()))
                .forEach(originalLawCalculator::fillOriginalLaw);
    }

}
