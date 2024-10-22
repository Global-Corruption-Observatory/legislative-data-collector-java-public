package com.precognox.ceu.legislative_data_collector.south_africa;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.south_africa.parsers.SaOriginatorVariableParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class SaOriginatorVariableParserTest {
    private final PrimaryKeyGeneratingRepository primaryKeyGeneratingRepository = Mockito.mock(
            PrimaryKeyGeneratingRepository.class);
    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);

    @Test
    void test_bill_351() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-351.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/351/");

        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        SaOriginatorVariableParser instance = new SaOriginatorVariableParser(pageSourceRepository,
                                                                             primaryKeyGeneratingRepository);
        LegislativeDataRecord record = instance.parsePage(new LegislativeDataRecord());

        assertEquals("GOVERNMENT", record.getOriginType().toString());

        assertEquals("Minister of Transport", record.getOriginators().get(0).getName());
        assertEquals("Minister of Transport", record.getOriginators().get(0).getAffiliation());
    }

    @Test
    void test_bill_1140() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-1140.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/1140/");

        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        SaOriginatorVariableParser instance = new SaOriginatorVariableParser(pageSourceRepository,
                                                                             primaryKeyGeneratingRepository);
        LegislativeDataRecord record = instance.parsePage(new LegislativeDataRecord());

        assertEquals("INDIVIDUAL_MP", record.getOriginType().toString());
        assertEquals("Dr L Schreiber", record.getOriginators().get(0).getName());
    }

    @Test
    void test_bill_1055() throws IOException {
        PageSource testBillPageSource = getPageSourceObj("/south_africa/bill-1055.html");
        testBillPageSource.setPageUrl("https://pmg.org.za/bill/1055/");

        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(testBillPageSource);

        SaOriginatorVariableParser instance = new SaOriginatorVariableParser(pageSourceRepository,
                                                                             primaryKeyGeneratingRepository);
        LegislativeDataRecord record = instance.parsePage(new LegislativeDataRecord());

        assertEquals("GOVERNMENT", record.getOriginType().toString());
        assertEquals("Minister of Basic Education", record.getOriginators().get(0).getName());
        assertEquals("Minister of Basic Education", record.getOriginators().get(0).getAffiliation());
    }
}
