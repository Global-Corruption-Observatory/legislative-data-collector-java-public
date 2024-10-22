package com.precognox.ceu.legislative_data_collector.chile.recordbuilders;

import com.jayway.jsonpath.DocumentContext;
import com.precognox.ceu.legislative_data_collector.chile.utils.OriginalLawCalculator;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.chile.ChileCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.utils.JsonPathUtils;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.LAW_ORIGINATOR_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.LAW_TERMINATION_DATE_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileApiUrls.ORIGINATOR_PAGE_API;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.JSON_TYPE_LAW_TERMINATION;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.JSON_TYPE_ORIGINATOR_LIST;
import static com.precognox.ceu.legislative_data_collector.chile.utils.ChileanSourceTypes.PAGE_TYPE_LAW_ORIGINATOR;

@Slf4j
public class LawRecordBuilder extends RecordBuilder {

    private final OriginalLawCalculator originalLawCalculator = new OriginalLawCalculator();

    public final static String LAW_ID_FORMATTER = "Ley-%s";
    private final static String LAW_PAGE_URL_FORMATTER = "https://www.leychile.cl/Navegar?idNorma=%s";
    private final static String MULTIPLE_LAW_ID_DELIMITER = "&&";
    private final static LocalDate ORIGIN_TYPE_START_DATE = LocalDate.of(1997, 12, 31);
    private final static Pattern NORM_ID_URL_EXTRACT_REGEX = Pattern.compile("idNorma=(\\d+)");
    private final static Pattern REQUIRED_LAW_REGEX = Pattern.compile("^Ley\\D*(\\d+)$");
    private final static Pattern TERMINATION_DATE_REGEX = Pattern.compile("(\\d{2})-(\\d{2})-(\\d{4})");

    @Override
    protected void buildRecord(PageSource pageSource, Optional<LegislativeDataRecord> recordOpt) {
        record = recordOpt.orElseGet(LegislativeDataRecord::new);
        record.setBillPageUrl(pageSource.getPageUrl());
        record.setCountry(Country.CHILE);
        record.setBillStatus(LegislativeDataRecord.BillStatus.PASS);
        setUpCollections();

        DocumentContext json = JsonPathUtils.parseJson(pageSource.getRawSource());

        trySettingTextFromJson(getLawIdSetterMethod(), json, "$.metadatos.tipos_numeros[?(@.tipo==\"1\")].numero", "law id", MULTIPLE_LAW_ID_DELIMITER);
        trySettingDateFromJson(record::setDatePassing, LocalDate::parse, json, "$.metadatos.fecha_promulgacion", "date of passing");
        trySettingTextFromJson(record::setLawText, json, "$.html..t", "law text", "\n");

        setCountrySpecificVariablesFromJSON(json);
        originalLawCalculator.fillOriginalLaw(record);

        String normId = record.getChileCountrySpecificVariables().getNormId();

        if (Objects.nonNull(normId)) {
            record.setLawTextUrl(String.format(LAW_PAGE_URL_FORMATTER, normId));

            try {
                List<Originator> originators = getOriginatorsForLaw();
                record.setOriginators(originators);
            } catch (DataCollectionException ex) {
                log.error(ex.getMessage());
                record.setOriginators(new ArrayList<>());
            }

            //trySettingModifiedLaws();
        } else {
            log.error("As the alternative law id was not found in the json, the law text url couldn't be created, and the originators and modified laws information couldn't be parsed!");
        }

        record.setAffectingLawsCount(0); //Default value for laws so it won't be NULL

        OriginType originType = (record.getOriginators().size() == 0)
                ? OriginType.GOVERNMENT
                : OriginType.INDIVIDUAL_MP;

        //Originators are not recorded before 1998, so we don't set the origin_type for earlier bills to avoid a wrong value
        if (isAfter1998(record)
                && getCountryVariables().getDateEnteringForceLastVersion().isAfter(ORIGIN_TYPE_START_DATE)) {
            //Date of entering force as that is set for every law []
            record.setOriginType(originType);
        }
    }

    private boolean isAfter1998(LegislativeDataRecord record) {
        return record.getDatePassing() != null && record.getDatePassing().getYear() >= 1998;
    }

    @Override
    protected Optional<LegislativeDataRecord> getRecordForSource(PageSource source) {
        Matcher matcher = NORM_ID_URL_EXTRACT_REGEX.matcher(source.getPageUrl());

        if (matcher.find()) {
            String url = String.format(LAW_PAGE_URL_FORMATTER, matcher.group(1));
            return readService.findByLawTextUrl(url);
        }

        return Optional.empty();
    }

    private void setCountrySpecificVariablesFromJSON(DocumentContext json) {
        ChileCountrySpecificVariables countryVariables = getCountryVariables();

        trySettingTextFromJson(countryVariables::setNormId, json, "$.metadatos.id_norma", "alternative law id");
        trySettingDateFromJson(countryVariables::setDatePublication, LocalDate::parse, json, "$.metadatos.fecha_publicacion", "date of publication");
        trySettingTextFromJson(countryVariables::setAffectedOrganisms, json, "$.metadatos.organismos", "affected organisms");
        trySettingTextFromJson(countryVariables::setLawTitle, json, "$.metadatos.titulo_norma", "law title");
        trySettingTextFromJson(countryVariables::setBillMainTopic, json, "$.metadatos.materias", "main topic");
        trySettingTextFromJson(countryVariables::setNumberEnactedLaw, json, "$.metadatos.numero_fuente", "number in official gazette");
        trySettingTextFromJson(countryVariables::setBillSummary, json, "$.metadatos.resumenes", "summary");
        trySettingDateFromJson(countryVariables::setDateFinalIntoForce, LocalDate::parse, json, "$.metadatos.vigencia.fin_vigencia", "date final in force");
        trySettingDateFromJson(countryVariables::setDateEnteringForceLastVersion, LocalDate::parse, json, "$.metadatos.vigencia.inicio_vigencia", "date of entering force (last version)");
        Optional<DocumentContext> terminationDateJson = getTerminationDateJson(countryVariables.getNormId());
        terminationDateJson.ifPresent(termDateJson -> trySettingDateFromJson(countryVariables::setTerminationDate, getTerminationDateExtractMethod(), termDateJson, "$[1][0][3]", "termination date"));
    }

