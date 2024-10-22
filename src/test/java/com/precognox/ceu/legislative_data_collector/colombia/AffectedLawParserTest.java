package com.precognox.ceu.legislative_data_collector.colombia;

import com.precognox.ceu.legislative_data_collector.colombia.affecting.AffectedLawParser;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.colombia.ColombiaCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.ReadDatabaseService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.common.ResourceLoader.getPageSourceObj;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AffectedLawParserTest {
    private final PrimaryKeyGeneratingRepository dataRepository = mock(PrimaryKeyGeneratingRepository.class);
    private final LegislativeDataRepository lazyRepository = mock(LegislativeDataRepository.class);
    private final ReadDatabaseService readService = mock(ReadDatabaseService.class);

    @Test
    void handleAffectedLawsWithNoRecords() {
        // Given
        AffectedLawParser affectedLawParser = new AffectedLawParser(null, dataRepository, null, null);
        when(dataRepository.findLawsByCountry(Country.COLOMBIA)).thenReturn(Collections.emptyList());

        // When
        affectedLawParser.handleAffectedLaws();

        // Then
        verify(dataRepository, never()).findById(anyLong());
    }

    @Test
    void handleAffectedLawsWithRecordsHavingNoModifications() {
        // Given
        AffectedLawParser affectedLawParser = new AffectedLawParser(null, dataRepository,
                lazyRepository, null);
        LegislativeDataRecord dataRecord = new LegislativeDataRecord();
        dataRecord.setId(1L);
        dataRecord.setCountry(Country.COLOMBIA);
        dataRecord.setLawId("test");
        ColombiaCountrySpecificVariables specificVariables = new ColombiaCountrySpecificVariables();
        specificVariables.setBillTypeColombia("Proyecto de Ley");
        dataRecord.setColombiaCountrySpecificVariables(specificVariables);
        when(dataRepository.findLawsByCountry(Country.COLOMBIA)).thenReturn(List.of(dataRecord));
        when(dataRepository.findByLawIdAndCountry(anyString(), any())).thenReturn(Optional.empty());

        // When
        affectedLawParser.handleAffectedLaws();

        // Then
        Assertions.assertAll(() -> {
            Assertions.assertTrue(dataRecord.getModifiedLaws().isEmpty());
            Assertions.assertEquals(0, dataRecord.getAffectingLawsCount());
        });

        verify(lazyRepository, times(1)).save(dataRecord);
        verify(lazyRepository, times(0)).findByLawId("test");
    }

    @Test
    void handleAffectedLawsWithRecordsHavingModifications() throws IOException {
        // Given
        AffectedLawParser affectedLawParser = new AffectedLawParser(null, dataRepository,
                lazyRepository, readService);
        ColombiaCountrySpecificVariables specificVariables = new ColombiaCountrySpecificVariables();
        specificVariables.setBillTypeColombia("Proyecto de Ley");
        LegislativeDataRecord dataRecord = new LegislativeDataRecord();
        dataRecord.setId(1L);
        dataRecord.setCountry(Country.COLOMBIA);
        dataRecord.setLawId("2021/2169");
        dataRecord.setColombiaCountrySpecificVariables(specificVariables);
        dataRecord.setBillStatus(LegislativeDataRecord.BillStatus.PASS);
        LegislativeDataRecord affectingRecord = new LegislativeDataRecord();
        affectingRecord.setId(2L);
        String affectingLawId = "2023/2294";
        affectingRecord.setLawId(affectingLawId);
        affectingRecord.setCountry(Country.COLOMBIA);
        affectingRecord.setColombiaCountrySpecificVariables(specificVariables);
        when(dataRepository.findLawsByCountry(Country.COLOMBIA)).thenReturn(List.of(dataRecord));
        when(dataRepository.findByLawIdAndCountry(affectingLawId, Country.COLOMBIA)).thenReturn(Optional.of(affectingRecord));
        PageSource lawTextSource = getPageSourceObj("/colombia/2021_2169_lawtext_source.html");
        when(readService.findByPageTypeAndPageUrl(anyString(), anyString())).thenReturn(Optional.of(lawTextSource));
        when(dataRepository.findById(2L)).thenReturn(Optional.of(affectingRecord));

        // When
        affectedLawParser.handleAffectedLaws();

        // Then
        Assertions.assertAll(() -> {
            Assertions.assertEquals(1, affectingRecord.getModifiedLaws().size());
            Assertions.assertTrue(affectingRecord.getModifiedLaws().contains("2021/2169"));
        });

        verify(lazyRepository, times(1)).save(dataRecord);
        verify(readService, times(1)).findByPageTypeAndPageUrl(any(), anyString());
        verify(dataRepository, times(1)).findByLawIdAndCountry(any(), any());
    }
}
