package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LawPageParserTest {

    @Test
    void test_2024_404() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/law_2024_404.html");

        LawPageParser lawPageParser = new LawPageParser(null, null, null);
        LegislativeDataRecord record = lawPageParser.parsePage(testPage);

        assertAll(
                () -> assertEquals("2024:404", record.getLawId()),
                () -> assertEquals(LocalDate.of(2024, 5, 30), record.getDatePassing()),
                () -> assertEquals("Lag (2024:404) om skattefrihet och förfarande för kompensation till personer födda 1957 på grund av höjd åldersgräns för förhöjt grundavdrag", record.getBillTitle()),
                () -> assertEquals("http://rkrattsbaser.gov.se/sfsr?bet=2024:404", record.getSwedenCountrySpecificVariables().getAffectingLawsPageUrl()),
                () -> assertEquals("http://rkrattsbaser.gov.se/sfst?bet=2024:404", record.getLawTextUrl())
        );
    }

}
