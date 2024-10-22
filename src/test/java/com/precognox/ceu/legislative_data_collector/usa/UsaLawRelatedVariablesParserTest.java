package com.precognox.ceu.legislative_data_collector.usa;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.usa.parsers.UsaLawRelatedVariablesParser;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.mockito.ArgumentMatchers.any;

public class UsaLawRelatedVariablesParserTest {

    private final PrimaryKeyGeneratingRepository legislativeRecordRepository = Mockito.mock(
            PrimaryKeyGeneratingRepository.class);
    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);
    private final UsaCommonFunctions commonFunctions = Mockito.mock(UsaCommonFunctions.class);
    private final UsaLawRelatedVariablesParser parser = new UsaLawRelatedVariablesParser(legislativeRecordRepository,
            pageSourceRepository,
            commonFunctions);

    @Test
    public void testBill_HR1082_period_117() throws IOException, PageNotFoundException {
        String billPageUrl = "https://www.congress.gov/bill/117th-congress/house-bill/1082?s=5&r=8628";
        PageSource billPageSource = getPageSourceObj(
                "/usa/bill_HR1082_period_117/bill_HR1082_period_117_bill_page.html");
        billPageSource.setPageUrl(billPageUrl);

        String lawTextPageUrl = "https://www.congress.gov/bill/117th-congress/house-bill/1082/text?s=5&r=8628/text";
        PageSource lawTextPageSource = getPageSourceObj(
                "/usa/bill_HR1082_period_117/bill_HR1082_period_117_law_text_page.html");

        String actionsUrl = "https://www.congress.gov/bill/117th-congress/house-bill/1082/all-actions?s=5&r=8628";
        PageSource actionsPageSource = getPageSourceObj(
                "/usa/bill_HR1082_period_117/bill_HR1082_period_117_action_page.html");

        Mockito.when(commonFunctions.getActionsUrl(any())).thenReturn(Optional.of(actionsUrl));
        Mockito.when(pageSourceRepository.getByPageUrl(billPageUrl)).thenReturn(billPageSource);
        Mockito.when(commonFunctions.getCurrentPeriod(billPageUrl)).thenReturn("117");

        Mockito.when(pageSourceRepository.findByPageUrl(lawTextPageUrl))
                .thenReturn(Optional.of(lawTextPageSource));

        Mockito.when(commonFunctions.getPageFromDbOrDownload(PageTypes.LAW_TEXT.name(), lawTextPageUrl))
                .thenReturn(Jsoup.parse(lawTextPageSource.getRawSource()));
        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(billPageSource);
        Mockito.when(commonFunctions.getPageFromDbOrDownload(PageTypes.ACTION.name(), actionsUrl))
                .thenReturn(Jsoup.parse(actionsPageSource.getRawSource()));

        LegislativeDataRecord record = parser.parsePage(new LegislativeDataRecord());
        Assertions.assertEquals("PASS", record.getBillStatus().name());
        Assertions.assertEquals("Public", record.getLawType());
        Assertions.assertEquals("117-330", record.getLawId());
        Assertions.assertTrue(record.getLawId().contains("117-330"));
        Assertions.assertEquals("2023-01-05", record.getDatePassing().toString());
        Assertions.assertNull(record.getDateEnteringIntoForce());
    }

    @Test
    public void testBill_HR2884_period_107() throws IOException, PageNotFoundException {
        String billPageUrl = "https://www.congress.gov/bill/107th-congress/house-bill/2884?s=10&r=2884";
        PageSource billPageSource = getPageSourceObj(
                "/usa/bill_HR2884_period_107/bill_HR2884_period_107_bill_page.html");
        billPageSource.setPageUrl(billPageUrl);

        String lawTextPageUrl = "https://www.congress.gov/bill/107th-congress/house-bill/2884/text?s=10&r=2884/text";
        PageSource lawTextPageSource = getPageSourceObj(
                "/usa/bill_HR2884_period_107/bill_HR2884_period_107_law_text_page.html");

        String actionsUrl = "https://www.congress.gov/bill/107th-congress/house-bill/2884/all-actions?s=10&r=2884";
        PageSource actionsPageSource = getPageSourceObj(
                "/usa/bill_HR2884_period_107/bill_HR2884_period_107_action_page.html");

        String votesPageUrl = "https://clerk.house.gov/Votes/2001340";
        PageSource votesPageSource = getPageSourceObj(
                "/usa/bill_HR2884_period_107/bill_HR2884_period_107_votes_page.html");

        Mockito.when(commonFunctions.getActionsUrl(any())).thenReturn(Optional.of(actionsUrl));
        Mockito.when(pageSourceRepository.getByPageUrl(billPageUrl)).thenReturn(billPageSource);
        Mockito.when(commonFunctions.getCurrentPeriod(billPageUrl)).thenReturn("107");
        Mockito.when(commonFunctions.getPageFromDbOrDownload(PageTypes.LAW_TEXT.name(), lawTextPageUrl))
                .thenReturn(Jsoup.parse(lawTextPageSource.getRawSource()));
        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(billPageSource);
        Mockito.when(commonFunctions.getPageFromDbOrDownload(PageTypes.ACTION.name(), actionsUrl))
                .thenReturn(Jsoup.parse(actionsPageSource.getRawSource()));
        Mockito.when(commonFunctions.getPageFromDbOrDownload(PageTypes.VOTES.name(), votesPageUrl))
                .thenReturn(Jsoup.parse(votesPageSource.getRawSource()));

        LegislativeDataRecord record = parser.parsePage(new LegislativeDataRecord());
        Assertions.assertEquals("PASS", record.getBillStatus().name());
        Assertions.assertEquals("Public", record.getLawType());
        Assertions.assertEquals("107-134", record.getLawId());
        Assertions.assertTrue(record.getLawId().contains("107-134"));
        Assertions.assertEquals("2002-01-23", record.getDatePassing().toString());
        Assertions.assertNull(record.getDateEnteringIntoForce());
        Assertions.assertEquals(418, record.getFinalVoteFor());
        Assertions.assertEquals(0, record.getFinalVoteAgainst());
        Assertions.assertEquals(12, record.getFinalVoteAbst());
    }

}
