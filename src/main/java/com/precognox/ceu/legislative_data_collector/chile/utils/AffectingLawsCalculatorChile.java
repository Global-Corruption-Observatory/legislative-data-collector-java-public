package com.precognox.ceu.legislative_data_collector.chile.utils;

import com.jayway.jsonpath.DocumentContext;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.chile.AffectingLawDetailed;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.JsonPathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.precognox.ceu.legislative_data_collector.chile.recordbuilders.LawRecordBuilder.LAW_ID_FORMATTER;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.AFFECTING_LAWS_DETAILED_URL;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.MODIFIED_LAWS_URL;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.JSON_TYPE_AFFECTING_LAWS_DETAILED;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.JSON_TYPE_MODIFIED_LAWS;

@Slf4j
@Service
public class AffectingLawsCalculatorChile {

    public final static Integer ITEMS_PER_PAGE_DEFAULT = 5000;
    private final static Pattern LAW_ID_NUM_REGEX = Pattern.compile("^Ley\\D(\\d+)");
    private final static Pattern DATE_STRING_REGEX = Pattern.compile("(\\d+)-(\\w+)-(\\d+)");
    private final static Map<String, Integer> MONTH_MAP = new HashMap<>();

    static {
        MONTH_MAP.put("ENE", 1);
        MONTH_MAP.put("FEB", 2);
        MONTH_MAP.put("MAR", 3);
        MONTH_MAP.put("ABR", 4);
        MONTH_MAP.put("MAY", 5);
        MONTH_MAP.put("JUN", 6);
        MONTH_MAP.put("JUL", 7);
        MONTH_MAP.put("AGO", 8);
        MONTH_MAP.put("SEP", 9);
        MONTH_MAP.put("OCT", 10);
        MONTH_MAP.put("NOV", 11);
        MONTH_MAP.put("DIC", 12);
    }

    private PrimaryKeyGeneratingRepository billRepository;
    private PageSourceRepository pageRepository;
    private EntityManager entityManager;

