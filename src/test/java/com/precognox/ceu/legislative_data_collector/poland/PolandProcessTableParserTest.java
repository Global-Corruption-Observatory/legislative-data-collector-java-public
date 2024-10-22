package com.precognox.ceu.legislative_data_collector.poland;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.poland.parsers.PolandProcessTableParser;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import com.precognox.ceu.legislative_data_collector.utils.PdfUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getResourceAsBytes;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class PolandProcessTableParserTest {

    @InjectMocks
    private PolandProcessTableParser instance;

    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);
    private final PdfParser pdfParser = Mockito.mock(PdfParser.class);

    @Test
    @Disabled
    void parseProcessTable_term9_pr3238() throws IOException {
        // Given
        String processTableUrl = "https://www.sejm.gov.pl/Sejm9.nsf/PrzebiegProc.xsp?nr=3238";
        PageSource testPage = getPageSourceObj("/poland/PrNr_3238.html");
        testPage.setPageUrl(processTableUrl);
        String commReport = PdfUtils.extractText(getResourceAsBytes("/poland/3356-A.pdf"));
        String commReportTextUrl = "https://orka.sejm.gov.pl/Druki9ka.nsf/0/8E351AD66D54FA1EC12589D0002D2FF4/%24File/3356-A.pdf";

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pageSourceRepository.findByPageUrl(processTableUrl)).thenReturn(Optional.of(testPage));
        Mockito.when(pdfParser.tryPdfTextExtraction(commReportTextUrl)).thenReturn(Optional.of(commReport));

        // When
        instance.parseProcessTableData(processTableUrl, dataRecord);

        // Then
        Assertions.assertAll(()-> Assertions.assertEquals(7, dataRecord.getStagesCount()),
                ()-> Assertions.assertEquals(7, dataRecord.getStagesCount()),
                ()-> Assertions.assertEquals(236, dataRecord.getFinalVoteFor()),
                ()-> Assertions.assertEquals(185, dataRecord.getFinalVoteAgainst()),
                ()-> Assertions.assertEquals(32, dataRecord.getFinalVoteAbst()),
                ()-> Assertions.assertEquals("Stanowisko Senatu", dataRecord.getStages().get(3).getName()),
                ()-> Assertions.assertEquals(LocalDate.of(2023, 6, 20), dataRecord.getStages().get(3).getDate()),
                ()-> Assertions.assertEquals(4, dataRecord.getStages().get(3).getStageNumber())
        );

        Mockito.verify(pageSourceRepository, Mockito.times(1)).findByPageUrl(processTableUrl);
        Mockito.verify(pdfParser, Mockito.times(3)).tryPdfTextExtraction(any());
        Mockito.verifyNoMoreInteractions(pageSourceRepository);
        Mockito.verifyNoMoreInteractions(pdfParser);
    }

    @Test
    void parseProcessTable_term3_pr15() throws IOException {
        // Given
        String processTableUrl = "https://orka.sejm.gov.pl/proc3.nsf/opisy/15.htm";
        PageSource testPage = getPageSourceObj("/poland/15.html");
        testPage.setPageUrl(processTableUrl);
        String commReport = PdfUtils.extractText(getResourceAsBytes("/poland/32.pdf"));
        String commReportTextUrl = "https://orka.sejm.gov.pl/Rejestrd.nsf/wgdruku/32/$file/32.pdf";

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pageSourceRepository.findByPageUrl(processTableUrl)).thenReturn(Optional.of(testPage));
        Mockito.when(pdfParser.tryPdfTextExtraction(commReportTextUrl)).thenReturn(Optional.ofNullable(commReport));

        // When
        instance.parseProcessTableData(processTableUrl, dataRecord);

        // Then
        Assertions.assertEquals(7, dataRecord.getStagesCount());
        Assertions.assertEquals(387, dataRecord.getFinalVoteFor());
        Assertions.assertEquals(0, dataRecord.getFinalVoteAgainst());
        Assertions.assertEquals(2, dataRecord.getFinalVoteAbst());
        Assertions.assertEquals("I CZYTANIE W KOMISJACH", dataRecord.getStages().get(0).getName());
        Assertions.assertEquals(LocalDate.of(1997, 11, 7), dataRecord.getStages().get(0).getDate());
        Assertions.assertEquals(1, dataRecord.getStages().get(0).getStageNumber());
        Assertions.assertNull(dataRecord.getStages().get(0).getDebateSize());
        Assertions.assertEquals("STANOWISKO SENATU", dataRecord.getStages().get(3).getName());
        Assertions.assertEquals(LocalDate.of(1997, 11, 21), dataRecord.getStages().get(3).getDate());

        Mockito.verify(pageSourceRepository, Mockito.times(1)).findByPageUrl(processTableUrl);
        Mockito.verify(pdfParser, Mockito.times(3)).tryPdfTextExtraction(any());
        Mockito.verifyNoMoreInteractions(pageSourceRepository);
        Mockito.verifyNoMoreInteractions(pdfParser);
    }

    @Test
    void parseProcessTable_term3_pr2375_two_third_reading_stages() throws IOException {
        // Given
        String processTableUrl = "https://orka.sejm.gov.pl/proc3.nsf/opisy/2375.htm";
        PageSource testPage = getPageSourceObj("/poland/2375.html");
        testPage.setPageUrl(processTableUrl);
        String commReport = PdfUtils.extractText(getResourceAsBytes("/poland/32.pdf"));
        String commReportTextUrl = "https://orka.sejm.gov.pl/Rejestrd.nsf/wgdruku/32/$file/32.pdf";

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pageSourceRepository.findByPageUrl(processTableUrl)).thenReturn(Optional.of(testPage));
        Mockito.when(pdfParser.tryPdfTextExtraction(commReportTextUrl)).thenReturn(Optional.ofNullable(commReport));

        // When
        instance.parseProcessTableData(processTableUrl, dataRecord);

        //Then
        Assertions.assertEquals(8, dataRecord.getStagesCount());
        Assertions.assertEquals(399, dataRecord.getFinalVoteFor());
        Assertions.assertEquals(1, dataRecord.getFinalVoteAgainst());
        Assertions.assertEquals(5, dataRecord.getFinalVoteAbst());
        Assertions.assertEquals("I CZYTANIE NA POSIEDZENIU SEJMU", dataRecord.getStages().get(0).getName());
        Assertions.assertEquals(LocalDate.of(2000, 11, 28), dataRecord.getStages().get(0).getDate());
        Assertions.assertEquals("III CZYTANIE NA POSIEDZENIU SEJMU", dataRecord.getStages().get(2).getName());
        Assertions.assertEquals(LocalDate.of(2000, 12, 8), dataRecord.getStages().get(2).getDate());
        Assertions.assertEquals("III CZYTANIE NA POSIEDZENIU SEJMU", dataRecord.getStages().get(3).getName());
        Assertions.assertEquals(LocalDate.of(2000, 12, 15), dataRecord.getStages().get(3).getDate());
        Assertions.assertEquals(1, dataRecord.getStages().get(0).getStageNumber());
        Assertions.assertNull(dataRecord.getStages().get(0).getDebateSize());

        Mockito.verify(pageSourceRepository, Mockito.times(1)).findByPageUrl(processTableUrl);
        Mockito.verify(pdfParser, Mockito.times(3)).tryPdfTextExtraction(any());
        Mockito.verifyNoMoreInteractions(pageSourceRepository);
        Mockito.verifyNoMoreInteractions(pdfParser);
    }

    @Test
    void parseProcessTable_term3_pr2271_only_for_votes() throws IOException {
        // Given
        String processTableUrl = "https://orka.sejm.gov.pl/proc3.nsf/opisy/2271.htm";
        PageSource testPage = getPageSourceObj("/poland/2271.html");
        testPage.setPageUrl(processTableUrl);
        String commReport = PdfUtils.extractText(getResourceAsBytes("/poland/32.pdf"));
        String commReportTextUrl = "https://orka.sejm.gov.pl/Rejestrd.nsf/wgdruku/32/$file/32.pdf";

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pageSourceRepository.findByPageUrl(processTableUrl)).thenReturn(Optional.of(testPage));
        Mockito.when(pdfParser.tryPdfTextExtraction(commReportTextUrl)).thenReturn(Optional.ofNullable(commReport));

        // When
        instance.parseProcessTableData(processTableUrl, dataRecord);

        //Then
        Assertions.assertEquals(435, dataRecord.getFinalVoteFor());
        Assertions.assertEquals(0, dataRecord.getFinalVoteAgainst());
        Assertions.assertEquals(0, dataRecord.getFinalVoteAbst());
        Assertions.assertEquals("I CZYTANIE W KOMISJACH", dataRecord.getStages().get(0).getName());
        Assertions.assertNull(dataRecord.getStages().get(0).getDate());

        Mockito.verify(pageSourceRepository, Mockito.times(1)).findByPageUrl(processTableUrl);
        Mockito.verify(pdfParser, Mockito.times(3)).tryPdfTextExtraction(any());
        Mockito.verifyNoMoreInteractions(pageSourceRepository);
        Mockito.verifyNoMoreInteractions(pdfParser);
    }

    @Test
    void parseProcessTable_term3_pr741_only_against_votes() throws IOException {
        // Given
        String processTableUrl = "https://orka.sejm.gov.pl/proc4.nsf/opisy/741.htm";
        PageSource testPage = getPageSourceObj("/poland/741.html");
        testPage.setPageUrl(processTableUrl);
        String commReport = PdfUtils.extractText(getResourceAsBytes("/poland/32.pdf"));
        String commReportTextUrl = "https://orka.sejm.gov.pl/Rejestrd.nsf/wgdruku/32/$file/32.pdf";

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pageSourceRepository.findByPageUrl(processTableUrl)).thenReturn(Optional.of(testPage));
        Mockito.when(pdfParser.tryPdfTextExtraction(commReportTextUrl)).thenReturn(Optional.ofNullable(commReport));

        // When
        instance.parseProcessTableData(processTableUrl, dataRecord);

        //Then
        Assertions.assertEquals(0, dataRecord.getFinalVoteFor());
        Assertions.assertEquals(391, dataRecord.getFinalVoteAgainst());
        Assertions.assertEquals(0, dataRecord.getFinalVoteAbst());
        Assertions.assertEquals("PRACA W KOMISJACH NAD STANOWISKIEM SENATU", dataRecord.getStages().get(4).getName());

        Mockito.verify(pageSourceRepository, Mockito.times(1)).findByPageUrl(processTableUrl);
        Mockito.verify(pdfParser, Mockito.times(3)).tryPdfTextExtraction(any());
        Mockito.verifyNoMoreInteractions(pageSourceRepository);
        Mockito.verifyNoMoreInteractions(pdfParser);
    }
}
