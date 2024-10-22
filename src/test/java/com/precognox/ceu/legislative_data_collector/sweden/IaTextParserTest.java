package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.utils.PdfUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getResourceAsBytes;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IaTextParserTest {

    private final IaTextParser parser = new IaTextParser(null);

    @Test
    void testBill2023_23_117() throws IOException {
        String pdfText = PdfUtils.extractText(getResourceAsBytes("/sweden/bill_text_prop_202223__117.pdf"));
        Boolean result = parser.hasIa(pdfText);
        assertTrue(result);
    }

    @Test
    void testBill2023_23_137() throws IOException {
        String pdfText = PdfUtils.extractText(getResourceAsBytes("/sweden/bill_text_prop_202223__137.pdf"));
        Boolean result = parser.hasIa(pdfText);
        assertTrue(result);
    }

    @Test
    void testBill2022_23_106() throws IOException {
        String pdfText = PdfUtils.extractText(getResourceAsBytes("/sweden/bill_text_prop_202223__106.pdf"));
        Boolean result = parser.hasIa(pdfText);
        assertTrue(result);
    }

    @Test
    void testBill2022_23_108() throws IOException {
        String pdfText = PdfUtils.extractText(getResourceAsBytes("/sweden/bill_prop_202223__108.pdf"));
        Boolean result = parser.hasIa(pdfText);
        assertTrue(result);
    }

    @Test
    void testBill2022_23_112() throws IOException {
        String pdfText = PdfUtils.extractText(getResourceAsBytes("/sweden/bill_text_prop_202223__112.pdf"));
        Boolean result = parser.hasIa(pdfText);
        assertTrue(result);
    }

    @Test
    void testBill2022_23_105() throws IOException {
        String pdfText = PdfUtils.extractText(getResourceAsBytes("/sweden/bill_text_prop_202223__105.pdf"));
        Boolean result = parser.hasIa(pdfText);
        assertFalse(result);
    }

    @Test
    void testBill2022_23_102() throws IOException {
        String pdfText = PdfUtils.extractText(getResourceAsBytes("/sweden/bill_text_prop_202223__102.pdf"));
        Boolean result = parser.hasIa(pdfText);
        assertTrue(result);
    }

    @Test
    void testBill_2014_15_81() throws IOException {
        String pdfText = PdfUtils.extractText(getResourceAsBytes("/sweden/bill_text_prop_201415__81.pdf"));
        Boolean result = parser.hasIa(pdfText);
        assertTrue(result);
    }
}
