package com.precognox.ceu.legislative_data_collector.colombia;

import com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.RecordBuilder;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.TextSource;
import com.precognox.ceu.legislative_data_collector.utils.ReadDatabaseService;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverWrapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getResourceAsString;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordBuilderTest {

    private final ReadDatabaseService readDatabaseService = Mockito.mock(ReadDatabaseService.class);
    private final WebDriverWrapper fakeBrowser = Mockito.mock(WebDriverWrapper.class);

    @Test
    void shouldNotBuildRecordWhenAlreadyExists() throws IOException {
        // Given
        PageSource testPage = getPageSourceObj("/colombia/12664.html");
        testPage.setRawSource(getResourceAsString("/colombia/12664.html"));
        String billPageUrl = "https://congresovisible.uniandes.edu.co/proyectos-de-ley/ppor-medio-de-la-cual-se" +
                "-implementa-el-manual-de-identidad-visual-de-las-entidades-estatales-se-prohiben-las-marcas-de" +
                "-gobierno-y-se-establecen-medidas-para-la-austeridad-en-la-publicidad-estatal-manual-de-identidad" +
                "-visual-de-las-entidades-estatales/12664/";
        testPage.setPageUrl(billPageUrl);

        RecordBuilder instance = new RecordBuilder(testPage, readDatabaseService);
        LegislativeDataRecord dataRecord = new LegislativeDataRecord();
        dataRecord.setCountry(Country.COLOMBIA);
        dataRecord.setBillPageUrl(billPageUrl);

        when(readDatabaseService.findByBillPageUrl(billPageUrl)).thenReturn(Optional.of(dataRecord));

        // When
        instance.buildRecord(fakeBrowser);

        // Then
        assertNull(instance.getDataRecord());
        verify(readDatabaseService, times(1)).findByBillPageUrl(billPageUrl);
    }

    @Test
    void shouldBuildRecordWhenDoesNotExistNoVoting() throws IOException {
        // Given
        PageSource testPage = getPageSourceObj("/colombia/12664.html");
        testPage.setRawSource(getResourceAsString("/colombia/12664.html"));
        String billPageUrl = "https://congresovisible.uniandes.edu.co/proyectos-de-ley/ppor-medio-de-la-cual-se" +
                "-implementa-el-manual-de-identidad-visual-de-las-entidades-estatales-se-prohiben-las-marcas-de" +
                "-gobierno-y-se-establecen-medidas-para-la-austeridad-en-la-publicidad-estatal-manual-de-identidad" +
                "-visual-de-las-entidades-estatales/12664/";
        testPage.setPageUrl(billPageUrl);

        RecordBuilder instance = new RecordBuilder(testPage, readDatabaseService);

        when(readDatabaseService.findByBillPageUrl(billPageUrl)).thenReturn(Optional.empty());
        ColombiaDataParser.votePages = List.of(new Document("testUri"));

        TextSource testSource = new TextSource();
        testSource.setDownloadUrl("testUrl");
        testSource.setTextContent("testContent");
        when(readDatabaseService.findByTextTypeAndIdentifierAndCountry(any(), any(), any())).thenReturn(Optional.of(testSource));
        when(readDatabaseService.findByPageTypeAndPageUrl(any(), any())).thenReturn(Optional.of(testPage));

        // When
        instance.buildRecord(fakeBrowser);

        // Then
        LegislativeDataRecord dataRecord = instance.getDataRecord();
        assertAll(
                () -> assertEquals("Por medio de la cual se implementa el manual de identidad visual de las " +
                        "entidades estatales, se prohíben las marcas de gobierno y se establecen medidas para la " +
                        "austeridad en la publicidad estatal. [Manual de identidad visual de las entidades estatales]", dataRecord.getBillTitle()),
                () -> assertEquals("163/22-323/23", dataRecord.getBillId()),
                () -> assertEquals(billPageUrl, dataRecord.getBillPageUrl()),
                () -> assertEquals(Country.COLOMBIA, dataRecord.getCountry()),
                () -> assertEquals(Boolean.TRUE, dataRecord.getOriginalLaw()),
                () -> assertEquals(LegislativeDataRecord.BillStatus.PASS, dataRecord.getBillStatus()),
                () -> assertEquals("2023/2345", dataRecord.getLawId()),
                () -> assertEquals(OriginType.INDIVIDUAL_MP, dataRecord.getOriginType()),
                () -> assertEquals(LocalDate.of(2022, 8, 30), dataRecord.getDateIntroduction()),
                () -> assertEquals(LocalDate.of(2023, 12, 30), dataRecord.getDatePassing()),
                () -> assertEquals(LegislativeDataRecord.ProcedureType.REGULAR, dataRecord.getProcedureTypeStandard()),
                () -> assertEquals("Standard", dataRecord.getProcedureTypeNational()),
                () -> assertEquals(6, dataRecord.getStagesCount()),
                () -> assertEquals(2, dataRecord.getCommittees().size()),
                () -> assertEquals("Tercera de Senado", dataRecord.getCommittees().get(0).getName()),
                () -> assertEquals("Treasury, Credit, Finances, Banks, Taxes.", dataRecord.getCommittees().get(0).getRole()),
                () -> assertEquals(LocalDate.of(2023, 8, 15), dataRecord.getCommittees().get(0).getDate()),
                () -> assertEquals("Cuarta de Cámara", dataRecord.getCommittees().get(1).getName()),
                () -> assertEquals("Procurement, Fiscal Control, Budget.", dataRecord.getCommittees().get(1).getRole()),
                () -> assertEquals(LocalDate.of(2022, 12, 14), dataRecord.getCommittees().get(1).getDate()),
                () -> assertEquals(4, dataRecord.getOriginators().size()),
                () -> assertEquals("Jonathan Ferney Jota Pe Pulido Hernández", dataRecord.getOriginators().get(0).getName()),
                () -> assertEquals("Senado de la República", dataRecord.getOriginators().get(0).getAffiliation()),
                () -> assertNull(dataRecord.getFinalVoteFor()),
                () -> assertNull(dataRecord.getFinalVoteAgainst()),
                () -> assertNull(dataRecord.getFinalVoteAbst()),
                () -> assertEquals("http://www.secretariasenado.gov.co/senado/basedoc/ley_2345_2023.html", dataRecord.getLawTextUrl())
        );
        verify(readDatabaseService, times(1)).findByBillPageUrl(billPageUrl);
        verify(readDatabaseService, times(14)).findByTextTypeAndIdentifierAndCountry(any(), any(), any());
        verify(readDatabaseService, times(5)).findByPageTypeAndPageUrl(any(), any());
    }

    @Test
    void shouldBuildRecordWhenDoesNotExistWithVoting() throws IOException {
        // Given
        PageSource testPage = getPageSourceObj("/colombia/12558.html");
        testPage.setRawSource(getResourceAsString("/colombia/12558.html"));
        String billPageUrl = "https://congresovisible.uniandes.edu.co/proyectos-de-ley/ppor-medio-de-la-cual-se-adopta-" +
                "una-reforma-tributaria-para-la-igualdad-y-la-justicia-social-y-se-dictan-otras-disposiciones-reforma-" +
                "tributaria/12558/";
        testPage.setPageUrl(billPageUrl);

        RecordBuilder instance = new RecordBuilder(testPage, readDatabaseService);

        when(readDatabaseService.findByBillPageUrl(billPageUrl)).thenReturn(Optional.empty());

        Document votingPage = Jsoup.parse(getResourceAsString("/colombia/voting_12558.html"));
        ColombiaDataParser.votePages = List.of(votingPage);

        TextSource testSource = new TextSource();
        testSource.setDownloadUrl("testUrl");
        testSource.setTextContent("testContent");
        when(readDatabaseService.findByTextTypeAndIdentifierAndCountry(any(), any(), any())).thenReturn(Optional.of(testSource));
        when(readDatabaseService.findByPageTypeAndPageUrl(any(), any())).thenReturn(Optional.of(testPage));

        // When
        instance.buildRecord(fakeBrowser);

        // Then
        LegislativeDataRecord dataRecord = instance.getDataRecord();
        assertAll(
                () -> assertEquals("118/22-131/22", dataRecord.getBillId()),
                () -> assertEquals(Boolean.TRUE, dataRecord.getOriginalLaw()),
                () -> assertEquals(LegislativeDataRecord.BillStatus.PASS, dataRecord.getBillStatus()),
                () -> assertEquals("2022/2277", dataRecord.getLawId()),
                () -> assertEquals(OriginType.GOVERNMENT, dataRecord.getOriginType()),
                () -> assertEquals(LegislativeDataRecord.ProcedureType.EXCEPTIONAL, dataRecord.getProcedureTypeStandard()),
                () -> assertEquals("Urgent Message. Art 163 Political Constitution of Colombia", dataRecord.getProcedureTypeNational()),
                () -> assertEquals(63, dataRecord.getFinalVoteFor()),
                () -> assertEquals(13, dataRecord.getFinalVoteAgainst()),
                () -> assertEquals(3, dataRecord.getFinalVoteAbst())
        );
        verify(readDatabaseService, times(1)).findByBillPageUrl(billPageUrl);
        verify(readDatabaseService, times(20)).findByTextTypeAndIdentifierAndCountry(any(), any(), any());
        verify(readDatabaseService, times(1)).findByPageTypeAndPageUrl(any(), any());
    }
}
