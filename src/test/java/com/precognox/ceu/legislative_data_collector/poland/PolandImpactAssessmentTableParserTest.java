package com.precognox.ceu.legislative_data_collector.poland;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.poland.parsers.PolandImpactAssessmentTableParser;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.DocUtils;
import org.apache.tika.exception.TikaException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;

@ExtendWith(MockitoExtension.class)
class PolandImpactAssessmentTableParserTest {

    @InjectMocks
    private PolandImpactAssessmentTableParser instance;

    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);
    private final IaTextCollector iaTextCollector = Mockito.mock(IaTextCollector.class);
    private final DocUtils docUtils = Mockito.mock(DocUtils.class);

    @Disabled
    @Test
    void parseCommitteeTable_term9_pr3238() throws TikaException, IOException, SAXException {
        // Given
        String iaTableUrl = "https://www.sejm.gov.pl/Sejm9.nsf/opinieBAS.xsp?nr=3238";
        String iaTextUrl = "https://orka.sejm.gov.pl/rexdomk9.nsf/0/793F728785906E26C12589A900405D8E/%24File/i859-23.docx";
        PageSource testPage = getPageSourceObj("/poland/3238_iaTable.html");
        PageSource iaTextSource = getPageSourceObj("/poland/3238_iaText.txt");
        testPage.setPageUrl(iaTableUrl);
        iaTextSource.setPageUrl(iaTextUrl);
        String iaTitle = "Ocena skutków prawnych regulacji zawartej w rządowym projekcie ustawy o zmianie ustawy – Prawo geologiczne " +
                "i górnicze oraz niektórych innych ustaw (druk sejmowy nr 3238)";

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pageSourceRepository.findByPageUrl(iaTableUrl)).thenReturn(Optional.of(testPage));
        Mockito.when(docUtils.downloadDocText(iaTextUrl)).thenReturn(Optional.ofNullable(iaTextSource.getRawSource()));

        // When
        instance.parseImpactAssessment(testPage, dataRecord);

        // Then
        Assertions.assertEquals(1, dataRecord.getImpactAssessments().size());
        Assertions.assertEquals(LocalDate.of(2023, 6, 15), dataRecord.getImpactAssessments().get(0).getDate());
        Assertions.assertEquals(iaTextUrl, dataRecord.getImpactAssessments().get(0).getOriginalUrl());
        Assertions.assertEquals(iaTitle, dataRecord.getImpactAssessments().get(0).getTitle());

        Mockito.verifyNoMoreInteractions(pageSourceRepository);
    }

    @Test
    void parseCommitteeTable_term4_pr28() throws TikaException, IOException, SAXException {
        // Given
        String iaTableUrl = "https://orka.sejm.gov.pl/rexdomk4.nsf/Opdodr?OpenPage&nr=28";
        PageSource testPage = getPageSourceObj("/poland/28_iaTable.html");
        testPage.setPageUrl(iaTableUrl);
        String iaTextBaseUrl = "https://orka.sejm.gov.pl";
        String iaOriginalUrl = "/rexdomk4.nsf/0/AE154455B8DE9A98C1256AFB0050597C/$file/I90-01.rtf";
        String iaTextUrl = iaTextBaseUrl + iaOriginalUrl;
        PageSource iaTextSource = getPageSourceObj("/poland/28_iaText.txt");
        iaTextSource.setPageUrl(iaTextUrl);
        String iaTitle = "Opinia dot. projektu ustawy o zmianie ustawy o podatku dochodowym od osób fizycznych";

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pageSourceRepository.findByPageUrl(iaTableUrl)).thenReturn(Optional.of(testPage));
        Mockito.when(docUtils.downloadDocText(iaTextUrl)).thenReturn(Optional.ofNullable(iaTextSource.getRawSource()));

        // When
        instance.parseImpactAssessment(testPage, dataRecord);

        // Then
        Assertions.assertEquals(1, dataRecord.getImpactAssessments().size());
        Assertions.assertEquals(LocalDate.of(2001, 11, 6), dataRecord.getImpactAssessments().get(0).getDate());
        Assertions.assertEquals(iaOriginalUrl, dataRecord.getImpactAssessments().get(0).getOriginalUrl());
        Assertions.assertEquals(iaTitle, dataRecord.getImpactAssessments().get(0).getTitle());

        Mockito.verifyNoMoreInteractions(pageSourceRepository);
    }
}
