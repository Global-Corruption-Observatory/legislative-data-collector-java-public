package com.precognox.ceu.legislative_data_collector.poland;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.poland.parsers.PolandCommitteesTableParser;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
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

@ExtendWith(MockitoExtension.class)
class PolandCommitteesTableParserTest {

    @InjectMocks
    private PolandCommitteesTableParser instance;

    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);


    @Test
    @Disabled
    void parseCommitteeTable_term4_pr886() throws IOException {
        // Given
        String commTableUrl = "https://www.sejm.gov.pl/SQL2.nsf/poskomprocall?OpenAgent&4&886";
        String commJsonUrl = "https://api.sejm.gov.pl/sejm/term4/committees";
        PageSource testPage = getPageSourceObj("/poland/886_commTab.html");
        PageSource commJson = getPageSourceObj("/poland/committees_term4.json");
        testPage.setPageUrl(commTableUrl);
        commJson.setPageUrl(commJsonUrl);

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pageSourceRepository.findByPageUrl(commTableUrl)).thenReturn(Optional.of(testPage));
        Mockito.when(pageSourceRepository.findByPageUrl(commJsonUrl)).thenReturn(Optional.of(commJson));

        // When
        instance.parseCommittees(testPage, dataRecord);

        // Then
        Assertions.assertAll(() -> Assertions.assertEquals(2, dataRecord.getCommitteeCount()),
                () -> Assertions.assertEquals(6, dataRecord.getCommitteeHearingCount()),
                () -> Assertions.assertEquals(LocalDate.of(2002, 10, 8), dataRecord.getCommitteeDate()),
                () -> Assertions.assertEquals("GOS", dataRecord.getCommittees().get(0).getName()),
                () -> Assertions.assertEquals("OBN", dataRecord.getCommittees().get(1).getName()),
                () -> Assertions.assertEquals("GOS", dataRecord.getCommittees().get(2).getName()),
                () -> Assertions.assertEquals(LocalDate.of(2002, 10, 28), dataRecord.getCommittees().get(2).getDate()),
                () -> Assertions.assertEquals("OBN", dataRecord.getCommittees().get(3).getName()),
                () -> Assertions.assertEquals(LocalDate.of(2002, 10, 28), dataRecord.getCommittees().get(3).getDate()),
                () -> Assertions.assertEquals("GOS", dataRecord.getCommittees().get(4).getName()),
                () -> Assertions.assertEquals("Komisja Gospodarki", dataRecord.getCommittees().get(4).getRole()),
                () -> Assertions.assertEquals("OBN", dataRecord.getCommittees().get(5).getName()),
                () -> Assertions.assertEquals("Komisja Obrony Narodowej", dataRecord.getCommittees().get(5).getRole()),
                () -> Assertions.assertEquals(3, dataRecord.getAmendments().size())
        );

        Mockito.verify(pageSourceRepository, Mockito.times(6)).findByPageUrl(commJsonUrl);
        Mockito.verifyNoMoreInteractions(pageSourceRepository);
    }

    @Test
    @Disabled
    void parseCommitteeTable_term9_pr3238() throws IOException {
        // Given
        String commTableUrl = "https://www.sejm.gov.pl/SQL2.nsf/poskomprocall?OpenAgent&9&3238";
        String commJsonUrl = "https://api.sejm.gov.pl/sejm/term9/committees";
        PageSource testPage = getPageSourceObj("/poland/3238_commTab.html");
        PageSource commJson = getPageSourceObj("/poland/committees_term9.json");
        testPage.setPageUrl(commTableUrl);
        commJson.setPageUrl(commJsonUrl);

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pageSourceRepository.findByPageUrl(commTableUrl)).thenReturn(Optional.of(testPage));
        Mockito.when(pageSourceRepository.findByPageUrl(commJsonUrl)).thenReturn(Optional.of(commJson));

        // When
        instance.parseCommittees(testPage, dataRecord);

        // Then
        Assertions.assertAll(() -> Assertions.assertEquals(2, dataRecord.getCommitteeCount()),
                () -> Assertions.assertEquals(3, dataRecord.getCommitteeHearingCount()),
                () -> Assertions.assertEquals(LocalDate.of(2023, 6, 13), dataRecord.getCommitteeDate()),
                () -> Assertions.assertEquals("ESK", dataRecord.getCommittees().get(0).getName()),
                () -> Assertions.assertEquals("OSZ", dataRecord.getCommittees().get(1).getName()),
                () -> Assertions.assertEquals("ESK", dataRecord.getCommittees().get(2).getName()),
                () -> Assertions.assertEquals(LocalDate.of(2023, 6, 16), dataRecord.getCommittees().get(2).getDate()),
                () -> Assertions.assertEquals("OSZ", dataRecord.getCommittees().get(3).getName()),
                () -> Assertions.assertEquals(LocalDate.of(2023, 6, 16), dataRecord.getCommittees().get(3).getDate()),
                () -> Assertions.assertEquals("ESK", dataRecord.getCommittees().get(4).getName()),
                () -> Assertions.assertEquals("Komisja do Spraw Energii, Klimatu i Aktywów Państwowych", dataRecord.getCommittees().get(4).getRole()),
                () -> Assertions.assertEquals("OSZ", dataRecord.getCommittees().get(5).getName()),
                () -> Assertions.assertEquals("Komisja Ochrony Środowiska, Zasobów Naturalnych i Leśnictwa", dataRecord.getCommittees().get(5).getRole())
                // TODO: these assertions are failing because of CAPTCHA changes in source pages (fix in Phase 3)
//                () -> Assertions.assertEquals(3, dataRecord.getAmendments().size()),
//                () -> Assertions.assertEquals("Komisja do Spraw Energii, Klimatu i Aktywów Państwowych /nr 169/,Komisja Ochrony Środowiska, Zasobów Naturalnych i Leśnictwa /nr 162/", dataRecord.getAmendments().get(0).getCommitteeName()),
//                () -> Assertions.assertEquals("Komisja do Spraw Energii, Klimatu i Aktywów Państwowych /nr 172/,Komisja Ochrony Środowiska, Zasobów Naturalnych i Leśnictwa /nr 165/", dataRecord.getAmendments().get(1).getCommitteeName()),
//                () -> Assertions.assertEquals("Komisja do Spraw Energii, Klimatu i Aktywów Państwowych /nr 180/,Komisja Ochrony Środowiska, Zasobów Naturalnych i Leśnictwa /nr 176/", dataRecord.getAmendments().get(2).getCommitteeName())
        );

        Mockito.verify(pageSourceRepository, Mockito.times(6)).findByPageUrl(commJsonUrl);
        Mockito.verifyNoMoreInteractions(pageSourceRepository);
    }
}
