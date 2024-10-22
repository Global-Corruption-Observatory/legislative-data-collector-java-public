package com.precognox.ceu.legislative_data_collector.usa;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.usa.parsers.UsaBillPageParser;
import com.precognox.ceu.legislative_data_collector.utils.JsoupUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;

public class UsaBillPageParserTest {

    private final PrimaryKeyGeneratingRepository legislativeRecordRepository = Mockito.mock(
            PrimaryKeyGeneratingRepository.class);
    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);
    private final JsoupUtils jsoupUtils = Mockito.mock(JsoupUtils.class);
    private final UsaCommonFunctions commonFunctions = new UsaCommonFunctions(pageSourceRepository, jsoupUtils, null);

    private final UsaBillPageParser parser = new UsaBillPageParser(legislativeRecordRepository, pageSourceRepository,
                                                                   commonFunctions);

    @Test
    public void testBill_HR1277_period_118() throws IOException {
        String billPageUrl = "https://www.congress.gov/bill/118th-congress/house-bill/1277?s=2&r=6243";
        PageSource billPageSource = getPageSourceObj(
                "/usa/bill_HR1277_period_118/bill_HR1277_period_118_bill_page.html");
        billPageSource.setPageUrl(billPageUrl);

        String billTextUrl = "https://www.congress.gov/bill/118th-congress/house-bill/1277/text?s=2&r=6243&format=txt";
        PageSource billTextPageSource = getPageSourceObj(
                "/usa/bill_HR1277_period_118/bill_HR1277_period_118_bill_text_page.html");
        billTextPageSource.setPageUrl(billTextUrl);
        billTextPageSource.setMetadata(billPageUrl);

        String cosponsorsPageUrl = "https://www.congress.gov/bill/118th-congress/house-bill/1277/cosponsors?s=2&r=6243";
        PageSource cosponsorsPageSource = getPageSourceObj(
                "/usa/bill_HR1277_period_118/bill_HR1277_period_118_cosponsors_page.html");

        String actionPageUrl = "https://www.congress.gov/bill/118th-congress/house-bill/1277/all-actions?s=2&r=6243";
        PageSource actionPageSource = getPageSourceObj(
                "/usa/bill_HR1277_period_118/bill_HR1277_period_118_action_page.html");
        actionPageSource.setPageUrl(actionPageUrl);

        String relatedBillUrl1 = "https://www.congress.gov/bill/118th-congress/house-bill/1277/related-bills?s=1&r=6406";
        PageSource relatedBillPageSource1 = getPageSourceObj(
                "/usa/bill_HR1277_period_118/bill_HR1277_period_118_related_bill_page_1.html");
        String relatedBillUrl2 = "https://www.congress.gov/bill/118th-congress/house-bill/1277/related-bills?s=2&r=6243";
        PageSource relatedBillPageSource2 = getPageSourceObj(
                "/usa/bill_HR1277_period_118/bill_HR1277_period_118_related_bill_page_2.html");

        String committeePageUrl1 = "https://www.congress.gov/bill/118th-congress/house-bill/1277/all-actions?r=6243&s=2&q=%7B%22house-committees%22%3A%22all%22%7D";
        PageSource committeePageSource1 = getPageSourceObj(
                "/usa/bill_HR1277_period_118/bill_HR1277_period_118_committee_page_1.html");
        String committeePageUrl2 = "https://www.congress.gov/bill/118th-congress/house-bill/1277/all-actions?r=6406&s=1&q=%7B%22house-committees%22%3A%22all%22%7D";
        PageSource committeePageSource2 = getPageSourceObj(
                "/usa/bill_HR1277_period_118/bill_HR1277_period_118_committee_page_2.html");

        parser.currentPeriod = "118";
        Mockito.when(pageSourceRepository.findPagesByPageTypeAndCountry(PageTypes.COMMITTEE_LIST.name(), Country.USA))
                .thenReturn(getCommitteePages().stream().toList());
        Mockito.when(pageSourceRepository.findByPageUrl(billTextUrl)).thenReturn(Optional.of(billTextPageSource));
        Mockito.when(pageSourceRepository.findByPageUrl(cosponsorsPageUrl))
                .thenReturn(Optional.of(cosponsorsPageSource));
        Mockito.when(pageSourceRepository.findByPageUrl(relatedBillUrl1))
                .thenReturn(Optional.of(relatedBillPageSource1));
        Mockito.when(pageSourceRepository.findByPageUrl(relatedBillUrl2))
                .thenReturn(Optional.of(relatedBillPageSource2));
        Mockito.when(pageSourceRepository.findByPageUrl(actionPageUrl)).thenReturn(Optional.of(actionPageSource));
        Mockito.when(pageSourceRepository.findByPageUrl(committeePageUrl1))
                .thenReturn(Optional.of(committeePageSource1));
        Mockito.when(pageSourceRepository.findByPageUrl(committeePageUrl2))
                .thenReturn(Optional.of(committeePageSource2));

        LegislativeDataRecord record = parser.parsePage(billPageSource);

        Assertions.assertEquals("H.R.1277", record.getBillId());
        Assertions.assertEquals("Military Spouse Hiring Act", record.getBillTitle());
        Assertions.assertEquals("INDIVIDUAL_MP", record.getOriginType().toString());

        Assertions.assertEquals(2, record.getStagesCount());
        Assertions.assertEquals(1, record.getStages().get(0).getStageNumber());
        Assertions.assertEquals("2023-03-01", record.getStages().get(0).getDate().toString());
        Assertions.assertEquals("House - Introduced in House Action By: House of Representatives",
                                record.getStages().get(0).getName());

        Assertions.assertEquals(2, record.getStages().get(1).getStageNumber());
        Assertions.assertEquals("2023-03-01", record.getStages().get(1).getDate().toString());
        Assertions.assertEquals(
                "House - Referred to the House Committee on Ways and Means. Action By: House of Representatives",
                record.getStages().get(1).getName());

        Assertions.assertEquals("Rep. Beyer, Donald S., Jr.", record.getOriginators().get(0).getName());
        Assertions.assertEquals("Democrats", record.getOriginators().get(0).getAffiliation());

        Assertions.assertEquals(2134, record.getBillSize());
        Assertions.assertTrue(record.getBillText().contains("Military Spouse Hiring Act"));
        Assertions.assertEquals("2023-03-01", record.getDateIntroduction().toString());

        Assertions.assertEquals(1, record.getCommitteeCount());
        Assertions.assertEquals("Ways and Means", record.getCommittees().get(0).getName());
        Assertions.assertEquals("2023-03-01", record.getCommittees().get(0).getDate().toString());

        Assertions.assertEquals("REGULAR", record.getProcedureTypeStandard().toString());
        Assertions.assertNull(record.getAmendmentCount());

        Assertions.assertEquals(146, record.getOriginatorSupportNames().size());

        Assertions.assertEquals(1, record.getRelatedBills().size());
        Assertions.assertEquals("S.596", record.getRelatedBills().get(0).getRelatedBillId());
        Assertions.assertEquals("Military Spouse Hiring Act", record.getRelatedBills().get(0).getRelatedBillTitle());
        Assertions.assertEquals("Identical bill", record.getRelatedBills().get(0).getRelatedBillRelationship());
    }

    @Test
    public void testBill_HR1727_period_103() throws IOException {
        String billPageUrl = "https://www.congress.gov/bill/103rd-congress/house-bill/1727?s=3&r=3584";
        String cleanBillPageUrl = "https://www.congress.gov/bill/103rd-congress/house-bill/1727";
        PageSource billPageSource = getPageSourceObj(
                "/usa/bill_HR1727_period_103/bill_HR1727_period_103_bill_page.html");
        billPageSource.setPageUrl(billPageUrl);
        billPageSource.setCleanUrl(cleanBillPageUrl);

        String actionPageUrl = "https://www.congress.gov/bill/103rd-congress/house-bill/1727/all-actions?s=3&r=3584";
        PageSource actionPageSource = getPageSourceObj(
                "/usa/bill_HR1727_period_103/bill_HR1727_period_103_action_page.html");
        actionPageSource.setPageUrl(actionPageUrl);

        String committeePageUrl_1 = "https://www.congress.gov/bill/103rd-congress/house-bill/1727/all-actions?r=3584&s=3&q=%7B%22house-committees%22%3A%22Science%2C+Space+and+Technology%22%7D";
        PageSource committeePageSource_1 = getPageSourceObj(
                "/usa/bill_HR1727_period_103/bill_HR1727_period_103_committee_page_1.html");
        committeePageSource_1.setPageUrl(committeePageUrl_1);

        String committeePageUrl_2 = "https://www.congress.gov/bill/103rd-congress/house-bill/1727/all-actions?r=3584&s=3&q=%7B%22senate-committees%22%3A%22all%22%7D";
        PageSource committeePageSource_2 = getPageSourceObj(
                "/usa/bill_HR1727_period_103/bill_HR1727_period_103_committee_page_2.html");
        committeePageSource_2.setPageUrl(committeePageUrl_2);

        String relatedBillUrl = "https://www.congress.gov/bill/103rd-congress/house-bill/1727/related-bills?s=3&r=3584";
        PageSource relatedBillPageSource = getPageSourceObj(
                "/usa/bill_HR1727_period_103/bill_HR1727_period_103_related_bill_page.html");
        relatedBillPageSource.setPageUrl(relatedBillUrl);

        String cosponsorsPageUrl = "https://www.congress.gov/bill/103rd-congress/house-bill/1727/cosponsors?s=3&r=3584";
        PageSource cosponsorsPageSource = getPageSourceObj(
                "/usa/bill_HR1727_period_103/bill_HR1727_period_103_cosponsros_page.html");
        cosponsorsPageSource.setPageUrl(cosponsorsPageUrl);

        String billTextUrl = "https://www.congress.gov/bill/103rd-congress/house-bill/1727";
        PageSource billTextPageSource = getPageSourceObj(
                "/usa/bill_HR1727_period_103/bill_HR1727_period_103_bill_text_page.html");
        billTextPageSource.setPageUrl(billTextUrl);
        billTextPageSource.setMetadata(billPageUrl);

        parser.currentPeriod = "103";
        Mockito.when(pageSourceRepository.findPagesByPageTypeAndCountry(PageTypes.COMMITTEE_LIST.name(), Country.USA))
                .thenReturn(getCommitteePages().stream().toList());
        Mockito.when(pageSourceRepository.findByPageUrl(billPageUrl)).thenReturn(Optional.of(billPageSource));
        Mockito.when(pageSourceRepository.findByPageUrl(actionPageUrl)).thenReturn(Optional.of(actionPageSource));
        Mockito.when(pageSourceRepository.findByPageUrl(committeePageUrl_1))
                .thenReturn(Optional.of(committeePageSource_1));
        Mockito.when(pageSourceRepository.findByPageUrl(committeePageUrl_2))
                .thenReturn(Optional.of(committeePageSource_2));
        Mockito.when(pageSourceRepository.findByPageUrl(relatedBillUrl)).thenReturn(Optional.of(relatedBillPageSource));
        Mockito.when(pageSourceRepository.findByPageUrl(cosponsorsPageUrl))
                .thenReturn(Optional.of(cosponsorsPageSource));
        Mockito.when(pageSourceRepository.findByPageUrl(billTextUrl)).thenReturn(Optional.of(billTextPageSource));
        Mockito.when(pageSourceRepository.findByUrlByMetadata(PageTypes.BILL_TEXT.name(), Country.USA, billTextUrl))
                .thenReturn(Optional.of(billTextPageSource));

        LegislativeDataRecord record = parser.parsePage(billPageSource);


        Assertions.assertEquals("H.R.1727", record.getBillId());
        Assertions.assertEquals("Arson Prevention Act of 1994", record.getBillTitle());
        Assertions.assertEquals("INDIVIDUAL_MP", record.getOriginType().toString());

        Assertions.assertEquals(6, record.getStagesCount());
        Assertions.assertEquals(1, record.getStages().get(0).getStageNumber());
        Assertions.assertEquals("1993-04-20", record.getStages().get(0).getDate().toString());
        Assertions.assertEquals("House - Introduced in House", record.getStages().get(0).getName());

        Assertions.assertEquals(6, record.getStages().get(5).getStageNumber());
        Assertions.assertEquals("1994-05-19", record.getStages().get(5).getDate().toString());
        Assertions.assertEquals("Became Public Law No: 103-254.", record.getStages().get(5).getName());

        Assertions.assertEquals("Rep. Boucher, Rick", record.getOriginators().get(0).getName());
        Assertions.assertEquals("Democrats", record.getOriginators().get(0).getAffiliation());

        Assertions.assertEquals("1993-04-20", record.getDateIntroduction().toString());

        Assertions.assertEquals(2, record.getCommitteeCount());
        Assertions.assertEquals("Science, Space and Technology", record.getCommittees().get(0).getName());
        Assertions.assertEquals("1993-04-20", record.getCommittees().get(0).getDate().toString());
        Assertions.assertEquals("Commerce, Science, and Transportation", record.getCommittees().get(1).getName());
        Assertions.assertEquals("1993-07-27", record.getCommittees().get(1).getDate().toString());

        Assertions.assertEquals("EXCEPTIONAL", record.getProcedureTypeStandard().toString());
        Assertions.assertNull(record.getAmendmentCount());

        Assertions.assertEquals(23, record.getOriginatorSupportNames().size());

        Assertions.assertEquals(1, record.getRelatedBills().size());
        Assertions.assertEquals("S.798", record.getRelatedBills().get(0).getRelatedBillId());
        Assertions.assertEquals("Arson Prevention Act of 1993", record.getRelatedBills().get(0).getRelatedBillTitle());
        Assertions.assertEquals("Related document", record.getRelatedBills().get(0).getRelatedBillRelationship());

    }

    public List<PageSource> getCommitteePages() throws IOException {
        List<PageSource> committeePages = new ArrayList<>();

        for (int i = 103; i <= 118; i++) {
            PageSource source = getPageSourceObj("/usa/committee_pages/committee_page_period_" + i + ".html");
            source.setPageUrl(
                    "https://www.congress.gov/search?pageSize=250&q=%7B%22source%22%3A%22legislation%22%2C%22type%22%3A%22bills%22%2C%22congress%22%3A103%7D&page=1");
            source.setCountry(Country.USA);
            source.setPageType(PageTypes.COMMITTEE_LIST.name());
            source.setMetadata("Period: " + i);
            committeePages.add(source);
        }

        return committeePages;
    }
}
