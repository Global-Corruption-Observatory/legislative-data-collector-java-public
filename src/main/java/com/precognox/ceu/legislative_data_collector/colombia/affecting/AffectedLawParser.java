package com.precognox.ceu.legislative_data_collector.colombia.affecting;

import com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.LawHandler;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.ReadDatabaseService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.LawHandler.createUniformLawId;

/**
 * This class handles the processing of the modification information about the laws
 */
@Slf4j
@Service
public class AffectedLawParser {

    private static final Pattern AFFECTING_LAW_REGEX =
            Pattern.compile("^- Modificad[ao] por la(\\D+\\d+\\s*de\\s*\\d+)\\s*,");

    private final AffectingLawCalculatorDummy affectingLawCalculator;
    private final PrimaryKeyGeneratingRepository dataRepository;
    private final LegislativeDataRepository lazyRepository;
    private final ReadDatabaseService readService;

    @Autowired
    public AffectedLawParser(AffectingLawCalculatorDummy affectingLawCalculator,
                             PrimaryKeyGeneratingRepository dataRepository,
                             LegislativeDataRepository lazyRepository,
                             ReadDatabaseService readService) {
        this.affectingLawCalculator = affectingLawCalculator;
        this.dataRepository = dataRepository;
        this.lazyRepository = lazyRepository;
        this.readService = readService;
    }

    @Transactional
    public void handleAffectedLaws() {
        Map<Long, List<String>> affectedLawsMap = new HashMap<>();
        List<LegislativeDataRecord> dataRecords = dataRepository.findLawsByCountry(Country.COLOMBIA);
        dataRecords.forEach(dataRecord -> {
                    try {
                        List<Long> affectingRecordIds = getModifiedByRecordId(dataRecord);
                        affectingRecordIds.forEach(affectingRecordId ->
                                addToMapList(affectedLawsMap, affectingRecordId, dataRecord.getLawId()));
                    } catch (DataCollectionException ex) {
                        dataRecord.getErrors().add(ex.getMessage());
                    } finally {
                        dataRecord.setAffectingLawsCount(0);
                        lazyRepository.save(dataRecord);
                    }
                });
        affectedLawsMap.forEach((affectingRecordId, affectedLawIds) ->
                dataRepository.findById(affectingRecordId)
                        .ifPresent(dataRecord -> {
                            dataRecord.setModifiedLaws(new HashSet<>(affectedLawIds));
                            lazyRepository.save(dataRecord);
                        })
        );
    }

    private void addToMapList(Map<Long, List<String>> map, Long key, String value) {
        if (!map.containsKey(key)) {
            map.put(key, new ArrayList<>());
        }
        map.get(key).add(value);
    }

    private List<Long> getModifiedByRecordId(LegislativeDataRecord record) throws DataCollectionException {
        LawHandler lawPageHandler =
                new LawHandler(record.getLawId(), record.getColombiaCountrySpecificVariables().getBillTypeColombia(), readService);
        Document page = lawPageHandler.getSenatePage();
        Optional<Element> modifications = Optional.ofNullable(page.selectFirst("#Table1"));
        return modifications.map(element ->
                element.select("p").stream()
                        .map(Element::text)
                        .map(AFFECTING_LAW_REGEX::matcher)
                        .filter(Matcher::find)
                        .map(matcher -> matcher.group(1).trim())
                        .map(originalAffectingLawId -> {
                            try {
                                return createUniformLawId(originalAffectingLawId);
                            } catch (DataCollectionException e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .map(affectingLawId -> {
                            Optional<LegislativeDataRecord> affectingLaw =
                                    dataRepository.findByLawIdAndCountry(affectingLawId, Country.COLOMBIA);
                            if (affectingLaw.isEmpty()) {
                                log.error(String.format("Law was modified by %s, which is not found in the database", affectingLawId));
                            }
                            return affectingLaw;
                        })
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(LegislativeDataRecord::getId)
                        .toList())
                .orElseGet(ArrayList::new);
    }

    @Transactional
    public void setAffectedLawsCount() {
        dataRepository.findPassedBillsByCountry(Country.COLOMBIA).forEach(dataRecord -> {
            dataRecord.setModifiedLawsCount(dataRecord.getModifiedLaws().size());
            lazyRepository.save(dataRecord);
        });
    }

    @Transactional
    public void processAffectingLaws() {
        affectingLawCalculator.processAffectingLaws();
    }
}
