package com.precognox.ceu.legislative_data_collector.south_africa;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.south_africa.parsers.SaLawRelatedVariablesParser;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class SaLawRelatedVariableParserTest {
    private final PrimaryKeyGeneratingRepository primaryKeyGeneratingRepository = Mockito.mock(
            PrimaryKeyGeneratingRepository.class);
    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);
    private final SaPageCollector saPageCollector = Mockito.mock(SaPageCollector.class);
    private final PdfParser pdfParser = Mockito.mock(PdfParser.class);

    private final SaLawRelatedVariablesParser instance = new SaLawRelatedVariablesParser(saPageCollector,
                                                                                         pageSourceRepository,
                                                                                         primaryKeyGeneratingRepository,
                                                                                         pdfParser);

    @Test
    void test_bill_410() throws IOException {
        String billPageUrl = "https://pmg.org.za/bill/410/";
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-410.html");
        testBillPageSource.setPageUrl(billPageUrl);

        String lawPageUrl = "https://www.gov.za/documents/taxation-laws-second-amendment-act-1";
        PageSource testLawPageSource = getPageSourceObj("/south_africa/bill-410_lawPage.html");
        testLawPageSource.setPageUrl(lawPageUrl);

        String affectingLawPageUrl = "https://www.gov.za/documents/tax-administration-act";
        PageSource testAffectingLawPageSource = getPageSourceObj("/south_africa/bill-410_affectingLawPage.html");
        testAffectingLawPageSource.setPageUrl(affectingLawPageUrl);

        String modifiedLawPageUrl = "https://www.gov.za/documents/income-tax-act-29-may-1962-0000";
        PageSource testModifiedLawPageSource = getPageSourceObj("/south_africa/bill-410_modifiedLawPage.html");
        testModifiedLawPageSource.setPageUrl(modifiedLawPageUrl);

        Mockito.when(pageSourceRepository.findByPageUrl(billPageUrl)).thenReturn(Optional.of(testBillPageSource));
        Mockito.when(pageSourceRepository.getByPageUrl(billPageUrl)).thenReturn(testBillPageSource);
        Mockito.when(pageSourceRepository.findByPageUrl(modifiedLawPageUrl)).thenReturn(
                Optional.of(testModifiedLawPageSource));
        Mockito.when(pageSourceRepository.findByPageUrl(affectingLawPageUrl)).thenReturn(
                Optional.of(testAffectingLawPageSource));
        Mockito.when(pageSourceRepository.findPageSourceByLawId(any(), any()))
                .thenReturn(Optional.of(testLawPageSource));

        LegislativeDataRecord record = new LegislativeDataRecord();
        record.setBillPageUrl(billPageUrl);
        record = instance.parsePage(record);

        assertEquals("PASS", record.getBillStatus().toString());
        assertEquals("Act 4 of 2008", record.getLawId());
        assertEquals("2008-06-03", record.getDatePassing().toString());
        assertEquals("2008-12-31", record.getDateEnteringIntoForce().toString());

        assertEquals(2, record.getAffectingLawsCount());
        assertEquals("2012-10-01", record.getAffectingLawsFirstDate().toString());
        assertEquals(1, record.getModifiedLawsCount());

        assertEquals(lawPageUrl, record.getSouthAfricaCountrySpecificVariables().getGovPageUrl());

        assertEquals("Taxation Laws Second Amendment Act 4 of 2008",
                     record.getSouthAfricaCountrySpecificVariables().getLawTitle());
    }

    @Test
    void test_bill_1140() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-1140.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/1140/");

        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        LegislativeDataRecord record = instance.parsePage(new LegislativeDataRecord());

        assertEquals("ONGOING", record.getBillStatus().toString());
        assertNull(record.getAffectingLawsCount());
        assertNull(record.getAffectingLawsFirstDate());
        assertNull(record.getModifiedLawsCount());
    }
}
