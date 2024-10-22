package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.common.ResourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.brazil.BrazilCountrySpecificVariables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CamaraPageParserTest {

    private PageSourceLoader mockLoader;

    @InjectMocks
    private CamaraPageParser parser;

    public CamaraPageParserTest() {
        mockLoader = Mockito.mock(PageSourceLoader.class);
        Mockito.when(mockLoader.loadFromDbOrFetchWithHttpGet(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void test_6932_2013() throws IOException {
        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        PageSource testPage =
                ResourceLoader.getPageSourceObj("/brazil/test_pages/camara_bill_detail_page_6932_2013.html");

        parser.parseCamaraPage(testRecord, testPage);

        assertAll(
                () -> assertEquals(LegislativeDataRecord.BillStatus.REJECT, testRecord.getBillStatus()),
                () -> assertEquals(false, testRecord.getOriginalLaw())
        );
    }

    @Test
    void test_7508_2002() throws IOException {
        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        PageSource testPage =
                ResourceLoader.getPageSourceObj("/brazil/test_pages/camara_bill_detail_page_7508_2002.html");

        parser.parseCamaraPage(testRecord, testPage);

        assertAll(
                () -> assertEquals(LegislativeDataRecord.ProcedureType.EXCEPTIONAL, testRecord.getProcedureTypeStandard()),
                () -> assertEquals("Urgência", testRecord.getProcedureTypeNational()),
                () -> assertEquals(LegislativeDataRecord.BillStatus.PASS, testRecord.getBillStatus()),
                () -> assertEquals("https://www.camara.leg.br/proposicoesWeb/prop_mostrarintegra?codteor=111134&filename=PL%207508/2002", testRecord.getBillTextUrl()),
                () -> assertEquals(1, testRecord.getCommittees().size()),
                () -> assertEquals("Comissão de Trabalho, de Administração e Serviço Público ( CTASP )", testRecord.getCommittees().get(0).getName()),
                () -> assertEquals(LocalDate.of(2003, 2, 20), testRecord.getCommittees().get(0).getDate()),
                () -> assertEquals(1, testRecord.getOriginators().size()),
                () -> assertEquals("Poder Executivo", testRecord.getOriginators().get(0).getName()),
                () -> assertNull(testRecord.getOriginators().get(0).getAffiliation()),
                () -> assertEquals(1, testRecord.getModifiedLawsCount()),
                () -> assertEquals(false, testRecord.getOriginalLaw()),
                () -> assertEquals(30, testRecord.getAmendmentCount()),
                () -> assertIterableEquals(List.of("https://www.camara.leg.br/proposicoesWeb/prop_emendas?idProposicao=103297&subst=0"), testRecord.getBrazilCountrySpecificVariables().getAmendmentPageLinks()),
                () -> assertIterableEquals(List.of("LEI-9650-1998-05-27"), testRecord.getModifiedLaws())
        );
    }

    @Test
    void test_354_2015() throws IOException {
        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        PageSource testPage =
                ResourceLoader.getPageSourceObj("/brazil/test_pages/camara_bill_detail_page_354_2015.html");

        parser.parseCamaraPage(testRecord, testPage);

        assertAll(
                () -> assertEquals(LegislativeDataRecord.ProcedureType.REGULAR, testRecord.getProcedureTypeStandard()),
                () -> assertEquals("Ordinário (Art. 151, III, RICD)", testRecord.getProcedureTypeNational()),
                () -> assertEquals(LegislativeDataRecord.BillStatus.REJECT, testRecord.getBillStatus()),
                () -> assertEquals(1, testRecord.getOriginators().size()),
                () -> assertEquals("Major Olimpio", testRecord.getOriginators().get(0).getName()),
                () -> assertEquals("PDT/SP", testRecord.getOriginators().get(0).getAffiliation()),
                () -> assertEquals(1, testRecord.getModifiedLawsCount()),
                () -> assertEquals(false, testRecord.getOriginalLaw()),
                () -> assertEquals(0, testRecord.getAmendmentCount()),
                () -> assertEquals("https://www.camara.leg.br/proposicoesWeb/prop_mostrarintegra?codteor=1300145&filename=PL%20354/2015", testRecord.getBillTextUrl()),
                () -> assertIterableEquals(List.of("LEI-2848-1940-12-07"), testRecord.getModifiedLaws())
        );
    }

    @Test
    void test_1034_2022() throws IOException {
        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        PageSource testPage =
                ResourceLoader.getPageSourceObj("/brazil/test_pages/camara_bill_detail_page_1034_2022.html");

        parser.parseCamaraPage(testRecord, testPage);

        assertAll(
                () -> assertEquals("https://www.camara.leg.br/proposicoesWeb/prop_mostrarintegra?codteor=2160933&filename=PL%201034/2022", testRecord.getBillTextUrl()),
                () -> assertEquals(LegislativeDataRecord.BillStatus.ONGOING, testRecord.getBillStatus()),
                () -> assertEquals(true, testRecord.getOriginalLaw()),
                () -> assertEquals(LegislativeDataRecord.ProcedureType.REGULAR, testRecord.getProcedureTypeStandard()),
                () -> assertEquals("Ordinário (Art. 151, III, RICD)", testRecord.getProcedureTypeNational()),
                () -> assertEquals(0, testRecord.getAmendmentCount()),
                () -> assertEquals(4, testRecord.getCommitteeCount()),
                () -> assertEquals(4, testRecord.getCommittees().size()),
                () -> assertEquals("Comissão de Constituição e Justiça e de Cidadania (CCJC)", testRecord.getCommittees().get(0).getName()),
                () -> assertEquals("Comissão de Educação (CE)", testRecord.getCommittees().get(1).getName()),
                () -> assertEquals("Comissão de Cultura (CCULT)", testRecord.getCommittees().get(2).getName()),
                () -> assertEquals("Educação ( CE )", testRecord.getCommittees().get(3).getName()),
                () -> assertEquals(LocalDate.of(2022, 5, 9), testRecord.getCommittees().get(3).getDate())
        );
    }

    @Test
    void test_1269_2022() throws IOException {
        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        testRecord.setBrazilCountrySpecificVariables(new BrazilCountrySpecificVariables());

        PageSource testPage =
                ResourceLoader.getPageSourceObj("/brazil/test_pages/camara_bill_detail_page_1269_2022.html");

        parser.parseCamaraPage(testRecord, testPage);

        assertAll(
                () -> assertEquals(1, testRecord.getAmendmentCount()),
                () -> assertEquals(false, testRecord.getOriginalLaw()),
                () -> assertEquals(LegislativeDataRecord.BillStatus.PASS, testRecord.getBillStatus()),
                () -> assertEquals(LegislativeDataRecord.ProcedureType.EXCEPTIONAL, testRecord.getProcedureTypeStandard()),
                () -> assertEquals("Urgência (Art. 155, RICD)", testRecord.getProcedureTypeNational()),
                () -> assertEquals(1, testRecord.getCommitteeCount()),
                () -> assertEquals("Constituição e Justiça e de Cidadania ( CCJC )", testRecord.getCommittees().get(0).getName()),
                () -> assertEquals(LocalDate.of(2022, 5, 30), testRecord.getCommittees().get(0).getDate()),
                () -> assertEquals("http://www2.camara.gov.br/atividade-legislativa/plenario/chamadaExterna.html?link=http://www.camara.gov.br/internet/votacao/mostraVotacao.asp?ideVotacao=12036", testRecord.getBrazilCountrySpecificVariables().getVotesPageUrl())
        );
    }

    @Test
    void test_4591_2012() throws IOException {
        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        testRecord.setBrazilCountrySpecificVariables(new BrazilCountrySpecificVariables());

        PageSource testPage =
                ResourceLoader.getPageSourceObj("/brazil/test_pages/camara_bill_detail_page_4591_2012.html");

        parser.parseCamaraPage(testRecord, testPage);

        assertAll(
                () -> assertEquals(4, testRecord.getAmendmentCount()),
                () -> assertEquals(false, testRecord.getOriginalLaw()),
                () -> assertEquals(2, testRecord.getCommitteeCount()),
                () -> assertEquals(
                        "Comissão de Trabalho, de Administração e Serviço Público ( CTASP )",
                        testRecord.getCommittees().get(0).getName()
                ),
                () -> assertEquals(LocalDate.of(2012, 12, 4), testRecord.getCommittees().get(0).getDate()),
                () -> assertEquals(
                        "Constituição e Justiça e de Cidadania ( CCJC )",
                        testRecord.getCommittees().get(1).getName()
                ),
                () -> assertEquals(LocalDate.of(2013, 6, 6), testRecord.getCommittees().get(1).getDate()),
                () -> assertEquals(LegislativeDataRecord.BillStatus.PASS, testRecord.getBillStatus()),
                () -> assertEquals(
                        LegislativeDataRecord.ProcedureType.EXCEPTIONAL,
                        testRecord.getProcedureTypeStandard()
                ),
                () -> assertEquals("Urgência (Art. 154, RICD)", testRecord.getProcedureTypeNational()),
                () -> assertEquals(
                        "https://www.camara.leg.br/evento-legislativo/67239",
                        testRecord.getBrazilCountrySpecificVariables().getVotesPageUrl()
                )
        );
    }

    @Test
    void test_2260_2022() throws IOException {
        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        testRecord.setBrazilCountrySpecificVariables(new BrazilCountrySpecificVariables());

        PageSource testPage =
                ResourceLoader.getPageSourceObj("/brazil/test_pages/camara_bill_detail_page_2260_2022.html");

        parser.parseCamaraPage(testRecord, testPage);

        assertAll(
                () -> assertEquals(false, testRecord.getOriginalLaw()),
                () -> assertTrue(testRecord.getModifiedLaws().contains("LEI-13536-2017-12-15")),
                () -> assertEquals(2, testRecord.getCommittees().size()),
                () -> assertEquals("Defesa dos Direitos da Mulher ( CMULHER )", testRecord.getCommittees().get(0).getName()),
                () -> assertEquals(LocalDate.of(2023, 7, 26), testRecord.getCommittees().get(0).getDate()),
                () -> assertEquals("Previdência, Assis. Social, Infância, Adolescência e Família ( CPASF )", testRecord.getCommittees().get(1).getName()),
                () -> assertEquals(LocalDate.of(2024, 6, 19), testRecord.getCommittees().get(1).getDate()),
                () -> assertEquals(1, testRecord.getOriginators().size()),
                () -> assertEquals("Alessandro Vieira", testRecord.getOriginators().get(0).getName()),
                () -> assertEquals("PSDB/SE", testRecord.getOriginators().get(0).getAffiliation())
        );
    }

    @Test
    void test_709_2020() throws IOException {
        LegislativeDataRecord testRecord = new LegislativeDataRecord();
        testRecord.setBrazilCountrySpecificVariables(new BrazilCountrySpecificVariables());

        PageSource testPage =
                ResourceLoader.getPageSourceObj("/brazil/test_pages/camara_bill_detail_page_709_2020.html");

        parser.parseCamaraPage(testRecord, testPage);

        assertAll(
                () -> assertEquals(false, testRecord.getOriginalLaw()),
                () -> assertEquals(1, testRecord.getOriginators().size()),
                () -> assertEquals(0, testRecord.getCommitteeCount()),
                () -> assertEquals("Diego Andrade", testRecord.getOriginators().get(0).getName()),
                () -> assertEquals("PSD/MG", testRecord.getOriginators().get(0).getAffiliation())
        );
    }
}
