package com.precognox.ceu.legislative_data_collector.south_africa;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.south_africa.parsers.SaImpactAssessmentVariablesParser;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class SaImpactAssessmentVariableParserTest {
    private final PdfParser pdfParser = Mockito.mock(PdfParser.class);
    private final LegislativeDataRepository legislativeDataRepository = Mockito.mock(LegislativeDataRepository.class);
    private final TransactionTemplate transactionTemplate = Mockito.mock(TransactionTemplate.class);
    private final PrimaryKeyGeneratingRepository primaryKeyGeneratingRepository = Mockito.mock(
            PrimaryKeyGeneratingRepository.class);
    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);

    private final SaImpactAssessmentVariablesParser instance =
            new SaImpactAssessmentVariablesParser(
                    primaryKeyGeneratingRepository, pageSourceRepository, legislativeDataRepository, pdfParser,
                    transactionTemplate);

    @Test
    void test_bill_351() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-351.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/351/");
        PageSource testLawPageSource = getPageSourceObj("/south_africa/bill-351_lawPage.html");
        testLawPageSource.setPageUrl("https://www.gov.za/documents/civil-aviation-act");

        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        LegislativeDataRecord record = instance.parsePage(new LegislativeDataRecord());


        assertTrue(record.getImpactAssessments().isEmpty());
        assertEquals(Boolean.FALSE, record.getImpactAssessmentDone());
    }

    @Test
    void test_bill_1055() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-1055.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/1055/");
        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        Mockito.when(pdfParser.tryPdfTextExtraction("https://pmg.org.za/files/SEIAS_Final_Assessment.pdf"))
                .thenReturn(Optional.of(
                        SaBillPageParserTest.getPdfTextFromTxt(
                                "src/test/resources/south_africa/bill-351_amendment-B73A-2008.txt")));

        LegislativeDataRecord record = instance.parsePage(new LegislativeDataRecord());

        assertEquals(Boolean.TRUE, record.getImpactAssessmentDone());
        assertEquals("SEIAS Final Assessment", record.getImpactAssessments().get(0).getTitle());
        assertEquals("2022-02-22", record.getImpactAssessments().get(0).getDate().toString());
    }
}
