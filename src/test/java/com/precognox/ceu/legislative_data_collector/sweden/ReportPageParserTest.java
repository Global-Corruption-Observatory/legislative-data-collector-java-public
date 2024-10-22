package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportPageParserTest {

    private ReportPageParser parser = new ReportPageParser(null);

    @Test
    void test() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/report_2022_23_juu31.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/betankande/hemliga-tvangsmedel-effektiva-verktyg-for-att_ha01juu31/");

        Amendment testAmendment = new Amendment();

        parser.parseVotes(testAmendment, testPage);

        assertEquals(19, testAmendment.getVotesInFavor());
        assertEquals(283, testAmendment.getVotesAgainst());
        assertEquals(47, testAmendment.getVotesAbstention());

        String expectedDebateText = new String(
                getClass().getResourceAsStream("/sweden/report_2022_23_juu31_expected_debate_text.txt").readAllBytes()
        );
        int expectedPlenarySize = TextUtils.getLengthWithoutWhitespace(expectedDebateText);

        Integer plenarySize = parser.parsePlenarySize(testPage);

        assertNotNull(plenarySize);
        assertThat(plenarySize).isCloseTo(expectedPlenarySize, Offset.offset(250));
    }

}