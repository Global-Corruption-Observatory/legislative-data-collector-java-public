package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.ResourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StagesPageParserTest {

    @Test
    void test_583_2011() throws IOException {
        String testPage = ResourceLoader.getResourceAsString("/brazil/test_pages/stages_page_583_2011.html");

        List<LegislativeStage> result = new StagesPageParser(null, null, null).processPage(testPage);

        Assertions.assertAll(
                () -> assertEquals(2, result.size()),
                () -> assertEquals(1, result.get(0).getStageNumber()),
                () -> assertEquals("Casa Iniciadora (Câmara)", result.get(0).getName()),
                () -> assertEquals(LocalDate.of(2011, 2, 23), result.get(0).getDate()),
                () -> assertEquals(2, result.get(1).getStageNumber()),
                () -> assertEquals("Casa Revisora (Senado)", result.get(1).getName()),
                () -> assertEquals(LocalDate.of(2022, 8, 4), result.get(1).getDate())
        );
    }

    @Test
    void test_7508_2002() throws IOException {
        String testPage = ResourceLoader.getResourceAsString("/brazil/test_pages/stages_page_7508_2002.html");

        List<LegislativeStage> result = new StagesPageParser(null, null, null).processPage(testPage);

        Assertions.assertAll(
                () -> assertEquals(2, result.size()),
                () -> assertEquals(1, result.get(0).getStageNumber()),
                () -> assertEquals("Casa Iniciadora (Câmara)", result.get(0).getName()),
                () -> assertEquals(LocalDate.of(2002, 12, 26), result.get(0).getDate()),
                () -> assertEquals(2, result.get(1).getStageNumber()),
                () -> assertEquals("Casa Revisora (Senado)", result.get(1).getName()),
                () -> assertEquals(LocalDate.of(2003, 11, 5), result.get(1).getDate())
        );
    }

    @Test
    void test_24_1973() throws IOException {
        //original URL: https://www.congressonacional.leg.br/materias/materias-bicamerais/-/ver/pls-24-1973
        String testPage = ResourceLoader.getResourceAsString("/brazil/test_pages/stages_page_24_1973.html");

        List<LegislativeStage> result = new StagesPageParser(null, null, null).processPage(testPage);

        Assertions.assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals(1, result.get(0).getStageNumber()),
                () -> assertEquals("Casa Iniciadora (Senado)", result.get(0).getName()),
                () -> assertEquals(LocalDate.of(1973, 5, 5), result.get(0).getDate())
        );
    }

}
