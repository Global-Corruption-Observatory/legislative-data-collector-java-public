package com.precognox.ceu.legislative_data_collector.sweden;

import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LawTextCollectorTest {

    private LawTextCollector parser = new LawTextCollector(null, null);

    @Test
    void testLaw_2009_400() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/law_text_test_2009_400.html");
        testPage.setPageUrl("http://rkrattsbaser.gov.se/sfst?bet=2009:400/");

        Optional<String> result = parser.processPage(testPage);

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("1 § Denna lag innehåller"));
        assertTrue(result.get().endsWith("och offentliggöra uppgifter (44 kap.)."));
    }

    @Test
    void testLaw_2023_490() throws IOException {
        PageSource testPage = getPageSourceObj("/sweden/law_text_test_2023_490.html");
        testPage.setPageUrl("http://rkrattsbaser.gov.se/sfst?bet=2023:490/");

        Optional<String> result = parser.processPage(testPage);

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("/Träder i kraft I:2024-01-01/"));
        assertTrue(result.get().endsWith("lämnas senast den 30 september 2028."));
    }
}