    private void trySettingTextFromJson(
            Consumer<String> setMethod, DocumentContext json, String jsonPath, String searchedVariable) {
        trySettingTextFromJson(setMethod, json, jsonPath, searchedVariable, "; ");
    }

    private void trySettingTextFromJson(
            Consumer<String> setMethod,
            DocumentContext json,
            String jsonPath,
            String searchedVariable,
            String delimiter) {
        try {
            String text = getTextFromJson(json, jsonPath, searchedVariable, delimiter);
            setMethod.accept(text);
        } catch (DataCollectionException ex) {
            log.error(ex.getMessage());
        }
    }

    private String getTextFromJson(DocumentContext json, String path) throws DataCollectionException {
        return getTextFromJson(json, path, "Searched tag");
    }

    private String getTextFromJson(DocumentContext json, String path, String searchedVariable)
            throws DataCollectionException {
        return getTextFromJson(json, path, searchedVariable, "; ");
    }

    private String getTextFromJson(
            DocumentContext json, String path, String searchedVariable, String delimiter) throws DataCollectionException {
        List<String> listResponse = JsonPathUtils.findListByJsonPath(json, path);

        if (listResponse.size() == 0) {
            throw new DataCollectionException(getNotFoundInJsonErrorMessage(searchedVariable, path));
        }

        return TextUtils.cleanHTMLEntitiesFromText(StringUtils.join(listResponse, delimiter));
    }

    private String getNotFoundInJsonErrorMessage(String searchedVariable, String path) {
        return String.format("%s not found in JSON with given path: %s", searchedVariable, path);
    }

    private Consumer<String> getLawIdSetterMethod() {
        return (String lawIdNumberFromJson) -> {
            String lawIdNumber = lawIdNumberFromJson.split(MULTIPLE_LAW_ID_DELIMITER)[0].trim();
            String lawId = String.format(LAW_ID_FORMATTER, lawIdNumber);
            record.setLawId(lawId);
        };
    }

    private void trySettingDateFromJson(
            Consumer<LocalDate> setMethod,
            Function<String, LocalDate> dateExtractFunction,
            DocumentContext json,
            String jsonPath,
            String searchedVariable) {
        try {
            String dateString = getTextFromJson(json, jsonPath, searchedVariable);
            LocalDate date = dateExtractFunction.apply(dateString);
            setMethod.accept(date);
        } catch (DateTimeParseException ex) {
            log.error(String.format("Wrong date format for the %s", searchedVariable));
        } catch (RuntimeException | DataCollectionException ex) { //Runtime exception from termination date
            log.error(ex.getMessage());
        }
    }

    private Optional<DocumentContext> getTerminationDateJson(String normId) {
        String url = String.format(LAW_TERMINATION_DATE_API, normId);
        return readService.findByPageTypeAndPageUrl(JSON_TYPE_LAW_TERMINATION, url)
                .map(PageSource::getRawSource)
                .map(JsonPathUtils::parse);
    }

    private Function<String, LocalDate> getTerminationDateExtractMethod() {
        return (String text) -> {
            Matcher dateMatcher = TERMINATION_DATE_REGEX.matcher(text);
            if (dateMatcher.find()) {
                int day = Integer.parseInt(dateMatcher.group(1));
                int month = Integer.parseInt(dateMatcher.group(2));
                int year = Integer.parseInt(dateMatcher.group(3));
                return LocalDate.of(year, month, day);
            }
            throw new RuntimeException("Termination date couldn't be extracted");
        };
    }

    private List<Originator> getOriginatorsForLaw() throws DataCollectionException {
        String normId = record.getChileCountrySpecificVariables().getNormId();
        String url = String.format(LAW_ORIGINATOR_API, normId);
        Optional<PageSource> originatorsListSource = readService.findByPageTypeAndPageUrl(JSON_TYPE_ORIGINATOR_LIST, url);
        if (originatorsListSource.isPresent()) {
            return JsonPathUtils.getNestedObject(originatorsListSource.get().getRawSource(), "$..i", new ArrayList<String>())
                    .stream()
                    .map(personId -> String.format(ORIGINATOR_PAGE_API, personId))
                    .map(originatorPageUrl -> readService.findByPageTypeAndPageUrl(PAGE_TYPE_LAW_ORIGINATOR, originatorPageUrl))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(PageSource::getRawSource)
                    .map(this::getOriginatorFromHTML)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
        } else {
            throw new DataCollectionException("Not found originators list page in the database");
        }
    }

    private Optional<Originator> getOriginatorFromHTML(String html) {
        Document page = Jsoup.parse(html);
        Element nameElement = page.selectXpath("/html/body/section/div[1]/div/div[4]/div[2]/div/div[1]/h2").first();
        Element affilElement = page.selectXpath("//table[@id='trayectoria']/tbody/tr[2]/td/div[contains(.//*/text(),'Partido') or contains(.//*/text(),'Independiente')]").first();
        if (Objects.nonNull(nameElement)) {
            if (Objects.nonNull(affilElement)) {
                return Optional.of(new Originator(nameElement.text().trim(), affilElement.text().trim()));
            }
            return Optional.of(new Originator(nameElement.text().trim()));
        }
        return Optional.empty();
    }
}
