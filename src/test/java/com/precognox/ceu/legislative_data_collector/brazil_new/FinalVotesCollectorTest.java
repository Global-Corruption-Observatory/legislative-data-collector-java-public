package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.common.ResourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.brazil.BrazilCountrySpecificVariables;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FinalVotesCollectorTest {

    @Test
    void testType2_526_2023() throws IOException {
        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        testRecord.setBillId("526/2023");

        PageSourceLoader mockLoader = Mockito.mock(PageSourceLoader.class);
        PageSource mockPage1 = new PageSource();
        mockPage1.setPageUrl("https://www.camara.leg.br/presenca-comissoes/votacao-portal?reuniao=67239");
        mockPage1.setRawSource(ResourceLoader.getResourceAsString("/brazil/test_pages/final_votes_526_2023.html"));
        Mockito.when(mockLoader.loadFromDbOrFetchWithHttpGet(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq(mockPage1.getPageUrl())
                ))
                .thenReturn(Optional.of(mockPage1));

        PageSource mockPage2 = new PageSource();
        mockPage2.setPageUrl(
                "https://www.camara.leg.br/presenca-comissoes/votacao-portal?reuniao=67239&itemVotacao=11374");
        mockPage2.setRawSource(ResourceLoader.getResourceAsString("/brazil/test_pages/final_votes_526_2023.html"));
        Mockito.when(mockLoader.loadFromDbOrFetchWithHttpGet(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq(mockPage2.getPageUrl())
                ))
                .thenReturn(Optional.of(mockPage2));

        FinalVotesCollector finalVotesCollector = new FinalVotesCollector(null, null, mockLoader);

        //starting page is https://www.camara.leg.br/evento-legislativo/67239
        PageSource startingPage = ResourceLoader.getPageSourceObj("/brazil/test_pages/final_votes_session.html");
        finalVotesCollector.parsePreType2(startingPage, testRecord);

        assertEquals(280, testRecord.getFinalVoteFor());
        assertEquals(97, testRecord.getFinalVoteAgainst());
        assertEquals(1, testRecord.getFinalVoteAbst());
    }

    @Test
    void testType2_4591_2012() throws IOException {
        //same test as above, but with a different bill id - 4591/2012
        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        testRecord.setBillId("4591/2012");

        PageSourceLoader mockLoader = Mockito.mock(PageSourceLoader.class);
        PageSource mockPage1 = new PageSource();
        mockPage1.setPageUrl("https://www.camara.leg.br/presenca-comissoes/votacao-portal?reuniao=67239");
        mockPage1.setRawSource(ResourceLoader.getResourceAsString("/brazil/test_pages/final_votes_4591_2012.html"));
        Mockito.when(mockLoader.loadFromDbOrFetchWithHttpGet(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq(mockPage1.getPageUrl())
                ))
                .thenReturn(Optional.of(mockPage1));

        PageSource mockPage2 = new PageSource();
        mockPage2.setPageUrl(
                "https://www.camara.leg.br/presenca-comissoes/votacao-portal?reuniao=67239&itemVotacao=11375");
        mockPage2.setRawSource(ResourceLoader.getResourceAsString("/brazil/test_pages/final_votes_4591_2012.html"));
        Mockito.when(mockLoader.loadFromDbOrFetchWithHttpGet(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq(mockPage2.getPageUrl())
                ))
                .thenReturn(Optional.of(mockPage2));

        FinalVotesCollector finalVotesCollector = new FinalVotesCollector(null, null, mockLoader);

        //starting page is https://www.camara.leg.br/evento-legislativo/67239
        PageSource startingPage = ResourceLoader.getPageSourceObj("/brazil/test_pages/final_votes_session.html");
        finalVotesCollector.parsePreType2(startingPage, testRecord);

        assertEquals(14, testRecord.getFinalVoteFor());
        assertEquals(0, testRecord.getFinalVoteAgainst());
        //assertEquals(0, testRecord.getFinalVoteAbst());
    }

    @Test
    void testType1_1269_2022() throws IOException {
        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        PageSource containerPage = ResourceLoader.getPageSourceObj("/brazil/test_pages/final_votes_1269_2022.html");
        PageSource iframePage = ResourceLoader.getPageSourceObj("/brazil/test_pages/final_votes_1269_2022_iframe.html");

        PageSourceLoader mockLoader = Mockito.mock(PageSourceLoader.class);
        Mockito.when(mockLoader.loadFromDbOrFetchWithHttpGet(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq("https://www.camara.leg.br/internet/votacao/mostraVotacao.asp?ideVotacao=12036")
                ))
                .thenReturn(Optional.of(iframePage));

        FinalVotesCollector instance = new FinalVotesCollector(null, null, mockLoader);
        instance.parseType1(containerPage, testRecord);

        assertEquals(343, testRecord.getFinalVoteFor());
        assertEquals(11, testRecord.getFinalVoteAgainst());
        assertEquals(1, testRecord.getFinalVoteAbst());
    }

    @Test
    void test_2253_2022() throws IOException {
        PageSource testPage = ResourceLoader.getPageSourceObj("/brazil/test_pages/final_votes_2253_2022.html");
        testPage.setPageUrl("https://www25.senado.leg.br/web/atividade/materias/-/materia/154451/votacoes#votacao_6818");

        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        FinalVotesCollector instance = new FinalVotesCollector(null, null, null);
        instance.parseType3(testPage, testRecord);

        assertEquals(62, testRecord.getFinalVoteFor());
        assertEquals(2, testRecord.getFinalVoteAgainst());
        assertEquals(1, testRecord.getFinalVoteAbst());
    }

    @Test
    void test_5101_2023() throws IOException {
        PageSourceLoader mockLoader = Mockito.mock(PageSourceLoader.class);
        PageSource testVotesPage =
                ResourceLoader.getPageSourceObj("/brazil/test_pages/final_votes_5101_2023.html");

        Mockito.when(mockLoader.loadFromDbOrFetchWithHttpGet(
                        Mockito.any(),
                        Mockito.any(),
                        Mockito.eq("https://www.camara.leg.br/presenca-comissoes/votacao-portal?reuniao=73076&itemVotacao=62939")
                ))
                .thenReturn(Optional.of(testVotesPage));

        BrazilCountrySpecificVariables spec = new BrazilCountrySpecificVariables();
        spec.setVotesPageUrl("https://www.camara.leg.br/presenca-comissoes/votacao-portal?reuniao=73076&itemVotacao=62939");

        LegislativeDataRecord testRecord = new LegislativeDataRecord(Country.BRAZIL);
        testRecord.setBrazilCountrySpecificVariables(spec);

        FinalVotesCollector instance = new FinalVotesCollector(null, null, mockLoader);
        instance.processBill(testRecord);

        assertNull(testRecord.getFinalVoteFor());
        assertNull(testRecord.getFinalVoteAgainst());
        assertNull(testRecord.getFinalVoteAbst());
    }

}
