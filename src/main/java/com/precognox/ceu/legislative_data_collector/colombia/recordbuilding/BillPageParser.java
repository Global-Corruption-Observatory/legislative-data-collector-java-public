package com.precognox.ceu.legislative_data_collector.colombia.recordbuilding;

import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord.ProcedureType;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.colombia.ColombiaCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.COMMITTEE_ROLES;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.FILING;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.FIRST_AND_THIRD_DEBATE;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.FIRST_DEBATE;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.FOURTH_DEBATE;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.LEGISLATIVE_STAGE_TRANSLATIONS;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.ORIGIN_TYPE_TRANSLATIONS;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.PUBLICATION;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.SANCTION_LEGISLATIVE;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.SANCTION_NORMAL;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.SECOND_DEBATE;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.THIRD_DEBATE;
import static com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.RecordBuilder.LEGISLATIVE_STAGES;

/**
 * This class holds methods for reading the information from the bill page, and holds the information about legislative stages
 */
public class BillPageParser {

    private static final String DATE_KEY = "date";
    private static final String NAME_KEY = "name";
    private static final String GAZETTES_KEY = "gazettes";
    private static final String COMMENT_KEY = "comment";

    private final Document page;

    private List<Map<String, Optional<String>>> stagesInformation;

    public BillPageParser(Document page) {
        this.page = page;
        this.stagesInformation = new ArrayList<>();
        collectStageElementInformation();
    }

    /**
     * Collects all main information about legislative stages into a map, to avoid repeated parsing of the HTML document
     */
    private void collectStageElementInformation() {
        this.stagesInformation = page.select(".coloquial-timeline > ul > li > .rowInfo")
                .stream()
                .map(element -> {
                    Map<String, Optional<String>> elementInformation = new HashMap<>();
                    Element dateElement = element.selectFirst(".fecha");
                    Element nameElement = element.selectFirst(".estado");
                    Element commentElement = element.selectFirst(".debate p");
                    String date = Objects.nonNull(dateElement) ? dateElement.text().trim() : null;
                    String name = Objects.nonNull(nameElement) ? nameElement.text().replaceAll("-.*", "").trim() : null;
                    String gazettes = Objects.nonNull(nameElement) ? nameElement.text().replaceAll(".*?-.*?:", "").trim() : null;
                    String comment = Objects.nonNull(commentElement) ? commentElement.text().trim() : null;
                    elementInformation.put(DATE_KEY, Optional.ofNullable(date));
                    elementInformation.put(NAME_KEY, Optional.ofNullable(name));
                    elementInformation.put(GAZETTES_KEY, Optional.ofNullable(gazettes));
                    elementInformation.put(COMMENT_KEY, Optional.ofNullable(comment));
                    return elementInformation;
                })
                .filter(stageMap -> stageMap.get(NAME_KEY).isPresent())
                .toList();
    }

    public String getBillTitleFromPage() throws DataCollectionException {
        Element titleElement = page.selectFirst(".CVBannerTitle");
        if (Objects.nonNull(titleElement)) {
            return titleElement.text().trim();
        } else {
            throw new DataCollectionException("Bill title element not found on page");
        }
    }