    @Autowired
    public AffectingLawsCalculatorChile(
            PrimaryKeyGeneratingRepository billRepository,
            PageSourceRepository pageRepository,
            EntityManager entityManager) {
        this.billRepository = billRepository;
        this.pageRepository = pageRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Page<LegislativeDataRecord> parseAffectingLaws(Pageable paging) {
        Page<LegislativeDataRecord> records = billRepository.findByCountryAndLawIdIsNotNull(Country.CHILE, paging);

        records.getContent()
                .stream()
                .filter(record -> Objects.nonNull(record.getChileCountrySpecificVariables().getNormId()))
                .forEach(record -> {
                    String affectingLawsUrl = String.format(
                            AFFECTING_LAWS_DETAILED_URL,
                            ITEMS_PER_PAGE_DEFAULT,
                            record.getChileCountrySpecificVariables().getNormId());

                    Optional<PageSource> affectingLawsPage =
                            pageRepository.findByPageTypeAndPageUrl(JSON_TYPE_AFFECTING_LAWS_DETAILED, affectingLawsUrl);

                    if (affectingLawsPage.isPresent() && isAffectingLawsPage(affectingLawsPage.get().getRawSource())) {
                        log.debug("START Processing affecting laws for {}", record.getRecordId());
                        List<DocumentContext> affectingJsons = getAffectingJsons(affectingLawsPage.get());

                        List<AffectingLawDetailed> affectingLaws = affectingJsons.stream()
                                .map(affectingLawJson -> createAffectingLawFromJson(affectingLawJson, record))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .collect(Collectors.toList());

                        record.setAffectingLawsDetailed(affectingLaws);

                        int affectingLawsCount = (int) affectingLaws.stream()
                                .map(AffectingLawDetailed::getAffectingLawId)
                                .distinct()
                                .count();

                        record.setAffectingLawsCount(affectingLawsCount);

                        affectingLaws.stream()
                                .map(AffectingLawDetailed::getAffectingDate)
                                .filter(Objects::nonNull)
                                .min(LocalDate::compareTo)
                                .ifPresent(record::setAffectingLawsFirstDate);

                        log.debug("END Affecting laws processed for {}", record.getRecordId());
                    } else {
                        record.setAffectingLawsCount(0);
                    }

                    String modifiedLawsUrl = String.format(
                            MODIFIED_LAWS_URL,
                            ITEMS_PER_PAGE_DEFAULT,
                            record.getChileCountrySpecificVariables().getNormId());

                    Optional<PageSource> modifiedLawsPage =
                            pageRepository.findByPageTypeAndPageUrl(JSON_TYPE_MODIFIED_LAWS, modifiedLawsUrl);

                    if (modifiedLawsPage.isPresent()) {
                        log.debug("START Processing modified laws for {}", record.getRecordId());

                        List<DocumentContext> modJsons = getAffectingJsons(modifiedLawsPage.get());
                        long count = modJsons.stream()
                                .map(j -> JsonPathUtils.findText(j, "$.NORMA"))
                                .filter(text -> text.matches(LAW_ID_NUM_REGEX.pattern()))
                                .distinct()
                                .count();

                        record.setModifiedLawsCount((int) count);
                    } else {
                        record.setModifiedLawsCount(0);
                    }
                });

        return records;
    }

    //If no there is no affecting law the response defaults to the modified laws list. There this text is 'Parte Modificatoria'. This check is to make sure that the json actually contains the right information
    private boolean isAffectingLawsPage(String json) {
        String typeText = JsonPathUtils.getNestedObject(json, "$.ultimaColumna");
        return typeText.equals("Parte Modificada");
    }

    //splits the JSONArray into individual JSONObjects.
    private List<DocumentContext> getAffectingJsons(PageSource affectingSource) {
        DocumentContext json = JsonPathUtils.parse(affectingSource.getRawSource());
        int affectingCount = JsonPathUtils.getNestedObject(json, "$.aciertos.length()");

        return IntStream.range(0, affectingCount)
                .mapToObj(i -> JsonPathUtils.findByJsonPath(json, String.format("$.aciertos[%d]", i)))
                .map(JsonPathUtils::parse)
                .toList();
    }

    private Optional<AffectingLawDetailed> createAffectingLawFromJson(
            DocumentContext json, LegislativeDataRecord modifiedRecord) {
        String affectingId = JsonPathUtils.findText(json, "$.NORMA").trim();
        Matcher lawIdMatcher = LAW_ID_NUM_REGEX.matcher(affectingId);

        if (lawIdMatcher.find()) {
            AffectingLawDetailed affectingLawDetailed = new AffectingLawDetailed();
            affectingLawDetailed.setDataRecord(modifiedRecord);
            String affectingLawId = String.format(LAW_ID_FORMATTER, lawIdMatcher.group(1));
            affectingLawDetailed.setAffectingLawId(affectingLawId);
            Optional<LegislativeDataRecord> affectingRecord = billRepository.findByLawId(affectingLawId);
            if (affectingRecord.isPresent()) {
                affectingLawDetailed.setAffectingRecord(affectingRecord.get());
            } else {
                log.error(String.format("Law was modified by %s, which not found in the database", affectingLawId));
            }
            String affectingPart = JsonPathUtils.findText(json, "$.PARTE").trim();
            if (!affectingPart.isBlank()) {
                affectingLawDetailed.setAffectingArticle(affectingPart);
            }
            String modifiedPart = JsonPathUtils.findText(json, "$.parteActual").trim();
            if (!modifiedPart.isBlank()) {
                affectingLawDetailed.setModifiedArticle(modifiedPart);
            }
            String affectingDateString = JsonPathUtils.findText(json, "$.FECHA_PUBLICACION");
            createDateFromString(affectingDateString).ifPresent(affectingLawDetailed::setAffectingDate);
            return Optional.of(affectingLawDetailed);
        }

        return Optional.empty();
    }

    private Optional<LocalDate> createDateFromString(String dateString) {
        Matcher dateMatcher = DATE_STRING_REGEX.matcher(dateString);
        if (dateMatcher.find()) {
            int day = Integer.parseInt(dateMatcher.group(1).trim());
            int month = MONTH_MAP.get(dateMatcher.group(2).trim());
            int year = Integer.parseInt(dateMatcher.group(3).trim());
            return Optional.of(LocalDate.of(year, month, day));
        } else {
            return Optional.empty();
        }
    }

//    @Transactional
//    public void calculateModifiedLaws() {
//        String query = "SELECT affecting_record_id, count(DISTINCT record_id) FROM affecting_laws_detailed WHERE affecting_record_id NOTNULL GROUP BY affecting_record_id ORDER BY affecting_record_id";
//
//        List<Object[]> results = entityManager.createNativeQuery(query).getResultList();
//
//        results.forEach(result -> {
//            String recordId = (String) result[0];
//            BigInteger count = (BigInteger) result[1];
//
//            billRepository.findById(recordId).ifPresent(record -> {
//                record.setModifiedLawsCount(count.intValue());
//                billRepository.save(record);
//            });
//        });
//    }

}