package com.precognox.ceu.legislative_data_collector.south_africa;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.south_africa.parsers.SaAmendmentVariablesParser;
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
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class SaAmendmentVariableParserTest {
    private final PdfParser pdfParser = Mockito.mock(PdfParser.class);
    private final LegislativeDataRepository legislativeDataRepository = Mockito.mock(LegislativeDataRepository.class);
    private final PrimaryKeyGeneratingRepository primaryKeyGeneratingRepository = Mockito.mock(
            PrimaryKeyGeneratingRepository.class);
    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);
    private final TransactionTemplate transactionTemplate = Mockito.mock(TransactionTemplate.class);

    @Test
    void test_bill_351() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-351.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/351/");

        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);
        Mockito.when(pdfParser.tryPdfTextExtraction(any())).thenReturn(
                Optional.of(SaBillPageParserTest.getPdfTextFromTxt(
                        "src/test/resources/south_africa/bill-351_amendment-B73A-2008.txt")));

        SaAmendmentVariablesParser instance = new SaAmendmentVariablesParser(primaryKeyGeneratingRepository,
                pageSourceRepository,
                legislativeDataRepository, pdfParser, transactionTemplate);
        LegislativeDataRecord record = instance.parsePage(new LegislativeDataRecord());

        assertEquals("B73A-2008", record.getAmendments().get(0).getAmendmentId());
        assertEquals("LOWER", record.getAmendments().get(0).getPlenary().toString());
        assertEquals("Portfolio Committee on Transport", record.getAmendments().get(0).getCommitteeName());
        assertEquals("https://pmg.org.za/files/bills/081119b73a-08.pdf",
                record.getAmendments().get(0).getTextSourceUrl());
    }
}
