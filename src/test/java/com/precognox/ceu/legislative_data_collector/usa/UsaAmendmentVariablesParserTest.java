package com.precognox.ceu.legislative_data_collector.usa;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.usa.parsers.UsaAmendmentVariablesParser;
import com.precognox.ceu.legislative_data_collector.usa.parsers.UsaBillPageParser;
import com.precognox.ceu.legislative_data_collector.utils.JsoupUtils;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.mockito.ArgumentMatchers.any;

public class UsaAmendmentVariablesParserTest {
    private final PrimaryKeyGeneratingRepository legislativeRecordRepository = Mockito.mock(
            PrimaryKeyGeneratingRepository.class);
    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);

    private final UsaBillPageParser usaBillPageParser = Mockito.mock(UsaBillPageParser.class);
    private final JsoupUtils jsoupUtils = Mockito.mock(JsoupUtils.class);
    private final UsaCommonFunctions commonFunctions = Mockito.mock(UsaCommonFunctions.class);
    private final UsaAmendmentVariablesParser parser = new UsaAmendmentVariablesParser(legislativeRecordRepository,
                                                                                       pageSourceRepository,
                                                                                       jsoupUtils,
                                                                                       commonFunctions);

    @Test
    public void testBill_S1159_period_105() throws IOException, PageNotFoundException {
        String billPageUrl = "https://www.congress.gov/bill/105th-congress/senate-bill/1159?s=2&r=6371";
        PageSource billPageSource = getPageSourceObj(
                "/usa/bill_S1159_period_105/bill_S1159_period_105_bill_page.html");
        billPageSource.setPageUrl(billPageUrl);

        String amendmentPageUrl = "https://www.congress.gov/bill/105th-congress/senate-bill/1159/amendments?s=2&r=6371&page=1";
        PageSource amendmentPageSource = getPageSourceObj(
                "/usa/bill_S1159_period_105/bill_S1159_period_105_amendment_page.html");
        amendmentPageSource.setPageUrl(amendmentPageUrl);

        Mockito.when(commonFunctions.getCurrentPeriod(billPageUrl)).thenReturn("105");
        Mockito.when(commonFunctions.getPageFromDbOrDownload(PageTypes.AMENDMENT.name(), amendmentPageUrl))
                .thenReturn(Jsoup.parse(amendmentPageSource.getRawSource()));
        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(billPageSource);

        LegislativeDataRecord record = parser.parsePage(new LegislativeDataRecord());

        Assertions.assertEquals(1, record.getAmendmentCount());
        Assertions.assertEquals("S.Amdt.3043", record.getAmendments().get(0).getAmendmentId());
        Assertions.assertEquals("Murkowski, Frank H.", record.getAmendments().get(0).getOriginators().get(0).getName());
        Assertions.assertEquals("S", record.getAmendments().get(0).getOriginators().get(0).getAffiliation());
        Assertions.assertEquals("UPPER", record.getAmendments().get(0).getPlenary().name());
        Assertions.assertEquals("APPROVED", record.getAmendments().get(0).getOutcome().name());
    }

    @Test
    public void testBill_HR1082_period_117() throws IOException, PageNotFoundException {
        String billPageUrl = "https://www.congress.gov/bill/117th-congress/house-bill/1082?s=5&r=8628";
        PageSource billPageSource = getPageSourceObj(
                "/usa/bill_HR1082_period_117/bill_HR1082_period_117_bill_page.html");
        billPageSource.setPageUrl(billPageUrl);

        String amendmentPageUrl = "https://www.congress.gov/bill/117th-congress/house-bill/1082/amendments?s=5&r=8628&page=1";
        PageSource amendmentPageSource = getPageSourceObj(
                "/usa/bill_HR1082_period_117/bill_HR1082_period_117_amendment_page.html");
        amendmentPageSource.setPageUrl(amendmentPageUrl);

        Mockito.when(commonFunctions.getCurrentPeriod(billPageUrl)).thenReturn("117");
        Mockito.when(commonFunctions.getPageFromDbOrDownload(PageTypes.AMENDMENT.name(), amendmentPageUrl))
                .thenReturn(Jsoup.parse(amendmentPageSource.getRawSource()));
        Mockito.when(pageSourceRepository.getByPageUrl(any())).thenReturn(billPageSource);

        LegislativeDataRecord record = parser.parsePage(new LegislativeDataRecord());

        Assertions.assertEquals(2, record.getAmendmentCount());
        Assertions.assertEquals("S.Amdt.6625", record.getAmendments().get(0).getAmendmentId());
        Assertions.assertEquals("Wicker, Roger F.", record.getAmendments().get(0).getOriginators().get(0).getName());
        Assertions.assertEquals("S", record.getAmendments().get(0).getOriginators().get(0).getAffiliation());
        Assertions.assertEquals("UPPER", record.getAmendments().get(0).getPlenary().name());
        Assertions.assertEquals("APPROVED", record.getAmendments().get(0).getOutcome().name());

        Assertions.assertEquals("S.Amdt.6624", record.getAmendments().get(1).getAmendmentId());
        Assertions.assertEquals("Wicker, Roger F.", record.getAmendments().get(1).getOriginators().get(0).getName());
        Assertions.assertEquals("S", record.getAmendments().get(1).getOriginators().get(0).getAffiliation());
        Assertions.assertEquals("UPPER", record.getAmendments().get(1).getPlenary().name());
        Assertions.assertEquals("APPROVED", record.getAmendments().get(1).getOutcome().name());
    }
}
