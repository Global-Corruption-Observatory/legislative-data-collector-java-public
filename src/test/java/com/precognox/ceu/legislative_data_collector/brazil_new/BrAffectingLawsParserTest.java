package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.ResourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class BrAffectingLawsParserTest {

    @Test
    void test_13707_2018() throws IOException {
        LegislativeDataRecord record = new LegislativeDataRecord();
        PageSource page = ResourceLoader.getPageSourceObj("/brazil/test_pages/senado_2_13707_2018.html");

        BrAffectingLawsParser parser = new BrAffectingLawsParser(null, null);
        parser.parseAffectingLaws(record, page);

        assertAll(
                () -> assertEquals(4, record.getAffectingLawsCount()),
                () -> assertEquals(LocalDate.of(2019, 2, 8), record.getAffectingLawsFirstDate()),
                () -> assertIterableEquals(
                        List.of("9702/2019", "9711/2019", "13857/2019", "13897/2019"),
                        record.getBrazilCountrySpecificVariables().getAffectingLaws()
                )
        );
    }

}
