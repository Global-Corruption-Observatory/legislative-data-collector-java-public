package com.precognox.ceu.legislative_data_collector.poland;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.poland.parsers.PolandProcessApiDataParser;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
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

@ExtendWith(MockitoExtension.class)
class PolandProcessApiDataParserTest {

    @InjectMocks
    private PolandProcessApiDataParser instance;

    private final PageSourceRepository pageSourceRepository = Mockito.mock(PageSourceRepository.class);

    @Test
    void parseProcessJson_3238() throws IOException {
        // Given
        String processApiPageUrl = "https://api.sejm.gov.pl/sejm/term9/processes/3238";
        PageSource testPage = getPageSourceObj("/poland/PrNr_3238.json");
        testPage.setPageUrl(processApiPageUrl);

        LegislativeDataRecord dataRecord = new LegislativeDataRecord();

        Mockito.when(pageSourceRepository.findByPageUrl(processApiPageUrl)).thenReturn(Optional.of(testPage));

        // When
        instance.parseProcessApiData(processApiPageUrl, dataRecord);

        //Then
        Assertions.assertEquals(LocalDate.of(2023, 5, 5), dataRecord.getDateIntroduction());
        Assertions.assertEquals(OriginType.GOVERNMENT, dataRecord.getOriginType());

        Mockito.verify(pageSourceRepository, Mockito.times(1)).findByPageUrl(processApiPageUrl);
        Mockito.verifyNoMoreInteractions(pageSourceRepository);
    }
}
