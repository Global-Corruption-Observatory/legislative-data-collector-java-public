package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.ResourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.brazil.BrazilCountrySpecificVariables;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SenadoPageParserTest {

    @Test
    void test_2253_2022() throws IOException {
        //test bill type, stages page url, final votes url
        PageSource testPage =
                ResourceLoader.getPageSourceObj("/brazil/test_pages/senado_bill_details_2253_2022.html");

        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        testRecord.setBrazilCountrySpecificVariables(new BrazilCountrySpecificVariables());
        new SenadoPageParser(null, null).parsePage(testRecord, testPage);

        assertAll(
                () -> assertEquals(false, testRecord.getOriginalLaw()),
                () -> assertEquals(LegislativeDataRecord.BillStatus.PASS, testRecord.getBillStatus()),
                () -> assertEquals(
                        "https://legis.senado.leg.br/sdleg-getter/documento?dm=9192063&ts=1719515792288&disposition=inline",
                        testRecord.getBillTextUrl()
                ),
                () -> assertEquals("Norma Geral", testRecord.getBillType()),
                () -> assertEquals(
                        "https://www25.senado.leg.br/web/atividade/materias/-/materia/154451/votacoes#votacao_6818",
                        testRecord.getBrazilCountrySpecificVariables().getVotesPageUrl()
                ),
                () -> assertEquals(
                        "https://www.congressonacional.leg.br/materias/materias-bicamerais/-/ver/pl-2253-2022",
                        testRecord.getBrazilCountrySpecificVariables().getStagesPageUrl()
                ),
                () -> assertEquals(LocalDate.of(2022, 8, 10), testRecord.getDateIntroduction()),
                () -> assertEquals(2, testRecord.getCommittees().size()),
                () -> assertEquals("SF-CSP - Comissão de Segurança Pública", testRecord.getCommittees().get(0).getName()),
                () -> assertEquals("SF-CCJ - Comissão de Constituição, Justiça e Cidadania", testRecord.getCommittees().get(1).getName()),
                () -> assertEquals(1, testRecord.getOriginators().size()),
                () -> assertEquals("Câmara dos Deputados", testRecord.getOriginators().get(0).getName()),
                () -> assertEquals(OriginType.GOVERNMENT, testRecord.getOriginType()),
                () -> assertNull(testRecord.getOriginators().get(0).getAffiliation()),
                () -> assertEquals(9, testRecord.getAmendmentCount()),
                () -> assertEquals(9, testRecord.getAmendments().size()),
                () -> assertEquals("EMENDA 1 - PL 2253/2022", testRecord.getAmendments().get(0).getAmendmentId()),
                () -> assertEquals(1, testRecord.getAmendments().get(0).getOriginators().size()),
                () -> assertEquals("Jorge Kajuru", testRecord.getAmendments().get(0).getOriginators().get(0).getName()),
                () -> assertEquals("PSB/GO", testRecord.getAmendments().get(0).getOriginators().get(0).getAffiliation()),
                () -> assertEquals(LocalDate.of(2023, 10, 6), testRecord.getAmendments().get(0).getDate()),
                () -> assertEquals(Amendment.Outcome.REJECTED, testRecord.getAmendments().get(0).getOutcome()),
                () -> assertEquals("https://legis.senado.leg.br/sdleg-getter/documento?dm=9478464&ts=1719515795362&disposition=inline&ts=1719515795362", testRecord.getAmendments().get(0).getTextSourceUrl()),
                () -> assertEquals("Comissão de Segurança Pública", testRecord.getAmendments().get(0).getCommitteeName()),
                () -> assertEquals("EMENDA 9 PLEN - PL 2253/2022", testRecord.getAmendments().get(8).getAmendmentId()),
                () -> assertEquals(1, testRecord.getAmendments().get(8).getOriginators().size()),
                () -> assertEquals("Fabiano Contarato", testRecord.getAmendments().get(8).getOriginators().get(0).getName()),
                () -> assertEquals("PT/ES", testRecord.getAmendments().get(8).getOriginators().get(0).getAffiliation()),
                () -> assertEquals(LocalDate.of(2024, 2, 20), testRecord.getAmendments().get(8).getDate()),
                () -> assertNull(testRecord.getAmendments().get(8).getOutcome()),
                () -> assertEquals("https://legis.senado.leg.br/sdleg-getter/documento?dm=9548691&ts=1719515795429&disposition=inline&ts=1719515795429", testRecord.getAmendments().get(8).getTextSourceUrl()),
                () -> assertEquals("Plenário do Senado Federal", testRecord.getAmendments().get(8).getCommitteeName())
        );
    }

    @Test
    void test_10_1971() throws IOException {
        PageSource testPage = ResourceLoader.getPageSourceObj("/brazil/test_pages/senado_bill_details_10_1971.html");

        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        testRecord.setBrazilCountrySpecificVariables(new BrazilCountrySpecificVariables());
        new SenadoPageParser(null, null).parsePage(testRecord, testPage);

        assertAll(
                () -> assertEquals(0, testRecord.getCommittees().size()),
                () -> assertEquals(true, testRecord.getOriginalLaw()),
                () -> assertEquals(LegislativeDataRecord.BillStatus.PASS, testRecord.getBillStatus()),
                () -> assertEquals(1, testRecord.getOriginators().size()),
                () -> assertEquals("Câmara dos Deputados", testRecord.getOriginators().get(0).getName()),
                () -> assertEquals(OriginType.GOVERNMENT, testRecord.getOriginType()),
                () -> assertNull(testRecord.getOriginators().get(0).getAffiliation())
        );
    }

    @Test
    void test_595_2015() throws IOException {
        PageSource testPage =
                ResourceLoader.getPageSourceObj("/brazil/test_pages/senado_bill_details_595_2015.html");

        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        testRecord.setBrazilCountrySpecificVariables(new BrazilCountrySpecificVariables());
        new SenadoPageParser(null, null).parsePage(testRecord, testPage);

        assertAll(
                () -> assertEquals(false, testRecord.getOriginalLaw()),
                () -> assertEquals(LegislativeDataRecord.BillStatus.REJECT, testRecord.getBillStatus()),
                () -> assertEquals(1, testRecord.getOriginators().size()),
                () -> assertEquals("Donizeti Nogueira", testRecord.getOriginators().get(0).getName()),
                () -> assertEquals("PT/TO", testRecord.getOriginators().get(0).getAffiliation()),
                () -> assertEquals(OriginType.INDIVIDUAL_MP, testRecord.getOriginType()),
                () -> assertEquals(3, testRecord.getCommittees().size()),
                () -> assertEquals(
                        "SF-CAE - Comissão de Assuntos Econômicos",
                        testRecord.getCommittees().get(0).getName()
                ),
                () -> assertEquals(
                        "SF-CCJ - Comissão de Constituição, Justiça e Cidadania",
                        testRecord.getCommittees().get(1).getName()
                ),
                () -> assertEquals(
                        "SF-CMA - Comissão de Meio Ambiente | Deliberação terminativa",
                        testRecord.getCommittees().get(2).getName()
                )
        );
    }
}
