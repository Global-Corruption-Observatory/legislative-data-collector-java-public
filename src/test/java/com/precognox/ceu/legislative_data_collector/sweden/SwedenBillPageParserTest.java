package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SwedenBillPageParserTest {

    private final SwedenBillPageParser instance = new SwedenBillPageParser(null, null, null);

    @Test
    void test_2013_14_223() throws IOException {
        //https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/redovisningscentraler-for-taxi_h103223/
        PageSource testPage = getPageSourceObj("/sweden/bill_2013_14_223.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/redovisningscentraler-for-taxi_h103223/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals("2013/14:223", result.getBillId());
        assertEquals("Redovisningscentraler för taxi", result.getBillTitle());
        assertEquals(LocalDate.of(2014, 4, 24), result.getDateIntroduction());
        assertEquals(LocalDate.of(2014, 5, 12), result.getDatePassing());
    }

    @Test
    void testFamiljerattBill() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_familjeratt.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/motion/familjeratt_ha022218/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals("2022/23:2218", result.getBillId());
        assertEquals("Familjerätt", result.getBillTitle());
        assertEquals(LegislativeDataRecord.BillStatus.PASS, result.getBillStatus());
        assertEquals("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/motion/familjeratt_ha022218/html/", result.getBillTextUrl());
        assertEquals(OriginType.INDIVIDUAL_MP, result.getOriginType());

        assertEquals(1, result.getCommittees().size());
        assertEquals("Civilutskottet", result.getCommittees().get(0).getName());
        assertNull(result.getCommittees().get(0).getRole());

        assertNotNull(result.getStages());
        assertFalse(result.getStages().isEmpty());
        assertEquals("Inlämnad", result.getStages().get(0).getName());
        assertEquals(1, result.getStages().get(0).getStageNumber());
        assertEquals(LocalDate.of(2022, 11, 23), result.getStages().get(0).getDate());

        assertNotNull(result.getOriginators());
        assertEquals(5, result.getOriginators().size());
        assertEquals("Jennie Nilsson", result.getOriginators().get(0).getName());
        assertEquals("Socialdemokraterna", result.getOriginators().get(0).getAffiliation());
        assertEquals("Leif Nysmed", result.getOriginators().get(1).getName());
        assertEquals("Socialdemokraterna", result.getOriginators().get(1).getAffiliation());
        assertEquals("Denis Begic", result.getOriginators().get(2).getName());
        assertEquals("Socialdemokraterna", result.getOriginators().get(2).getAffiliation());
        assertEquals("Anna-Belle Strömberg", result.getOriginators().get(3).getName());
        assertEquals("Socialdemokraterna", result.getOriginators().get(3).getAffiliation());
        assertEquals("Markus Kallifatides", result.getOriginators().get(4).getName());
        assertEquals("Socialdemokraterna", result.getOriginators().get(4).getAffiliation());

        assertEquals(0, result.getAmendmentCount());
    }

    @Test
    void test_bill_2022_23_123() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2022_23_123.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/en-tillfallig-allman-flaggdag-for-att_ha03123/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertNotNull(result.getStages());
        assertFalse(result.getStages().isEmpty());
        assertEquals("Inlämnad", result.getStages().get(0).getName());
        assertEquals(1, result.getStages().get(0).getStageNumber());
        assertEquals(LocalDate.of(2023, 5, 25), result.getStages().get(0).getDate());

        assertNotNull(result.getSwedenCountrySpecificVariables());
        assertEquals("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/betankande/en-tillfallig-allman-flaggdag-for-att_ha01ku41/", result.getSwedenCountrySpecificVariables().getForslagspunkterPageUrl());

        assertNotNull(result.getAmendments());
        assertEquals(1, result.getAmendmentCount());
        assertEquals(1, result.getAmendments().size());

        Amendment am1 = result.getAmendments().get(0);
        assertEquals("med anledning av prop. 2022/23:123 En tillfällig allmän flaggdag för att högtidlighålla 50-årsdagen av konungens trontillträde", am1.getTitle());
        assertEquals("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/motion/med-anledning-av-prop.-202223123-en-tillfallig_ha022396", am1.getPageUrl());
        assertEquals("2022/23:2396", am1.getAmendmentId());
    }

    @Test
    void testLaw_2023_482() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/law_2023_482.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/svensk-forfattningssamling/forordning-2023482-om-provning-och-samordning_sfs-2023-482/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals("http://rkrattsbaser.gov.se/sfst?bet=2023:482", result.getLawTextUrl());
    }

    @Test
    void testBill_2022_23_2112() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2022_23_2112.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/motion/el-och-energiforsorjning-for-okad-tillvaxt-och_ha022112/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals(LegislativeDataRecord.BillStatus.PASS, result.getBillStatus());
        assertEquals("El- och energiförsörjning för ökad tillväxt och välstånd", result.getBillTitle());
        assertEquals("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/motion/el-och-energiforsorjning-for-okad-tillvaxt-och_ha022112/html/", result.getBillTextUrl());

        assertNotNull(result.getStages());
        assertFalse(result.getStages().isEmpty());
        assertEquals("Inlämnad", result.getStages().get(0).getName());
        assertEquals(1, result.getStages().get(0).getStageNumber());
        assertEquals(LocalDate.of(2022, 11, 23), result.getStages().get(0).getDate());

        assertEquals(0, result.getAmendmentCount());
    }

    @Test
    void testBill_2023_24_4() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2023_24_4.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/motion/forbjud-avstangning-av-gator-pga-av-mindre_hb024/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals(1, result.getCommittees().size());
        assertEquals("Trafikutskottet", result.getCommittees().get(0).getName());
        assertEquals(1, result.getOriginators().size());
        assertEquals("Robert Stenkvist", result.getOriginators().get(0).getName());
        assertEquals("Sverigedemokraterna", result.getOriginators().get(0).getAffiliation());
        assertEquals(LegislativeDataRecord.BillStatus.REJECT, result.getBillStatus());
        assertEquals("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/motion/forbjud-avstangning-av-gator-pga-av-mindre_hb024/html/", result.getBillTextUrl());
        assertEquals("Förbud mot avstängning av gator på grund av mindre idrottstävlingar i större städer", result.getBillTitle());

        assertNotNull(result.getStages());
        assertFalse(result.getStages().isEmpty());
        assertEquals("Inlämnad", result.getStages().get(0).getName());
        assertEquals(1, result.getStages().get(0).getStageNumber());
        assertEquals(LocalDate.of(2023, 9, 14), result.getStages().get(0).getDate());

        assertEquals(0, result.getAmendmentCount());
    }

    @Test
    void testBill_2022_23_126() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2022_23_126.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/hemliga-tvangsmedel-effektiva-verktyg-for-att_ha03126/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        Set<String> expected = Set.of("1988:97", "2000:562", "2007:979", "2017:1000", "2020:62");

        assertFalse(result.getModifiedLaws().isEmpty());
        assertEquals(5, result.getModifiedLaws().size());
        assertEquals(expected, result.getModifiedLaws());

        assertNotNull(result.getAmendments());
        assertEquals(3, result.getAmendmentCount());
        assertEquals(3, result.getAmendments().size());

        Amendment am1 = result.getAmendments().get(0);
        assertEquals("med anledning av prop. 2022/23:126 Hemliga tvångsmedel - effektiva verktyg för att förhindra och utreda allvarliga brott", am1.getTitle());
        assertEquals("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/motion/med-anledning-av-prop.-202223126-hemliga_ha022403/", am1.getPageUrl());
        assertEquals("2022/23:2403", am1.getAmendmentId());

        Amendment am2 = result.getAmendments().get(1);
        assertEquals("med anledning av prop. 2022/23:126 Hemliga tvångsmedel - effektiva verktyg för att förhindra och utreda allvarliga brott", am2.getTitle());
        assertEquals("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/motion/med-anledning-av-prop.-202223126-hemliga_ha022402/", am2.getPageUrl());
        assertEquals("2022/23:2402", am2.getAmendmentId());

        Amendment am3 = result.getAmendments().get(2);
        assertEquals("med anledning av prop. 2022/23:126 Hemliga tvångsmedel - effektiva verktyg för att förhindra och utreda allvarliga brott", am3.getTitle());
        assertEquals("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/motion/med-anledning-av-prop.-202223126-hemliga_ha022404/", am3.getPageUrl());
        assertEquals("2022/23:2404", am3.getAmendmentId());
    }

    @Test
    void testBill_2022_23_110_modified_laws() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2022_23_110.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/estetiska-produkter-en-overgangsbestammelse_ha03110/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        Set<String> expected = Set.of("2022:394", "2021:600");

        assertFalse(result.getModifiedLaws().isEmpty());
        assertEquals(2, result.getModifiedLaws().size());
        assertEquals(expected, result.getModifiedLaws());
    }

    @Test
    void testBill_2022_23_99() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2022_23_99.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/varandringsbudget-for-2023_ha0399/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        Set<String> expected = Set.of("2022:1857", "2022:1042", "2007:1150");

        assertFalse(result.getModifiedLaws().isEmpty());
        assertEquals(expected, result.getModifiedLaws());
        assertEquals(false, result.getOriginalLaw());
    }

    @Test
    void testLaw2009_400_affecting_laws() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/law_2009_400.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/svensk-forfattningssamling/offentlighets-och-sekretesslag-2009400_sfs-2009-400/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals(
                "http://rkrattsbaser.gov.se/sfsr?bet=2009:400",
                result.getSwedenCountrySpecificVariables().getAffectingLawsPageUrl()
        );
    }

    @Test
    void testBill_2011_12_126() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2011_12_126.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/elektronisk-stamningsansokan-i-brottmal-_h0b3126/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals("2011/12:126", result.getBillId());
    }

    @Test
    void testBill_1987_88_176() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_1987_88_176.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/om-aldreomsorgen-infor-90-talet_gb03176/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals(6, result.getModifiedLawsCount());
    }

    @Test
    @Disabled
    void testBill_2022_23_1() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2022_23_1.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/budgetpropositionen-for-2023_ha031/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        //nem kéne ott lennie: 2011:203, 2022:900
        assertEquals(17, result.getModifiedLawsCount());
        assertEquals(List.of("1997:238", "2022:174", "1994:451", "2016:1067", "2020:1251", "2019:1274", "2021:1241", "2021:1285", "2018:1893", "2021:64", "2021:1287", "2011:1244", "2022:1042", "1999:1229", "1999:1395", "1994:419", "1994:1776"), result.getModifiedLaws());
    }

    @Test
    void testBill_2013_14_234() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2013_14_234.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/sanktionsavgift-for-overtradelse-av-bestammelserna_h103234/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals(false, result.getOriginalLaw());
    }

    @Test
    void testBill_2022_23_65() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2022_23_65.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/ett-likviditetsverktyg-for-fonder_ha0365/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals(false, result.getOriginalLaw());
    }

    @Test
    void testBill_1995_96_58() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_1995_96_58.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/egs-andra-forenklingsdirektiv-och-den-svenska_gj0358/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals(LocalDate.of(1995, 10, 19), result.getDateIntroduction());
    }

    @Test
    void testBill_1998_99_82() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_1998_99_82.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/dubbelbeskattningsavtal-mellan-sverige-och_gm0382/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals(LocalDate.of(1999, 3, 8), result.getDateIntroduction());
    }

    @Test
    void testBill_2018_19_1() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/bill_2018_19_1.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/proposition/budgetpropositionen-for-2019_h6031/");

        LegislativeDataRecord result = instance.parsePage(testPage);

        assertEquals(LocalDate.of(2018, 11, 9), result.getDateIntroduction());
    }

}
