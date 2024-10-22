package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.ResourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LexMlPageParserTest {

    @Test
    void testBill7508_2002() throws IOException {
        String testPage =
                ResourceLoader.getResourceAsString("/brazil/test_pages/lexml_bill_detail_page_7508_2002.html");

        PageSource source = new PageSource();
        source.setPageUrl("https://www.lexml.gov.br/urn/urn:lex:br:camara.deputados:projeto.lei;pl:2002-12-26;7508");
        source.setRawSource(testPage);

        LexMlPageParser parser = new LexMlPageParser(null, null);
        LegislativeDataRecord result = parser.parsePage(source);

        Assertions.assertAll(
                () -> assertNotNull(result),
                () -> assertEquals("7508/2002", result.getBillId()),
                () -> assertEquals("Projeto de Lei (CD) nº 7508/2002", result.getBillTitle()),
                () -> assertEquals("LEI-10769-2003-11-19", result.getLawId()),
                () -> assertEquals("https://www.lexml.gov.br/urn/urn:lex:br:federal:lei:2003-11-19;10769", result.getLawTextUrl()),
                () -> assertEquals(LocalDate.of(2002, 12, 26), result.getDateIntroduction()),
                () -> assertEquals(LocalDate.of(2003, 11, 19), result.getDatePassing()),
                () -> assertIterableEquals(
                        List.of("84/2003"),
                        result.getBrazilCountrySpecificVariables().getAlternativeBillIds()
                )
        );
    }

}
