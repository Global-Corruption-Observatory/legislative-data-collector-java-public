package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.utils.PdfUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getResourceAsBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BillTextParserTest {

    private final BillTextParser instance = new BillTextParser(null);

    @Test
    void testBill2023_23_117() throws IOException {
        String pdfText = PdfUtils.extractText(getResourceAsBytes("/sweden/bill_text_prop_202223__117.pdf"));

        LegislativeDataRecord testRecord = new LegislativeDataRecord(Country.SWEDEN);
        testRecord.setBillPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/en-tillfallig-allman-flaggdag-for-att_ha03123/");
        testRecord.setOriginType(OriginType.GOVERNMENT);
        testRecord.setBillTextUrl("https://data.riksdagen.se/fil/B516845B-174C-44BB-9C31-640914F84DD9");
        testRecord.setBillText(pdfText);

        instance.parseVariables(testRecord);

        assertEquals(LocalDate.of(2023, 5, 4), testRecord.getDateIntroduction());
        assertEquals(LocalDate.of(2023, 10, 1), testRecord.getDateEnteringIntoForce());
        assertEquals(false, testRecord.getOriginalLaw());
    }

    @Test
    void testBill2022_23_72() throws IOException {
        String pdfText = PdfUtils.extractText(getResourceAsBytes("/sweden/bill_text_prop_202223__72.pdf"));

        LegislativeDataRecord testRecord = new LegislativeDataRecord(Country.SWEDEN);
        testRecord.setBillPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/eus-nya-tagpassagerarforordning_ha0372/");
        testRecord.setOriginType(OriginType.GOVERNMENT);
        testRecord.setBillTextUrl("https://data.riksdagen.se/fil/05B6A699-3C12-4601-9960-56BE230F7355");
        testRecord.setBillText(pdfText);

        instance.parseVariables(testRecord);

        assertEquals(LocalDate.of(2023, 3, 2), testRecord.getDateIntroduction());
        assertEquals(LocalDate.of(2023, 6, 7), testRecord.getDateEnteringIntoForce());
        assertEquals(false, testRecord.getOriginalLaw());
    }

    @Test
    void testBill2022_23_1() throws IOException {
        String pdfText = PdfUtils.extractText(getResourceAsBytes("/sweden/bill_text_prop_202223__1.pdf"));

        LegislativeDataRecord testRecord = new LegislativeDataRecord(Country.SWEDEN);
        testRecord.setBillPageUrl(
                "https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/budgetpropositionen-for-2023_ha031//");
        testRecord.setOriginType(OriginType.GOVERNMENT);
        testRecord.setBillTextUrl("https://data.riksdagen.se/fil/68ABCEC6-2C02-471D-B5D7-E7F23B0FF380");
        testRecord.setBillText(pdfText);

        instance.parseVariables(testRecord);

        assertEquals(LocalDate.of(2022, 11, 3), testRecord.getDateIntroduction());
    }

    @Test
    void testBill_2005_06_74() throws IOException {
        String pdfText = PdfUtils.extractText(getResourceAsBytes("/sweden/bill_text_prop_2005_06_74.pdf"));

        LegislativeDataRecord testRecord = new LegislativeDataRecord(Country.SWEDEN);
        testRecord.setBillPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/kvalificerad-yrkesutbildning-som_gt0374/");
        testRecord.setOriginType(OriginType.GOVERNMENT);
        testRecord.setBillTextUrl("https://data.riksdagen.se/fil/9DF01346-9D10-44C4-80EC-75726B9F6DB8");
        testRecord.setBillText(pdfText);

        instance.parseVariables(testRecord);

        assertEquals(false, testRecord.getOriginalLaw());
        assertEquals(LocalDate.of(2006, 3, 16), testRecord.getDateIntroduction());
    }

}
