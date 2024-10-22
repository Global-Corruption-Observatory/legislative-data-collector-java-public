package com.precognox.ceu.legislative_data_collector.hungary;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModifiedLawParserTest {

    private ModifiedLawParser parser = new ModifiedLawParser(null);

//    @Test
    public void name() throws IOException {
        LegislativeDataRecord record = new LegislativeDataRecord();
        record.setBillType(LegislativeDataRecord.BillNature.AMENDMENT_BILL.name());
        record.setBillText(new String(getClass().getResourceAsStream("/testBillText.txt").readAllBytes()));

        Set<String> result = parser.parseFromText(record);

        assertEquals(4, result.size());
    }

    @Test
    void testBill_2012_T_7757() throws IOException {
        String billTextFile = "/hungary/bill_texts/bill_2012_T_7757_text.txt";
        String billText = new String(getClass().getResourceAsStream(billTextFile).readAllBytes());

        LegislativeDataRecord record = new LegislativeDataRecord(Country.SWEDEN);
        record.setBillText(billText);
        record.setOriginalLaw(Boolean.FALSE);
        record.setBillTitle("Egyes törvényeknek a XX. századi önkényuralmi rendszerekhez köthető elnevezések tilalmával összefüggő módosításáról");

        parser.parseModifiedLaws(record);

        assertNotNull(record.getModifiedLaws());
        assertTrue(record.getModifiedLaws().contains("2006/V"));
        assertTrue(record.getModifiedLaws().contains("2010/CLXXXV"));
        assertTrue(record.getModifiedLaws().contains("2011/CLXXXI"));
        assertTrue(record.getModifiedLaws().contains("2011/CLXXXIX"));
        assertTrue(record.getModifiedLaws().contains("1990/XCIII"));
        assertTrue(record.getModifiedLaws().contains("1990/XCIII"));
        assertTrue(record.getModifiedLaws().contains("1996/LXXXV"));
    }

    @Test
    void testBill_2009_T_9485() throws IOException {
        String billTextFile = "/hungary/bill_texts/bill_2009_T_9485_text.txt";
        String billText = new String(getClass().getResourceAsStream(billTextFile).readAllBytes());

        LegislativeDataRecord record = new LegislativeDataRecord(Country.SWEDEN);
        record.setBillText(billText);
        record.setOriginalLaw(Boolean.FALSE);
        record.setBillTitle("A költségvetési szervek jogállásáról és gazdálkodásáról szóló 2008. CV. törvény módosításáról");

        parser.parseModifiedLaws(record);

        assertNotNull(record.getModifiedLaws());
        assertEquals(1, record.getModifiedLaws().size());
        assertEquals(1, record.getModifiedLawsCount());
        assertTrue(record.getModifiedLaws().contains("2008/CV"));
    }

}
