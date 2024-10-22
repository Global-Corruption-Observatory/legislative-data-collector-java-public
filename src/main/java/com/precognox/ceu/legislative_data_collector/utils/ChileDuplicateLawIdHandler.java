package com.precognox.ceu.legislative_data_collector.utils;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class ChileDuplicateLawIdHandler {
    private LegislativeDataRepository lazyRepository;

    @Autowired
    public ChileDuplicateLawIdHandler(LegislativeDataRepository lazyRepository) {
        this.lazyRepository = lazyRepository;
    }

    public void handleDuplicateLawIds(Country country) {
        lazyRepository.findLawIds(country)
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .flatMap(lawId -> markDuplicatedLawIds(lawId, country))
                .forEach(lazyRepository::save);
    }

    private Stream<LegislativeDataRecord> markDuplicatedLawIds(String lawId, Country country) {
        List<LegislativeDataRecord> duplicates =
                lazyRepository.findAllByLawIdAndCountryOrdered(lawId, country);

        IntStream.range(1, duplicates.size())//Starts from one as the first one (the latest) will be considered the original (as that is when it passed) and kept the same
                .forEach(i -> {
                    LegislativeDataRecord record = duplicates.get(i);
                    record.setLawId(String.format("duplicate(%d) of [%s]", i, lawId));
                });
        return duplicates.stream();
    }
}
