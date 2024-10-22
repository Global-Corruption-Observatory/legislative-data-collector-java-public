package com.precognox.ceu.legislative_data_collector.poland;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.poland.parsers.PolandBillApiDataParser;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import com.precognox.ceu.legislative_data_collector.utils.PdfUtils;
import org.junit.jupiter.api.Assertions;
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
class PolandBillApiDataParserTest {

    @InjectMocks
    private PolandBillApiDataParser instance;

    private final PdfParser pdfParser = Mockito.mock(PdfParser.class);
    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);

    @Test
    void parseBillJson_DU_2023_2029() throws IOException {
        // Given
        PageSource testPage = getPageSourceObj("/poland/DU_2023_2029.json");
        testPage.setPageUrl("https://api.sejm.gov.pl/eli/acts/DU/2023/2029");

        PageSource modifiedLawsSource = getPageSourceObj("/poland/DU_2015_1265.json");
        String modifiedLawsSourceUrl = "https://api.sejm.gov.pl/eli/acts/DU/2015/1265";
        modifiedLawsSource.setPageUrl(modifiedLawsSourceUrl);

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pdfParser.tryPdfTextExtraction(any())).thenReturn(Optional.of("any"));
        Mockito.when(pageSourceRepository.findByPageUrl(any())).thenReturn(Optional.of(modifiedLawsSource));

        // When
        instance.parseBillApiData(testPage, dataRecord);

        //Then
        Assertions.assertEquals(Country.POLAND, dataRecord.getCountry());
        Assertions.assertEquals("Dz.U. 2023 poz. 2029", dataRecord.getLawId());
        Assertions.assertEquals("Ustawa z dnia 16 czerwca 2023 r. o zmianie ustawy - Prawo geologiczne i górnicze oraz niektórych innych ustaw", dataRecord.getBillTitle());
        Assertions.assertEquals(Boolean.FALSE, dataRecord.getOriginalLaw());
        Assertions.assertEquals("https://isap.sejm.gov.pl/isap.nsf/DocDetails.xsp?id=WDU20230002029", dataRecord.getBillPageUrl());
        Assertions.assertEquals(LegislativeDataRecord.BillStatus.PASS, dataRecord.getBillStatus());
        Assertions.assertEquals(LocalDate.of(2023, 9, 27), dataRecord.getDatePassing());
        Assertions.assertEquals(LocalDate.of(2023, 10, 28), dataRecord.getDateEnteringIntoForce());
        Assertions.assertEquals("https://isap.sejm.gov.pl/isap.nsf/download.xsp/WDU20230002029/O/D20232029.pdf", dataRecord.getLawTextUrl());

        String lawText = PdfUtils.extractText(getResourceAsBytes("/poland/D20232029.pdf"));
        dataRecord.setLawText(lawText);
        String billText = PdfUtils.extractText(getResourceAsBytes("/poland/3238.pdf"));
        dataRecord.setBillText(billText);

        Assertions.assertEquals("Druk Sejmowy nr 3238 - IX kadencja", dataRecord.getBillId());
        Assertions.assertEquals(187_042, dataRecord.getLawSize());
        Assertions.assertEquals(2_164_408, dataRecord.getBillSize());
        Assertions.assertEquals(27, dataRecord.getModifiedLawsCount());
        Assertions.assertTrue(dataRecord.getModifiedLaws().contains("Dz.U. 2015 poz. 1265"));
        Assertions.assertEquals(0, dataRecord.getAffectingLawsCount());
        Assertions.assertNull(dataRecord.getAffectingLawsFirstDate());

        Mockito.verify(pageSourceRepository, Mockito.times(27)).findByPageUrl(any());
        Mockito.verifyNoMoreInteractions(pageSourceRepository);
    }

    @Test
    void parseBillJson_DU_1997_1026() throws IOException {
        // Given
        PageSource testPage = getPageSourceObj("/poland/DU_1997_1026.json");
        testPage.setPageUrl("https://api.sejm.gov.pl/eli/acts/DU/1997/1026");

        PageSource modifiedLawsSource = getPageSourceObj("/poland/DU_2015_1265.json");
        String modifiedLawsSourceUrl = "https://api.sejm.gov.pl/eli/acts/DU/2015/1265";
        modifiedLawsSource.setPageUrl(modifiedLawsSourceUrl);

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pdfParser.tryPdfTextExtraction(any())).thenReturn(Optional.of("any"));
        Mockito.when(pageSourceRepository.findByPageUrl(any())).thenReturn(Optional.of(modifiedLawsSource));

        // When
        instance.parseBillApiData(testPage, dataRecord);

        //Then
        Assertions.assertEquals(Country.POLAND, dataRecord.getCountry());
        Assertions.assertEquals("Dz.U. 1997 nr 157 poz. 1026", dataRecord.getLawId());
        Assertions.assertEquals("Ustawa z dnia 11 grudnia 1997 r. o administrowaniu obrotem z zagranicą towarami i usługami oraz o obrocie specjalnym.", dataRecord.getBillTitle());
        Assertions.assertEquals(Boolean.TRUE, dataRecord.getOriginalLaw());
        Assertions.assertEquals("https://isap.sejm.gov.pl/isap.nsf/DocDetails.xsp?id=WDU19971571026", dataRecord.getBillPageUrl());
        Assertions.assertEquals(LegislativeDataRecord.BillStatus.PASS, dataRecord.getBillStatus());
        Assertions.assertEquals(LocalDate.of(1997, 12, 23), dataRecord.getDatePassing());
        Assertions.assertEquals(LocalDate.of(1998, 1, 1), dataRecord.getDateEnteringIntoForce());
        Assertions.assertEquals("https://orka.sejm.gov.pl/Rejestrd.nsf/wgdruku/15/$file/15.pdf", dataRecord.getBillTextUrl());
        Assertions.assertEquals("https://isap.sejm.gov.pl/isap.nsf/download.xsp/WDU19971571026/U/D19971026Lj.pdf", dataRecord.getLawTextUrl());

        String lawText = PdfUtils.extractText(getResourceAsBytes("/poland/D19971026Lj.pdf"));
        dataRecord.setLawText(lawText);

        Assertions.assertEquals("Druk Sejmowy nr 15 - III kadencja", dataRecord.getBillId());
        Assertions.assertEquals(9100, dataRecord.getLawSize());
        Assertions.assertEquals(4, dataRecord.getModifiedLawsCount());
        Assertions.assertEquals(7, dataRecord.getAffectingLawsCount());
        Assertions.assertEquals(LocalDate.of(2001, 1, 1), dataRecord.getAffectingLawsFirstDate());

        Mockito.verify(pageSourceRepository, Mockito.times(4)).findByPageUrl(any());
        Mockito.verifyNoMoreInteractions(pageSourceRepository);
    }

    @Test
    void parseBillJson_DU_2002_331() throws IOException {
        // Given
        PageSource testPage = getPageSourceObj("/poland/DU_2002_331.json");
        testPage.setPageUrl("https://api.sejm.gov.pl/eli/acts/DU/2002/331");

        PageSource modifiedLawsSource = getPageSourceObj("/poland/DU_2015_1265.json");
        String modifiedLawsSourceUrl = "https://api.sejm.gov.pl/eli/acts/DU/2015/1265";
        modifiedLawsSource.setPageUrl(modifiedLawsSourceUrl);

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pdfParser.tryPdfTextExtraction(any())).thenReturn(Optional.of("any"));
        Mockito.when(pageSourceRepository.findByPageUrl(any())).thenReturn(Optional.of(modifiedLawsSource));

        // When
        instance.parseBillApiData(testPage, dataRecord);

        //Then
        Assertions.assertEquals(Country.POLAND, dataRecord.getCountry());
        Assertions.assertEquals("Dz.U. 2002 nr 37 poz. 331", dataRecord.getLawId());
        Assertions.assertEquals("Ustawa z dnia 20 marca 2002 r. o ustanowieniu 2 maja Dniem Polonii i Polaków za Granicą.", dataRecord.getBillTitle());
        Assertions.assertEquals(Boolean.TRUE, dataRecord.getOriginalLaw());
        Assertions.assertEquals("https://isap.sejm.gov.pl/isap.nsf/DocDetails.xsp?id=WDU20020370331", dataRecord.getBillPageUrl());
        Assertions.assertEquals(LegislativeDataRecord.BillStatus.PASS, dataRecord.getBillStatus());
        Assertions.assertEquals(LocalDate.of(2002, 4, 12), dataRecord.getDatePassing());
        Assertions.assertEquals(LocalDate.of(2002, 4, 27), dataRecord.getDateEnteringIntoForce());
        Assertions.assertEquals("https://orka.sejm.gov.pl/Druki4ka.nsf/wgdruku/250", dataRecord.getBillTextUrl());
        Assertions.assertEquals("https://isap.sejm.gov.pl/isap.nsf/download.xsp/WDU20020370331/O/D20020331.pdf", dataRecord.getLawTextUrl());
        Assertions.assertEquals("Druk Sejmowy nr 250 - IV kadencja", dataRecord.getBillId());
        Assertions.assertEquals(0, dataRecord.getModifiedLawsCount());
        Assertions.assertEquals(0, dataRecord.getAffectingLawsCount());

        Mockito.verifyNoInteractions(pageSourceRepository);
    }
}
