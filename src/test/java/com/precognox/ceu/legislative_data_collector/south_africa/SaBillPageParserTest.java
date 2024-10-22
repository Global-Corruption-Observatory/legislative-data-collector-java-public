package com.precognox.ceu.legislative_data_collector.south_africa;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.south_africa.parsers.SaBillPageParser;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;
import java.util.Scanner;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class SaBillPageParserTest {
    private final PdfParser pdfParser = Mockito.mock(PdfParser.class);
    private final PrimaryKeyGeneratingRepository primaryKeyGeneratingRepository = Mockito.mock(
            PrimaryKeyGeneratingRepository.class);
    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);
    @InjectMocks
    private final SaPageCollector saPageCollector = Mockito.mock(SaPageCollector.class);

    private final SaBillPageParser instance = new SaBillPageParser(pageSourceRepository, primaryKeyGeneratingRepository,
                                                                   saPageCollector, pdfParser);

    public static String getPdfTextFromTxt(String textUrl) throws FileNotFoundException {
        File file = new File(textUrl);
        Scanner sc = new Scanner(file);
        sc.useDelimiter("\\Z");
        return sc.next();
    }

    @Test
    void test_bill_351() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-351.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/351/");

        Mockito.when(pageSourceRepository.findByPageUrl(any())).thenReturn(Optional.of(testBillPageSource));
        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        LegislativeDataRecord record = instance.parsePage(testBillPageSource);

        //Bill page
        assertEquals("B73-2008", record.getBillId());
        assertEquals("Civil Aviation Bill", record.getBillTitle());
        assertEquals("REGULAR", record.getProcedureTypeStandard().toString());
        assertEquals("Section 75: Ordinary Bills not affecting the provinces", record.getProcedureTypeNational());
        assertEquals(Boolean.TRUE, record.getOriginalLaw());
        assertEquals("https://pmg.org.za/files/bills/080909b73-08.pdf", record.getBillTextUrl());
        assertEquals("2008-09-07", record.getDateIntroduction().toString());

        assertEquals(2, record.getStagesCount());
        assertEquals(1, record.getStages().get(0).getStageNumber());
        assertEquals("2008-09-07", record.getStages().get(0).getDate().toString());
        assertEquals("National Assembly", record.getStages().get(0).getName());
        assertEquals(2, record.getStages().get(1).getStageNumber());
        assertEquals("2009-01-28", record.getStages().get(1).getDate().toString());
        assertEquals("National Council of Provinces", record.getStages().get(1).getName());
    }

    @Test
    void test_bill_1140() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-1140.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/1140/");

        Mockito.when(pdfParser.tryPdfTextExtraction(any())).thenReturn(Optional.of("any"));

        LegislativeDataRecord record = instance.parsePage(testBillPageSource);

        //Then
        assertEquals("B8-2023", record.getBillId());
        assertEquals("Remuneration of Public Office Bearers Amendment Bill", record.getBillTitle());
        assertEquals("REGULAR", record.getProcedureTypeStandard().toString());
        assertEquals("Private Member Bill: Section 75", record.getProcedureTypeNational());
        assertEquals(Boolean.FALSE, record.getOriginalLaw());
        assertEquals("https://pmg.org.za/files/B8-2023_Remuneration_of_Public_Office_Bearers.pdf",
                     record.getBillTextUrl());

        assertEquals("2023-04-20", record.getDateIntroduction().toString());
        assertEquals(1, record.getStages().get(0).getStageNumber());
        assertEquals("2023-04-20", record.getStages().get(0).getDate().toString());
        assertEquals("National Assembly", record.getStages().get(0).getName());
    }

    @Test
    void test_bill_1055() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-1055.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/1055/");

        Mockito.when(pdfParser.tryPdfTextExtraction("https://pmg.org.za/files/SEIAS_Final_Assessment.pdf")).thenReturn(
                Optional.of(getPdfTextFromTxt("src/test/resources/south_africa/bill-351_amendment-B73A-2008.txt")));
        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        LegislativeDataRecord record = instance.parsePage(testBillPageSource);

        assertEquals("B2-2022", record.getBillId());
        assertEquals("Basic Education Laws Amendment Bill", record.getBillTitle());
    }
}
