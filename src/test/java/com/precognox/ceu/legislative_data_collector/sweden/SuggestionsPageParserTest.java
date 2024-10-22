package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.swe.SwedenCountrySpecificVariables;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SuggestionsPageParserTest {

    private SuggestionsPageParser instance = new SuggestionsPageParser(null, null);

    @Test
    void testBill_2022_23_123() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2022_23_123_suggestions_page.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/betankande/en-tillfallig-allman-flaggdag-for-att_ha01ku41/");

        LegislativeDataRecord record = new LegislativeDataRecord();
        record.setSwedenCountrySpecificVariables(new SwedenCountrySpecificVariables());

        instance.processPage(record, testPage);

        assertEquals(LegislativeDataRecord.BillStatus.PASS, record.getBillStatus());

        assertEquals(LocalDate.of(2023, 6, 21), record.getDatePassing());
        assertEquals(LocalDate.of(2023, 6, 13), record.getCommitteeDate());
        assertEquals("https://data.riksdagen.se/fil/C7083867-83C8-4E32-97AE-63B3E914A9E8", record.getSwedenCountrySpecificVariables().getStage1TextUrl());
        assertEquals(1, record.getCommitteeHearingCount());

        assertNotNull(record.getStages());
        assertEquals(3, record.getStages().size());

        assertEquals(2, record.getStages().get(0).getStageNumber());
        assertEquals("Beredning", record.getStages().get(0).getName());
        assertEquals(LocalDate.of(2023, 6, 15), record.getStages().get(0).getDate());

        assertEquals(3, record.getStages().get(1).getStageNumber());
        assertEquals("Debatt", record.getStages().get(1).getName());
        assertEquals(LocalDate.of(2023, 6, 21), record.getStages().get(1).getDate());

        assertEquals(4, record.getStages().get(2).getStageNumber());
        assertEquals("Beslut", record.getStages().get(2).getName());
        assertEquals(LocalDate.of(2023, 6, 21), record.getStages().get(2).getDate());

        assertEquals("2022/23:KU41", record.getSwedenCountrySpecificVariables().getReportId());
    }

    @Test
    void testBill_2022_23_57() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2022_23_57_suggestions_page.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/betankande/senarelagd-anslutning-till-nationell_ha01sou27/");

        LegislativeDataRecord record = new LegislativeDataRecord();
        record.setSwedenCountrySpecificVariables(new SwedenCountrySpecificVariables());

        instance.processPage(record, testPage);

        assertEquals(LegislativeDataRecord.BillStatus.PASS, record.getBillStatus());

        assertEquals(1, record.getCommitteeHearingCount());
        assertEquals(LocalDate.of(2023, 3, 29), record.getDatePassing());
        assertEquals(LocalDate.of(2023, 3, 9), record.getCommitteeDate());
        assertEquals("https://data.riksdagen.se/fil/DD1A205A-9AD9-4C15-96B9-75F6F8C94CB2", record.getSwedenCountrySpecificVariables().getStage1TextUrl());

        assertNotNull(record.getStages());
        assertEquals(3, record.getStages().size());

        assertEquals(2, record.getStages().get(0).getStageNumber());
        assertEquals("Beredning", record.getStages().get(0).getName());
        assertEquals(LocalDate.of(2023, 3, 23), record.getStages().get(0).getDate());

        assertEquals(3, record.getStages().get(1).getStageNumber());
        assertEquals("Debatt", record.getStages().get(1).getName());
        assertEquals(LocalDate.of(2023, 3, 29), record.getStages().get(1).getDate());

        assertEquals(4, record.getStages().get(2).getStageNumber());
        assertEquals("Beslut", record.getStages().get(2).getName());
        assertEquals(LocalDate.of(2023, 3, 29), record.getStages().get(2).getDate());

        assertEquals("2022/23:SoU27", record.getSwedenCountrySpecificVariables().getReportId());
    }

    @Test
    void testBill_2014_15_81() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2014_15_81_suggestions_page.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/betankande/klimatpolitik-m.m_h201mju13");

        LegislativeDataRecord record = new LegislativeDataRecord();
        record.setSwedenCountrySpecificVariables(new SwedenCountrySpecificVariables());

        instance.processPage(record, testPage);

        assertEquals(LegislativeDataRecord.BillStatus.PASS, record.getBillStatus());

        assertEquals("2014/15:MJU13", record.getSwedenCountrySpecificVariables().getReportId());
    }

    @Test
    void testBill_2012_13_153() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2012_13_153_suggestions_page.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/betankande/kommunal-medfinansiering-av-statlig_h101ku2/");

        LegislativeDataRecord record = new LegislativeDataRecord();
        record.setSwedenCountrySpecificVariables(new SwedenCountrySpecificVariables());

        instance.processPage(record, testPage);

        assertEquals(LegislativeDataRecord.BillStatus.PASS, record.getBillStatus());
        assertEquals("2013/14:KU2", record.getSwedenCountrySpecificVariables().getReportId());
    }

    @Test
    void testBill_2000_01_61() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2000_01_61_suggestions_page.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/betankande/forfalskade-fangeshandlingar-vid-ansokan-om_go01lu18/");

        LegislativeDataRecord record = new LegislativeDataRecord();
        record.setSwedenCountrySpecificVariables(new SwedenCountrySpecificVariables());

        instance.processPage(record, testPage);

        assertEquals(LocalDate.of(2001, 4, 19), record.getDatePassing());
        assertEquals(LegislativeDataRecord.BillStatus.PASS, record.getBillStatus());
        assertEquals("2000/01:LU18", record.getSwedenCountrySpecificVariables().getReportId());
    }

    @Test
    void testWithTwoHearings() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_two_comm_hearings.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/betankande/utokade-mojligheter-att-besluta-om-undantag-fran_ha01fiu39/");

        LegislativeDataRecord record = new LegislativeDataRecord();
        record.setSwedenCountrySpecificVariables(new SwedenCountrySpecificVariables());

        instance.processPage(record, testPage);

        assertEquals(LegislativeDataRecord.BillStatus.PASS, record.getBillStatus());

        assertEquals(2, record.getCommitteeHearingCount());
        assertEquals(LocalDate.of(2023, 5, 3), record.getDatePassing());
        assertEquals(LocalDate.of(2023, 4, 20), record.getCommitteeDate());
        assertEquals("https://data.riksdagen.se/fil/753CC8CC-E0F2-47E4-BA5C-11E1A36F6C40", record.getSwedenCountrySpecificVariables().getStage1TextUrl());

        assertNotNull(record.getStages());
        assertEquals(3, record.getStages().size());

        assertEquals(2, record.getStages().get(0).getStageNumber());
        assertEquals("Beredning", record.getStages().get(0).getName());
        assertEquals(LocalDate.of(2023, 4, 27), record.getStages().get(0).getDate());

        assertEquals(3, record.getStages().get(1).getStageNumber());
        assertEquals("Debatt", record.getStages().get(1).getName());
        assertEquals(LocalDate.of(2023, 5, 3), record.getStages().get(1).getDate());

        assertEquals(4, record.getStages().get(2).getStageNumber());
        assertEquals("Beslut", record.getStages().get(2).getName());
        assertEquals(LocalDate.of(2023, 5, 3), record.getStages().get(2).getDate());

        assertEquals("2022/23:FiU39", record.getSwedenCountrySpecificVariables().getReportId());
    }

}