    /**
     * sets values for country specific variables
     *
     * @return the list of variables missing (except gazette numbers)
     */
    public List<String> setCountrySpecificVariablesFromBillPage(ColombiaCountrySpecificVariables countryVariables) {
        List<String> missing = new ArrayList<>();

        trySettingField(getOriginTypeSetterMethod(countryVariables), ".two-columns > p:nth-child(7)", "^[^:]+[:]", missing, "origin type");
        trySettingField(countryVariables::setBillMainTopic, ".two-columns > p:nth-child(3)", "Tema principal:", missing, "main topic");
        trySettingField(countryVariables::setBillSecondaryTopic, ".two-columns > p:nth-child(4)", "Tema secundario:", missing, "secondary topic");
        trySettingField(countryVariables::setBillSummary, ".TextoFormateado", "<[^>]*>", missing, "summary");
        trySettingField(countryVariables::setHouseBillId, ".two-columns > p:nth-child(1)", missing, "house bill id");
        trySettingField(countryVariables::setSenateBillId, ".two-columns > p:nth-child(2)", missing, "senate bill id");
        trySettingField(countryVariables::setBillStatusColombia, ".coloquial-timeline ul li .rowInfo .estado", "-.*", missing, "legislative stages");
        trySettingField(countryVariables::setBillTypeColombia, ".two-columns > p:nth-child(8)", "Tipo de proyecto de ley:", missing, "bill type");

        countryVariables.setProceduralDefectDummy(checkForProceduralDefect());

        setGazetteNumbersForStage(countryVariables::setFilingGazette, FILING);
        setGazetteNumbersForStage(countryVariables::setPublicationGazette, PUBLICATION);
        setGazetteNumbersForStage(countryVariables::setFirstDebateGazette, FIRST_DEBATE);
        setGazetteNumbersForStage(countryVariables::setSecondDebateGazette, SECOND_DEBATE);
        setGazetteNumbersForStage(countryVariables::setThirdDebateGazette, THIRD_DEBATE);
        setGazetteNumbersForStage(countryVariables::setFourthDebateGazette, FOURTH_DEBATE);
        setGazetteNumbersForStage(countryVariables::setSanctionGazette, List.of(SANCTION_NORMAL, SANCTION_LEGISLATIVE));
        if (Objects.isNull(countryVariables.getFirstDebateGazette()) &&
                Objects.isNull(countryVariables.getThirdDebateGazette())) {
            setGazetteNumbersForStage(countryVariables::setFirstDebateGazette, FIRST_AND_THIRD_DEBATE);
            setGazetteNumbersForStage(countryVariables::setThirdDebateGazette, FIRST_AND_THIRD_DEBATE);
        }

        return missing;
    }

    private void trySettingField(Consumer<String> setFieldMethod, String cssQuery, List<String> missing,
                                 String missingVariableName) {
        trySettingField(setFieldMethod, cssQuery, null, missing, missingVariableName);
    }

    private void trySettingField(Consumer<String> setFieldMethod, String cssQuery, String replaceRegexString,
                                 List<String> missing, String missingVariableName) {
        Optional<String> replaceRegex = Optional.ofNullable(replaceRegexString);
        try {
            if (replaceRegex.isPresent()) {
                setField(setFieldMethod, cssQuery, replaceRegexString);
            } else {
                setField(setFieldMethod, cssQuery);
            }
        } catch (DataCollectionException ex) {
            missing.add(missingVariableName);
        }
    }

    private void setField(Consumer<String> setFieldMethod, String cssQuery) throws DataCollectionException {
        setField(setFieldMethod, cssQuery, null);
    }

    private void setField(Consumer<String> setFieldMethod, String cssQuery, String replaceRegexString)
            throws DataCollectionException {
        Optional<String> replaceRegex = Optional.ofNullable(replaceRegexString);
        Element element = page.selectFirst(cssQuery);
        if (Objects.nonNull(element)) {
            String text = element.text().trim();
            if (replaceRegex.isPresent()) {
                text = text.replaceAll(replaceRegex.get(), "").trim();
            }
            if (!text.isBlank()) {
                setFieldMethod.accept(text);
                return;
            }
        }
        throw new DataCollectionException("Field not found");
    }

    private Consumer<String> getOriginTypeSetterMethod(ColombiaCountrySpecificVariables countryVariables) {
        return (String typeString) ->
                countryVariables.setOriginTypeColombia(ORIGIN_TYPE_TRANSLATIONS.get(typeString.toLowerCase().trim()));
    }

    private boolean checkForProceduralDefect() {
        return this.stagesInformation
                .stream()
                .map(stageMap -> stageMap.get(NAME_KEY))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(LEGISLATIVE_STAGES::containsKey)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .values()
                .stream()
                .anyMatch(count -> count > 1);
    }

    private void setGazetteNumbersForStage(Consumer<String> setMethod, String stage) {
        setGazetteNumbersForStage(setMethod, List.of(stage));
    }

    private void setGazetteNumbersForStage(Consumer<String> setMethod, List<String> stages) {
        String gazettes = StringUtils.join(getGazetteIssuesForStages(stages), ", ");
        if (!gazettes.isBlank()) {
            setMethod.accept(gazettes);
        }
    }

    public List<String> getGazetteIssuesForStages(List<String> stageNames) {
        return stageNames.stream()
                .flatMap(stage -> getGazetteIssuesForStageByName(stage).stream())
                .toList();
    }

    public List<String> getGazetteIssuesForStageByName(String stageName) {
        final List<String> gazettes = new ArrayList<>();
        getStageElementInformation(NAME_KEY, stageName).ifPresent(stageMap -> {
            if (stageMap.get(GAZETTES_KEY).isPresent()) {
                String[] gazetteIssues = stageMap.get(GAZETTES_KEY).get().split(",");
                Arrays.stream(gazetteIssues)
                        .map(String::trim)
                        .forEach(gazettes::add);
            }
        });
        return gazettes;
    }

