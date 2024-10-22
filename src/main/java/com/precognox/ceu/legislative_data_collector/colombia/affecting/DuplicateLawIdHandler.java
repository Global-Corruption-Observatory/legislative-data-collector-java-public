package com.precognox.ceu.legislative_data_collector.colombia.affecting;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class DuplicateLawIdHandler {
    private LegislativeDataRepository dataRepository;
    private PageSourceRepository pageRepository;

    @Autowired
    public DuplicateLawIdHandler(LegislativeDataRepository lazyRepository, PageSourceRepository pageRepository) {
        this.dataRepository = lazyRepository;
        this.pageRepository = pageRepository;
    }


    private Stream<String> getDuplicatedLawIds(Country country) {
        return dataRepository.findLawIds(country)
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey);
    }

    @Transactional
    public void markDuplicatedLawIds(Country country) {
        getDuplicatedLawIds(country).forEach(lawId -> {
            List<LegislativeDataRecord> duplicates = dataRepository.findAllByLawIdAndCountryOrdered(lawId, country);
            IntStream.range(1, duplicates.size())//Starts from one as the first one (the latest) will be considered the original (as that is when it passed) and kept the same
                    .forEach(i -> {
                        LegislativeDataRecord record = duplicates.get(i);
                        record.setLawId(String.format("duplicate(%d) of [%s]", i, lawId));
                    });
        });
    }

    @Transactional
    public void deleteDuplicatedLawIds(Country country) {
        getDuplicatedLawIds(country).forEach(lawId -> {
            List<LegislativeDataRecord> duplicates = dataRepository.findAllByLawIdAndCountryOrdered(lawId, country);
            Comparator<LegislativeDataRecord> colombiaDuplicateLawIdRecordComparator =
                    Comparator.comparing(this::getStageCountWithDates)
                            .thenComparing(this::hasBillText)
                            .thenComparing(this::hasVotes)
                            .thenComparing(LegislativeDataRecord::getCommitteeCount)
                            .thenComparing(LegislativeDataRecord::getAmendmentCount)
                            .reversed();

            duplicates.stream()
                    .sorted(colombiaDuplicateLawIdRecordComparator)
                    .skip(1)
                    .map(LegislativeDataRecord::getId)
                    .forEach(id -> dataRepository.deleteById(id));
        });
    }

    /**
     * It is requested that all laws that are passed, have all 6 necessary legislative stages (Colombian case)
     * So when one is missing from the bill page it is added without a date. This function counts how many stages
     * are really on the bill page
     */
    private int getStageCountWithDates(LegislativeDataRecord record) {
        return (int) record.getStages()
                .stream()
                .filter(stage -> stage.getStageNumber() != 0)
                .filter(stage -> Objects.nonNull(stage.getDate()))
                .count();
    }

    private int hasBillText(LegislativeDataRecord record) {
        if (record.getBillText() != null) {
            return 1;
        }
        return 0;
    }

    private int hasVotes(LegislativeDataRecord record) {
        if (record.getFinalVoteFor() != null) {
            return 1;
        }
        return 0;
    }
}
