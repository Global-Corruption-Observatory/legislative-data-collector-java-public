package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AmendmentPageParserTest {

    private final AmendmentPageParser instance = new AmendmentPageParser(null, null, null);

    @Test
    void test() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/amendment_2022_23_2403.html");
        testPage.setPageUrl("https://www.riksdagen.se/sv/dokument-och-lagar/dokument/motion/med-anledning-av-prop.-202223126-hemliga_ha022403/");

        Amendment result = new Amendment();

        instance.processPage(result, testPage);

        assertEquals("2022/23:2403", result.getAmendmentId());
        assertEquals("Justitieutskottet", result.getCommitteeName());
        assertEquals(Amendment.Outcome.REJECTED, result.getOutcome());

        assertNotNull(result.getOriginators());
        assertEquals(5, result.getOriginators().size());

        assertEquals("Gudrun Nordborg", result.getOriginators().get(0).getName());
        assertEquals("Vänsterpartiet", result.getOriginators().get(0).getAffiliation());

        assertEquals("Nadja Awad", result.getOriginators().get(1).getName());
        assertEquals("Vänsterpartiet", result.getOriginators().get(1).getAffiliation());

        assertEquals("Tony Haddou", result.getOriginators().get(2).getName());
        assertEquals("Vänsterpartiet", result.getOriginators().get(2).getAffiliation());

        assertEquals("Lotta Johnsson Fornarve", result.getOriginators().get(3).getName());
        assertEquals("Vänsterpartiet", result.getOriginators().get(3).getAffiliation());

        assertEquals("Jessica Wetterling", result.getOriginators().get(4).getName());
        assertEquals("Vänsterpartiet", result.getOriginators().get(4).getAffiliation());

        assertEquals(
                "https://data.riksdagen.se/fil/18548C54-8E61-4855-A27E-A62E622AD820", result.getTextSourceUrl());
    }

}