    public List<LegislativeStage> getLegislativeStagesFromPage() throws DataCollectionException {
        if (this.stagesInformation.isEmpty()) {
            throw new DataCollectionException("No legislative stage elements found on the page");
        }
        Stream<LegislativeStage> usualProceedingsLegStage = getStagesForUsualProceedings();
        Stream<LegislativeStage> joinedFirstAndThirdLegStage = getStagesForJoinedFirstAndThirdSession();
        return Stream.concat(usualProceedingsLegStage, joinedFirstAndThirdLegStage).toList();
    }

    private Stream<LegislativeStage> getStagesForUsualProceedings() {
        return LEGISLATIVE_STAGES.keySet()
                .stream()
                .map(stageName -> getStageElementInformation(NAME_KEY, stageName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(stageMap -> {
                    if (stageMap.get(DATE_KEY).isEmpty()) {
                        return Optional.<LegislativeStage>empty();
                    }
                    return Optional.of(buildLegislativeStageFromStageMap(stageMap));
                })
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Stream<LegislativeStage> getStagesForJoinedFirstAndThirdSession() {
        Optional<Map<String, Optional<String>>> stageMapOpt = getStageElementInformation(NAME_KEY, FIRST_AND_THIRD_DEBATE);
        if (stageMapOpt.isPresent()) {
            Map<String, Optional<String>> stageMap = stageMapOpt.get();
            if (stageMap.get(DATE_KEY).isEmpty()) {
                return Stream.empty();
            }
            LegislativeStage debateOne = buildLegislativeStageFromStageMap(stageMap, FIRST_DEBATE);
            LegislativeStage debateThree = buildLegislativeStageFromStageMap(stageMap, THIRD_DEBATE);
            return Stream.of(debateOne, debateThree);
        }
        return Stream.empty();
    }

    private LegislativeStage buildLegislativeStageFromStageMap(Map<String, Optional<String>> stageMap) {
        String stageName = stageMap.get(NAME_KEY).get();
        return buildLegislativeStageFromStageMap(stageMap, stageName);
    }

    private LegislativeStage buildLegislativeStageFromStageMap(Map<String, Optional<String>> stageMap, String stageName) {
        LegislativeStage stage = new LegislativeStage();
        stage.setName(LEGISLATIVE_STAGE_TRANSLATIONS.get(stageName));
        stage.setStageNumber(LEGISLATIVE_STAGES.get(stageName));
        stage.setDate(DateUtils.parseColombiaDate(stageMap.get(DATE_KEY).get()));
        return stage;
    }

    public ProcedureType getProcedureTypeFromLegStages() {
        boolean exceptional = this.stagesInformation.stream()
                .anyMatch(stageMap -> stageMap.get(NAME_KEY).isPresent()
                        && stageMap.get(NAME_KEY).get().equals(FIRST_AND_THIRD_DEBATE));
        return exceptional ? ProcedureType.EXCEPTIONAL : ProcedureType.REGULAR;
    }

    public LocalDate getDateOfPassing(String billType) throws DataCollectionException {
        String stageName = getPassingStageName(billType);
        Optional<LocalDate> datePassing = getDateFromStageByName(stageName);
        if (datePassing.isPresent()) {
            return datePassing.get();
        } else {
            if (getStageElementInformation(NAME_KEY, stageName).isPresent()) {
                throw new DataCollectionException("Date of passing not found on the page");
            } else {
                throw new DataCollectionException("Stage of passing element not found on the page");
            }
        }
    }

    private String getPassingStageName(String billType) {
        return billType.equals("Proyecto Acto Legislativo") ? SANCTION_LEGISLATIVE : SANCTION_NORMAL;
    }

    public Optional<LocalDate> getDateFromStageByName(String stageName) {
        Optional<Map<String, Optional<String>>> stage = getStageElementInformation(NAME_KEY, stageName);
        if (stage.isPresent()) {
            Optional<String> date = stage.get().get(DATE_KEY);
            if (date.isPresent()) {
                return Optional.of(DateUtils.parseColombiaDate(date.get()));
            }
        }
        return Optional.empty();
    }

    public Optional<Map<String, Optional<String>>> getStageElementInformation(String getBy, String expectedValue) {
        List<Map<String, Optional<String>>> stagesMatching = this.stagesInformation
                .stream()
                .filter(stageMap -> stageMap.containsKey(getBy))
                .filter(stageMap -> stageMap.get(getBy).isPresent())
                .filter(stageMap -> stageMap.get(getBy).get().equals(expectedValue))
                .toList();
        if (stagesMatching.isEmpty()) {
            return Optional.empty();
        } else if (stagesMatching.size() == 1) {
            return Optional.ofNullable(stagesMatching.get(0));
        } else { //To make sure it always returns the oldest one (the reference to this is in the scraping status colombian sheet line 17)
            Optional<LocalDate> requiredDate = stagesMatching.stream()
                    .map(stageMap -> stageMap.get(DATE_KEY))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(DateUtils::parseColombiaDate)
                    .min(LocalDate::compareTo);

            return requiredDate.flatMap(reqDate ->
                    stagesMatching.stream()
                            .filter(stageMap -> stageMap.get(DATE_KEY).isPresent() &&
                                    DateUtils.parseColombiaDate(stageMap.get(DATE_KEY).get()).isEqual(reqDate))
                            .findFirst());
        }
    }

    public String getLawId(String billType) throws DataCollectionException {
        String stageName = getPassingStageName(billType);
        Optional<Map<String, Optional<String>>> passingStageInformation = getStageElementInformation(NAME_KEY, stageName);
        if (passingStageInformation.isPresent()) {
            Optional<String> lawId = passingStageInformation.get().get(COMMENT_KEY);
            if (lawId.isPresent()) {
                return lawId.get();
            } else {
                throw new DataCollectionException("Law id not found on the page");
            }
        } else {
            throw new DataCollectionException("Stage of passing element not found on the page");
        }
    }

    public List<Committee> getCommittees() {
        Elements legislativeStages = page.select(".coloquial-timeline > ul > li");
        return legislativeStages.stream()
                .filter(this::isCommitteeStageElement)
                .map(element -> {
                    Optional<LocalDate> stageDate = Optional.ofNullable(element.selectFirst(".fecha"))
                            .map(dateElem -> DateUtils.parseColombiaDate(dateElem.text().trim()));

                    return getCommitteeNamesFromElement(element)
                            .stream()
                            .map(this::buildCommitteeFromName)
                            .peek(committee -> stageDate.ifPresent(committee::setDate))
                            .toList();
                })
                .flatMap(List::stream)
                .toList();
    }

    private boolean isCommitteeStageElement(Element element) {
        Element stageNameElement = element.selectFirst(".estado");
        if (Objects.isNull(stageNameElement)) {
            return false;
        }
        String stageName = stageNameElement.text().replaceAll("-.*", "").trim();
        return (stageName.equals(FIRST_AND_THIRD_DEBATE)
                || stageName.equals(FIRST_DEBATE)
                || stageName.equals(THIRD_DEBATE));
    }

    private List<String> getCommitteeNamesFromElement(Element element) {
        return element.select(".rowComisiones li").stream()
                .filter(Objects::nonNull)
                .map(committeeElement -> committeeElement.selectFirst(".info p a"))
                .filter(Objects::nonNull)
                .map(Element::text)
                .distinct()
                .toList();
    }

    private Committee buildCommitteeFromName(String name) {
        Committee committee = new Committee();
        committee.setName(name);
        committee.setRole(getCommitteeRole(name));
        return committee;
    }

    private String getCommitteeRole(String name) {
        String numberOfTheCommittee = name.split(" ")[0];
        return COMMITTEE_ROLES.getOrDefault(numberOfTheCommittee, "Unknown committee");
    }

    public Elements getOriginatorContainers() {
        return page.select(".miembros .item");
    }

    ///---------------------METHODS THAT ARE NO LONGER USED BUT COULD BE NEEDED IF LEGISLATIVE STAGES WILL ONLY NEED THE 4 DEBATES
    public boolean existsPassingStage() {
        return getStageElementInformation(NAME_KEY, SANCTION_NORMAL).isPresent()
                || getStageElementInformation(NAME_KEY, SANCTION_LEGISLATIVE).isPresent();
    }

    public LocalDate getDateOfIntroduction() throws DataCollectionException {
        Optional<LocalDate> publicationDate = getDateFromStageByName(PUBLICATION);
        if (publicationDate.isPresent()) {
            return publicationDate.get();
        }
        Optional<LocalDate> filingDate = getDateFromStageByName(FILING);
        if (filingDate.isPresent()) {
            return filingDate.get();
        }
        throw new DataCollectionException("No introduction stage element found on page, cannot determine the date of introduction");
    }
}
