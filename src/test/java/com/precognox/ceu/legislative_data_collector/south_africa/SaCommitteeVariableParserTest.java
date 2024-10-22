package com.precognox.ceu.legislative_data_collector.south_africa;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.south_africa.parsers.SaCommitteeVariablesParser;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class SaCommitteeVariableParserTest {
    private final PdfParser pdfParser = Mockito.mock(PdfParser.class);
    private final PrimaryKeyGeneratingRepository primaryKeyGeneratingRepository = Mockito.mock(
            PrimaryKeyGeneratingRepository.class);
    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);
    @InjectMocks
    private final SaPageCollector saPageCollector = Mockito.mock(SaPageCollector.class);

    private final SaCommitteeVariablesParser instance = new SaCommitteeVariablesParser(primaryKeyGeneratingRepository,
                                                                                       pageSourceRepository,
                                                                                       saPageCollector);

    @Test
    void test_bill_351() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-351.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/351/");
        PageSource testLawPageSource = getPageSourceObj("/south_africa/bill-351_lawPage.html");
        testLawPageSource.setPageUrl("https://www.gov.za/documents/civil-aviation-act");

        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);
        Mockito.when(pdfParser.tryPdfTextExtraction(any())).thenReturn(
                Optional.of(SaBillPageParserTest.getPdfTextFromTxt(
                        "src/test/resources/south_africa/bill-351_amendment-B73A-2008.txt")));
        Mockito.when(pageSourceRepository.findByPageUrl(any())).thenReturn(Optional.of(testBillPageSource));
        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        LegislativeDataRecord record = instance.parsePage(new LegislativeDataRecord());

        assertEquals("Transport", record.getCommittees().get(0).getName());
        assertEquals("2008-09-17", record.getCommittees().get(0).getDate().toString());
        assertEquals(6, record.getCommittees().get(0).getCommitteeHearingCount());
        assertEquals(1, record.getCommittees().get(0).getNumberOfPublicHearingsCommittee());
        assertEquals("NCOP Public Services", record.getCommittees().get(1).getName());
        assertEquals("2009-01-28", record.getCommittees().get(1).getDate().toString());
        assertEquals(2, record.getCommittees().get(1).getCommitteeHearingCount());
        assertEquals(0, record.getCommittees().get(1).getNumberOfPublicHearingsCommittee());

        assertEquals(8, record.getCommitteeHearingCount());

        assertEquals("Civil Aviation Bill [B73-2008]: public hearings",
                     record.getSouthAfricaCountrySpecificVariables().getPublicHearings().get(0).getHearingTitle());
        assertEquals("2008-10-14",
                     record.getSouthAfricaCountrySpecificVariables().getPublicHearings().get(0).getHearingDate()
                             .toString());
    }

    @Test
    void test_bill_1140() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-1140.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/1140/");

        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        Mockito.when(pdfParser.tryPdfTextExtraction(any())).thenReturn(Optional.of("any"));
        Mockito.when(pageSourceRepository.findByPageUrl(any())).thenReturn(Optional.of(testBillPageSource));
        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        LegislativeDataRecord record = instance.parsePage(new LegislativeDataRecord());

        assertEquals(0, record.getCommitteeCount());

    }

    @Test
    void test_bill_1055() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-1055.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/1055/");
        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        Mockito.when(pdfParser.tryPdfTextExtraction("")).thenReturn(Optional.of("any"));
        Mockito.when(pdfParser.tryPdfTextExtraction("https://pmg.org.za/files/SEIAS_Final_Assessment.pdf")).thenReturn(
                Optional.of(SaBillPageParserTest.getPdfTextFromTxt(
                        "src/test/resources/south_africa/bill-351_amendment-B73A-2008.txt")));
        Mockito.when(pageSourceRepository.findByPageUrl(any())).thenReturn(Optional.of(testBillPageSource));
        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        LegislativeDataRecord record = instance.parsePage(new LegislativeDataRecord());

        assertEquals(1, record.getCommitteeCount());
        assertEquals(21, record.getCommitteeHearingCount());
        assertEquals("Basic Education", record.getCommittees().get(0).getName());
        assertEquals("2022-02-08", record.getCommittees().get(0).getDate().toString());
        assertEquals(21, record.getCommittees().get(0).getCommitteeHearingCount());
        assertEquals(4, record.getCommittees().get(0).getNumberOfPublicHearingsCommittee());

        assertEquals(4, record.getSouthAfricaCountrySpecificVariables().getPublicHearings().size());
        assertEquals("Basic Education Laws Amendment Bill: public hearings",
                     record.getSouthAfricaCountrySpecificVariables().getPublicHearings().get(0).getHearingTitle());
        assertEquals("2022-11-08",
                     record.getSouthAfricaCountrySpecificVariables().getPublicHearings().get(0).getHearingDate()
                             .toString());
        assertEquals("2022-11-15",
                     record.getSouthAfricaCountrySpecificVariables().getPublicHearings().get(1).getHearingDate()
                             .toString());
        assertEquals("2022-11-22",
                     record.getSouthAfricaCountrySpecificVariables().getPublicHearings().get(2).getHearingDate()
                             .toString());
        assertEquals("2022-11-29",
                     record.getSouthAfricaCountrySpecificVariables().getPublicHearings().get(3).getHearingDate()
                             .toString());
    }
}
