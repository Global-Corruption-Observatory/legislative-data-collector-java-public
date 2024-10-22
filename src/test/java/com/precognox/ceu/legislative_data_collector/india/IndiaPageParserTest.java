package com.precognox.ceu.legislative_data_collector.india;

import com.precognox.ceu.legislative_data_collector.common.ResourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;

class IndiaPageParserTest {

    private final PageSourceRepository mock = Mockito.mock(PageSourceRepository.class);
    private final PrimaryKeyGeneratingRepository mock1 = Mockito.mock(PrimaryKeyGeneratingRepository.class);

    private IndiaPageParser parser =
            new IndiaPageParser(null, mock1, null);

    @Test
    void testPage31() throws IOException {
        Mockito.when(mock1.existsByCountryAndBillIdAndBillTitle(any(), any(), any())).thenReturn(false);

        PageSource pageSource = ResourceLoader.getPageSourceObj("/india/bill_list_page_31.html");

        List<LegislativeDataRecord> results = parser.processPage(pageSource).toList();

        String testBillUrl = "http://164.100.47.4/BillsTexts/RSBillTexts/Asintroduced/RP-E-21619.pdf";
        Optional<LegislativeDataRecord> testBill = results.stream()
                .filter(record -> record.getBillPageUrl().equals(testBillUrl))
                .findFirst();

        List<Originator> originators = testBill.get().getOriginators();
        assertFalse(originators.isEmpty());
        assertEquals(1, originators.size());
    }
}
