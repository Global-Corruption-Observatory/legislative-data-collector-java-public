package com.precognox.ceu.legislative_data_collector.india.new_website;

import com.precognox.ceu.legislative_data_collector.common.ResourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiBillParserTest {

    private ApiBillParser parser = new ApiBillParser(null, null);

    @Test
    void testBillGroup1() throws IOException {
        PageSource source = ResourceLoader.getPageSourceObj("/india/test_api_response.json");

        List<LegislativeDataRecord> bills = parser.parseBills(source);
        assertNotNull(bills);
        assertEquals(10, bills.size());
    }

    @Test
    void testBillGroup2() throws IOException {
        PageSource source = ResourceLoader.getPageSourceObj("/india/test_api_response2.json");

        List<LegislativeDataRecord> bills = parser.parseBills(source);
        assertNotNull(bills);
        assertEquals(100, bills.size());

        Optional<LegislativeDataRecord> testBill = bills.stream()
                .filter(record -> record.getBillTitle().equals("The Major Port Authorities Bill, 2020"))
                .findFirst();

        assertTrue(testBill.isPresent());
        assertEquals(1, testBill.get().getOriginators().size());
        assertNull(testBill.get().getOriginators().get(0).getName());
        assertEquals("SHIPPING", testBill.get().getOriginators().get(0).getAffiliation());

        List<LegislativeStage> testStages = testBill.get().getStages();
        assertEquals(2, testStages.size());
        assertEquals(LocalDate.of(2020, 9, 23), testStages.get(0).getDate());
        assertEquals(LocalDate.of(2021, 2, 10), testStages.get(1).getDate());
        assertEquals(1, testStages.get(0).getStageNumber());
        assertEquals(2, testStages.get(1).getStageNumber());
    }

}
