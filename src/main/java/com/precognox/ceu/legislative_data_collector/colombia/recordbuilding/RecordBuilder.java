package com.precognox.ceu.legislative_data_collector.colombia.recordbuilding;

import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.colombia.ColombiaCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.exceptions.GazetteDataCollectionException;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.ReadDatabaseService;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverWrapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.precognox.ceu.legislative_data_collector.colombia.ColombiaDataParser.votePages;
import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.*;
import static com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.GazetteWebpageHandler.GAZETTE_INFORMATION_TEXT_KEY;
import static com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.GazetteWebpageHandler.GAZETTE_INFORMATION_URL_KEY;
import static com.precognox.ceu.legislative_data_collector.colombia.recordbuilding.LawHandler.ORIGINAL_LAW_ID_REGEX;


@Slf4j
public class RecordBuilder {
    protected static final Map<String, Integer> LEGISLATIVE_STAGES = new HashMap<>();
    private static final Map<Integer, String> REVERSE_LEGISLATIVE_STAGES = new HashMap<>();
    private static final String VOTES_FOR_SELECT_QUERY = ".bg-green p";
    private static final String VOTES_AGAINST_SELECT_QUERY = ".bg-red p";
    private static final String VOTES_ABS_SELECT_QUERY = ".bg-gray p";
    private static final String VOTES_KEY_FOR = "for";
    private static final String VOTES_KEY_AGAINST = "against";
    private static final String VOTES_KEY_ABS = "abs";
    private static final boolean RECOLLECT_ALREADY_COLLECTED = false;
    private static final boolean GAZETTE_WEBPAGE_NEEDED = true;

    private static final Pattern MODIFICATION_REGEX = Pattern.compile(
            "(modifica|modifican|adicionan|adiciona|alteran|altera|sustituye|sustituir|substituye|substituir" +
                    "|modificaciones|deroga|derogan|reforma|reforman|reformas|prorroga|prorrogan)");
    private static final Pattern MODIFICATION_REGEX2 = Pattern.compile(
            "(ley|leyes|decreto|decretos|artículos|artículo|articulo|articulos|codigo|codigos)");
    private static final List<String> AMENDMENT_STAGE_STRINGS = List.of(FIRST_DEBATE, SECOND_DEBATE, THIRD_DEBATE, FOURTH_DEBATE, FIRST_AND_THIRD_DEBATE);
    private static final Pattern LEGISLATIVE_ACT_SEARCH_TEXT = Pattern.compile("el presente acto leg.slat.vo", Pattern.CASE_INSENSITIVE);
    private static final int MAX_LEG_STAGES_COUNT = 6;
    private static final int MAX_ONGOING_BILLS_AGE = 2;

    static {
        RecordBuilder.LEGISLATIVE_STAGES.put(FILING, 0);
        RecordBuilder.LEGISLATIVE_STAGES.put(PUBLICATION, 1);
        RecordBuilder.LEGISLATIVE_STAGES.put(FIRST_DEBATE, 2);
        RecordBuilder.LEGISLATIVE_STAGES.put(SECOND_DEBATE, 3);
        RecordBuilder.LEGISLATIVE_STAGES.put(THIRD_DEBATE, 4);
        RecordBuilder.LEGISLATIVE_STAGES.put(FOURTH_DEBATE, 5);
        RecordBuilder.LEGISLATIVE_STAGES.put(SANCTION_NORMAL, 6);

        LEGISLATIVE_STAGES.forEach((key, value) -> REVERSE_LEGISLATIVE_STAGES.put(value, key));
    }

    @Getter
    private final PageSource billPage;
    private final ReadDatabaseService readService;
    @Getter
    private LegislativeDataRecord dataRecord;
    @Getter
    private LawHandler lawPageHandler;
    private BillPageParser pageParser;

    public RecordBuilder(PageSource billPage, ReadDatabaseService readService) {
        this.billPage = billPage;
        this.readService = readService;
    }

    public void buildRecord(WebDriverWrapper browser) {
        Optional<LegislativeDataRecord> recordByBillPageUrl = readService.findByBillPageUrl(billPage.getPageUrl());
        if (recordByBillPageUrl.isPresent()) {
            log.info("Record was already built for: {}", billPage.getPageUrl());
            return;
        }
        dataRecord = recordByBillPageUrl.orElseGet(LegislativeDataRecord::new);
        dataRecord.setCountry(Country.COLOMBIA);
        setUpEmbeddedCollections();
        if (RECOLLECT_ALREADY_COLLECTED || Objects.isNull(dataRecord.getAmendmentCount())) {
            dataRecord.setBillPageUrl(billPage.getPageUrl());
            log.info("Collecting page : {}", dataRecord.getBillPageUrl());
            Document page = Jsoup.parse(billPage.getRawSource());
            pageParser = new BillPageParser(page);

            processBasicBillPage();

            if (dataRecord.getBillStatus().equals(LegislativeDataRecord.BillStatus.PASS)
                    && Objects.nonNull(dataRecord.getLawId())) {
                try {
                    lawPageHandler = new LawHandler(dataRecord.getLawId(),
                            dataRecord.getColombiaCountrySpecificVariables().getBillTypeColombia(),
                            readService);
                    processLawInformation();
                } catch (DataCollectionException ex) {
                    log.error(ex.getMessage());
                }
            }

            if (GAZETTE_WEBPAGE_NEEDED) {
                processGazetteTexts(browser);
            }
            if (isLegislativeActByText()) {
                dataRecord = null;
                log.warn("Bill is legislative act by text [{}]", billPage.getPageUrl());
                return;
            }
            calculateAmendmentSizes();
            dataRecord.setOriginalLaw(isOriginalLaw(dataRecord.getBillTitle()));
            dataRecord.setDateProcessed(LocalDateTime.now());
            log.info("Record successfully collected for: {}", dataRecord.getBillPageUrl());
        }

    }

    private Boolean isOriginalLaw(String billTitle) {
        Boolean isOriginalLaw = true;
        if (MODIFICATION_REGEX.matcher(billTitle).find() && MODIFICATION_REGEX2.matcher(billTitle).find()) {
            return false;
        }
        return isOriginalLaw;
    }

    private void setUpEmbeddedCollections() {
        if (Objects.isNull(dataRecord.getErrors())) {
            dataRecord.setErrors(new HashSet<>());
        }
        if (Objects.isNull(dataRecord.getModifiedLaws())) {
            dataRecord.setModifiedLaws(new HashSet<>());
        }
        dataRecord.setAmendments(new ArrayList<>());
        if (Objects.isNull(dataRecord.getBillVersions())) {
            dataRecord.setBillVersions(new ArrayList<>());
        }
        if (Objects.isNull(dataRecord.getCommittees())) {
            dataRecord.setCommittees(new ArrayList<>());
        }
        dataRecord.setImpactAssessments(new ArrayList<>());
        if (Objects.isNull(dataRecord.getStages())) {
            dataRecord.setStages(new ArrayList<>());
        }
        if (Objects.isNull(dataRecord.getOriginators())) {
            dataRecord.setOriginators(new ArrayList<>());
        }
    }

    private void processBasicBillPage() {
        try {
            String billTitle = pageParser.getBillTitleFromPage();
            dataRecord.setBillTitle(billTitle);
            dataRecord.setOriginalLaw(new OriginalLawCalculator().isOriginalLaw(billTitle));
        } catch (DataCollectionException ex) {
            log.error(ex.getMessage());
        }

        List<String> missing = pageParser.setCountrySpecificVariablesFromBillPage(getCountryVariables());
        if (!missing.isEmpty()) {
            String missingString = StringUtils.join(missing, " & ");
            log.error(String.format("Elements not found on page: %s", missingString));
        }

        parseBillId();
        parseOriginType();
        parseLegislativeStages();

        try {
            dataRecord.setBillStatus(determineBillStatusFromLegStages());
        } catch (DataCollectionException ex) {
            log.error(ex.getMessage());
        }

        parseProcedureType();

        try {
            LocalDate dateOfIntroduction = getIntroductionDateFromLegStages();
            dataRecord.setDateIntroduction(dateOfIntroduction);
        } catch (DataCollectionException ex) {
            log.error(ex.getMessage());
        }

        //'Needs' stages, bill status and introduction stage set
        if (dataRecord.getBillStatus().equals(LegislativeDataRecord.BillStatus.ONGOING) && isExpired()) {
            dataRecord.setBillStatus(LegislativeDataRecord.BillStatus.REJECT);
            getCountryVariables().setBillStatusColombia("Presuntamente Archivado");
        }

        //Needs bill_status set
        parseLawRelatedVars();

        List<Committee> committees = pageParser.getCommittees();
        dataRecord.setCommittees(committees);
        dataRecord.setCommitteeCount(committees.size());

        processVotes();
        processOriginators();
    }

    private void parseBillId() {
        String houseId = Objects.nonNull(dataRecord.getColombiaCountrySpecificVariables().getHouseBillId())
                ? dataRecord.getColombiaCountrySpecificVariables().getHouseBillId() : "";
        String senateId = Objects.nonNull(dataRecord.getColombiaCountrySpecificVariables().getSenateBillId())
                ? dataRecord.getColombiaCountrySpecificVariables().getSenateBillId() : "";
        houseId = cleanId(houseId);
        senateId = cleanId(senateId);
        dataRecord.getColombiaCountrySpecificVariables().setHouseBillId(houseId);
        dataRecord.getColombiaCountrySpecificVariables().setSenateBillId(senateId);
        dataRecord.setBillId(String.format("%s-%s", houseId, senateId));
    }

    private void parseProcedureType() {
        dataRecord.setProcedureTypeStandard(pageParser.getProcedureTypeFromLegStages());
        String nationalProcedureType =
                dataRecord.getProcedureTypeStandard().equals(LegislativeDataRecord.ProcedureType.EXCEPTIONAL) ?
                        "Urgent Message. Art 163 Political Constitution of Colombia" : "Standard";
        dataRecord.setProcedureTypeNational(nationalProcedureType);
    }

    private void parseOriginType() {
        OriginType originType =
                dataRecord.getColombiaCountrySpecificVariables().getOriginTypeColombia() == null ? null :
                        switch (dataRecord.getColombiaCountrySpecificVariables().getOriginTypeColombia()) {
                            case GOVERNMENT -> OriginType.GOVERNMENT;
                            case PARLIAMENTARY -> OriginType.INDIVIDUAL_MP;
                            default -> null;
                        };
        dataRecord.setOriginType(originType);
    }

    private void parseLegislativeStages() {
        try {
            List<LegislativeStage> legislativeStages = pageParser.getLegislativeStagesFromPage();
            dataRecord.setStages(legislativeStages);
            dataRecord.setStagesCount(getNumberOfStages());
        } catch (DataCollectionException ex) {
            log.error(ex.getMessage());
        }
    }

    private void parseLawRelatedVars() {
        if (dataRecord.getBillStatus().equals(LegislativeDataRecord.BillStatus.PASS)) {
            try {
                LocalDate dateOfPassing =
                        pageParser.getDateOfPassing(dataRecord.getColombiaCountrySpecificVariables().getBillTypeColombia());
                dataRecord.setDatePassing(dateOfPassing);
            } catch (DataCollectionException ex) {
                log.error(ex.getMessage());
            }
            try {
                String lawIdText =
                        pageParser.getLawId(dataRecord.getColombiaCountrySpecificVariables().getBillTypeColombia());
                String lawId = getLawIdFromText(lawIdText);
                dataRecord.setLawId(lawId);
            } catch (DataCollectionException ex) {
                log.error(ex.getMessage());
            }
            List<LegislativeStage> missingStages = getMissingStages();
            if (!missingStages.isEmpty()) {
                List<LegislativeStage> completeStageList = new ArrayList<>(dataRecord.getStages());
                completeStageList.addAll(missingStages);
                dataRecord.setStages(completeStageList);
                dataRecord.setStagesCount(getNumberOfStages());
            }
        }
    }

    private void processVotes() {
        try {
            Map<String, Optional<Integer>> votes = getVotesForBill();
            Optional<Integer> votesForOpt = votes.get(VOTES_KEY_FOR);
            Optional<Integer> votesAgainstOpt = votes.get(VOTES_KEY_AGAINST);
            Optional<Integer> votesAbsOpt = votes.get(VOTES_KEY_ABS);
            setVote(dataRecord::setFinalVoteFor, votesForOpt, "supporting");
            setVote(dataRecord::setFinalVoteAgainst, votesAgainstOpt, "opposing");
            setVote(dataRecord::setFinalVoteAbst, votesAbsOpt, "absentee");
        } catch (DataCollectionException ex) {
            log.error(ex.getMessage());
        }
    }

    private void processOriginators() {
        List<Originator> originators = new OriginatorInformationCollector(
                        readService, pageParser, dataRecord.getColombiaCountrySpecificVariables().getOriginTypeColombia())
                        .getOriginatorsFromBillPage();
        dataRecord.setOriginators(originators);
    }

    private String cleanId(String houseOrSenateId) {
        return houseOrSenateId.replaceAll("[^\\d/]+", "");
    }

    private ColombiaCountrySpecificVariables getCountryVariables() {
        if (Objects.isNull(dataRecord.getColombiaCountrySpecificVariables())) {
            dataRecord.setColombiaCountrySpecificVariables(new ColombiaCountrySpecificVariables());
            dataRecord.getColombiaCountrySpecificVariables().setLegislativeDataRecord(dataRecord);
        }
        return dataRecord.getColombiaCountrySpecificVariables();
    }

    private LegislativeDataRecord.BillStatus determineBillStatusFromLegStages() throws DataCollectionException {
        String billStatusString = dataRecord.getColombiaCountrySpecificVariables().getBillStatusColombia();
        if (Objects.isNull(billStatusString)) {
            throw new DataCollectionException("Cannot determine bill status as no legislative stage found on the page");
        }
        boolean hasPresidentialSanction = dataRecord.getStages().stream()
                .map(LegislativeStage::getStageNumber)
                .anyMatch(n -> n == MAX_LEG_STAGES_COUNT);
        if (hasPresidentialSanction) {
            return LegislativeDataRecord.BillStatus.PASS;
        }
        return switch (billStatusString) {
            case "Sancionado como Ley" -> LegislativeDataRecord.BillStatus.PASS;
            case "Retirado por el Autor",
                 "Archivado en Debate",
                 "Archivado por Tránsito de Legislatura",
                 "Archivado por Vencimiento de Términos",
                 "Declarado Inexequible Total",
                 "Acumulado" -> LegislativeDataRecord.BillStatus.REJECT;
            default -> LegislativeDataRecord.BillStatus.ONGOING;
        };
    }

    private LegislativeStage getLegislativeStageByNumber(int number) throws DataCollectionException {
        try {
            return getLegislativeStageByNumber(dataRecord.getStages(), number);
        } catch (DataCollectionException ex) {
            throw new DataCollectionException(
                    String.format("No stage found for %s with number %d", dataRecord.getRecordId(), number));
        }
    }

    private LegislativeStage getLegislativeStageByNumber(List<LegislativeStage> stages, int number)
            throws DataCollectionException {
        Optional<LegislativeStage> stage = stages.stream()
                .filter(legislativeStage -> legislativeStage.getStageNumber() == number)
                .findFirst();
        if (stage.isPresent()) {
            return stage.get();
        }
        throw new DataCollectionException(String.format("No legislative stage found with number : %d", number));
    }

    private int getNumberOfStages() {
        if (Objects.nonNull(dataRecord.getStages())) {
            return (int) dataRecord.getStages().stream()
                    .map(LegislativeStage::getStageNumber)
                    .filter(n -> n != 0)
                    .count();
        }
        return 0;
    }

    private LocalDate getIntroductionDateFromLegStages() throws DataCollectionException {
        try {
            return getDateOfLegislativeStageByNumber(LEGISLATIVE_STAGES.get(FILING));
        } catch (DataCollectionException ex) {
            throw new DataCollectionException("No introduction stage found, cannot determine the date of introduction");
        }
    }

    private LocalDate getDateOfLegislativeStageByNumber(int number) throws DataCollectionException {
        LegislativeStage stage = getLegislativeStageByNumber(number);
        if (Objects.nonNull(stage.getDate())) {
            return stage.getDate();
        }
        throw new DataCollectionException("No date found for the stage");
    }

    /**
     * If a bill is still ongoing and is more than 2 years old, or it is older than 1 year and not had a debate approved
     * yet, it should be considered rejected.
     * This method checks for that.
     */
    private boolean isExpired() {
        if (Objects.nonNull(dataRecord.getDateIntroduction())) {
            LocalDate today = LocalDate.now();
            int ageInYears = DateUtils.getDifference(today, dataRecord.getDateIntroduction()).getYears();
            if (ageInYears >= MAX_ONGOING_BILLS_AGE) {
                return true;
            }
            if (ageInYears == 1 && Objects.nonNull(dataRecord.getStages())) {
                long debateCount = dataRecord.getStages().stream()
                        .map(LegislativeStage::getStageNumber)
                        .filter(n -> n > 1 && n < MAX_LEG_STAGES_COUNT)
                        .count();
                return debateCount == 0;
            }
        }
        return false;
    }

    /**
     * If the bill is passed, and it doesn't contain one of the debate stages [as it is missing from the page] it is returned here
     */
    private List<LegislativeStage> getMissingStages() {
        return IntStream.rangeClosed(LEGISLATIVE_STAGES.get(FIRST_DEBATE), LEGISLATIVE_STAGES.get(FOURTH_DEBATE))
                .filter(n -> !existStageByNumberForRecord(n))
                .mapToObj(n -> {
                    LegislativeStage stage = new LegislativeStage();
                    stage.setStageNumber(n);
                    stage.setName(LEGISLATIVE_STAGE_TRANSLATIONS.get(REVERSE_LEGISLATIVE_STAGES.get(n)));
                    return stage;
                })
                .toList();
    }

    private boolean existStageByNumberForRecord(int stageNumber) {
        try {
            getLegislativeStageByNumber(dataRecord.getStages(), stageNumber);
            return true;
        } catch (DataCollectionException e) {
            return false;
        }
    }

    private String getLawIdFromText(String text) throws DataCollectionException {
        String lawId = text.trim();
        if (ORIGINAL_LAW_ID_REGEX.matcher(lawId).find()) {
            return LawHandler.createUniformLawId(lawId);
        } else {
            Optional<String> gatheredLawId = handleIncorrectLawIds(lawId);
            if (gatheredLawId.isPresent()) {
                return LawHandler.createUniformLawId(gatheredLawId.get());
            } else {
                throw new DataCollectionException("No valid law id found for this law on the bill page");
            }
        }
    }

    /**
     * Although it not matches perfectly with the law id regex it contains it just with full date, or inside brackets. This method tries to extract these kinds of law ids
     */
    private Optional<String> handleIncorrectLawIds(String lawIdText) {
        //When no lawId is given
        if (lawIdText.equals("Sin anotaciones")) {
            return Optional.empty();
        }
        //When law id contains the whole date of passing
        Pattern wholeDateLawId = Pattern.compile("([Ll]ey|[Aa]cto [Ll]egislativo)\\s*(\\d+)\\s*(?:de|del)[^.]*?de\\D*(\\d+)");
        Matcher datedIdMatcher = wholeDateLawId.matcher(lawIdText);
        if (datedIdMatcher.find()) {
            return Optional.of(String.format("%s %s de %s", datedIdMatcher.group(1), datedIdMatcher.group(2), datedIdMatcher.group(3)));
        }
        //When text contains law id and other information as well (maybe multiple ids). The only example had its id updated, therefore the last one is selected
        Pattern basicLawId = Pattern.compile("(([Ll]ey|[Aa]cto [Ll]egislativo)\\s*\\d+\\s*[dD][eE]\\s*\\d+)");
        Matcher idMatcher = basicLawId.matcher(lawIdText);
        Optional<String> lastId = Optional.empty();
        while (idMatcher.find()) {
            lastId = Optional.of(idMatcher.group(1));
        }
        return lastId;
    }

    private Map<String, Optional<Integer>> getVotesForBill() throws DataCollectionException {
        LocalDate voteDate = getDateOfVoting();
        if (Objects.nonNull(dataRecord.getBillTitle())) {
            Optional<Element> voteElement = Optional.empty();
            for (Document page : votePages) {
                Elements voteElements = page.select(".ActLegislativaVotacionesList .listadoItem");
                voteElement = findVoteElementForBillInElements(voteElements, voteDate);
                if (voteElement.isPresent()) {
                    break;
                }
            }

            if (voteElement.isPresent()) {
                return Map.of(
                        VOTES_KEY_FOR, getVoteCount(VOTES_FOR_SELECT_QUERY, voteElement.get()),
                        VOTES_KEY_AGAINST, getVoteCount(VOTES_AGAINST_SELECT_QUERY, voteElement.get()),
                        VOTES_KEY_ABS, getVoteCount(VOTES_ABS_SELECT_QUERY, voteElement.get())
                );
            } else {
                log.debug("No vote information found");
                throw new DataCollectionException("Vote data was not found for this law");
            }

        } else {
            throw new DataCollectionException("Votes can't be collected as bill does not have a title");
        }
    }

    private LocalDate getDateOfVoting() throws DataCollectionException {
        if (Objects.isNull(dataRecord.getColombiaCountrySpecificVariables().getBillTypeColombia())) {
            throw new DataCollectionException("Bill type missing! Stage of voting cannot be determined!");
        }
        try {
            String stageName =
                    dataRecord.getColombiaCountrySpecificVariables().getBillTypeColombia().equals("Proyecto Acto Legislativo")
                            ? SANCTION_LEGISLATIVE : FOURTH_DEBATE;
            return getDateOfLegislativeStageByNumber(LEGISLATIVE_STAGES.get(stageName));
        } catch (DataCollectionException ex) {
            throw new DataCollectionException("Stage for voting not found");
        }
    }

    private Optional<Element> findVoteElementForBillInElements(List<Element> voteElements, LocalDate voteDate) {
        return voteElements.stream()
                .filter(element -> { //Filter by title
                    Element voteTitleElement = element.selectFirst(".itemHeader");
                    if (Objects.isNull(voteTitleElement)) {
                        return false;
                    }
                    String voteTitle = voteTitleElement.text().replaceAll("<[^>]*>", "").trim();
                    return dataRecord.getBillTitle().equals(voteTitle);
                })
                .filter(element -> { //Filter by date
                    Element dateOfVoteElement = element.selectFirst(".itemFooter > div:nth-child(1) > p:nth-child(1)");
                    if (Objects.isNull(dateOfVoteElement)) {
                        return false;
                    }
                    LocalDate dateOfVote = DateUtils.parseColombiaVotingDate(dateOfVoteElement.text().trim());
                    return voteDate.isEqual(dateOfVote);
                })
                .findFirst();
    }

    private Optional<Integer> getVoteCount(String cssQuery, Element voteElement) {
        return voteElement.select(cssQuery).stream()
                .filter(Objects::nonNull)
                .map(Element::text)
                .filter(text -> !text.trim().isBlank())
                .map(Integer::parseInt)
                .findFirst();
    }

    private void setVote(Consumer<Integer> setMethod, Optional<Integer> count, String voteType) {
        if (count.isPresent()) {
            setMethod.accept(count.get());
        } else {
            log.error("Count of {} votes not found for {}", voteType, dataRecord.getBillPageUrl());
        }
    }

    private void processLawInformation() {
        try {
            LocalDate dateOfEnteringForce = lawPageHandler.getDateOfEnteringIntoForce();
            dataRecord.setDateEnteringIntoForce(dateOfEnteringForce);
        } catch (DataCollectionException ex) {
            log.error(ex.getMessage());
        }
        if (Objects.isNull(dataRecord.getLawText())) {
            try {
                String lawText = lawPageHandler.getLawTextFromSenatePage();
                String lawTextUrl = lawPageHandler.createSenatePageUrl();
                dataRecord.setLawText(lawText);
                dataRecord.setLawTextUrl(lawTextUrl);
            } catch (DataCollectionException ex) {
                log.error(ex.getMessage());
            }
        }

    }

    private void processGazetteTexts(WebDriverWrapper browser) {
        if (Objects.isNull(dataRecord.getBillText()) || dataRecord.getBillText().isBlank()) {
            try {
                Map<String, String> billTextInformation =
                        getTextInformation(GazetteType.BILL, PUBLICATION, dataRecord.getBillId(), browser);
                dataRecord.setBillTextUrl(billTextInformation.get(GAZETTE_INFORMATION_URL_KEY));
                String billText = billTextInformation.get(GAZETTE_INFORMATION_TEXT_KEY);
                dataRecord.setBillText(cleanBillText(billText));
            } catch (DataCollectionException ex) {
                if (ex instanceof GazetteDataCollectionException gex) {
                    Optional.ofNullable(gex.getUrl()).ifPresent(dataRecord::setBillTextUrl);
                }
                log.error(ex.getMessage());
            }
        }

        if (Objects.isNull(dataRecord.getLawText()) || dataRecord.getLawText().isBlank()) {
            if (dataRecord.getBillStatus().equals(LegislativeDataRecord.BillStatus.PASS) && Objects.nonNull(dataRecord.getLawId())) {
                String stageName = (dataRecord.getColombiaCountrySpecificVariables().getBillTypeColombia().equals("Proyecto Acto Legislativo"))
                        ? SANCTION_LEGISLATIVE : SANCTION_NORMAL;
                try {
                    Map<String, String> lawTextInformation = getTextInformation(GazetteType.LAW, stageName, dataRecord.getLawId(), browser);
                    dataRecord.setLawTextUrl(lawTextInformation.get(GAZETTE_INFORMATION_URL_KEY));
                    dataRecord.setLawText(lawTextInformation.get(GAZETTE_INFORMATION_TEXT_KEY));
                } catch (DataCollectionException ex) {
                    if (ex instanceof GazetteDataCollectionException gex) {
                        Optional.ofNullable(gex.getUrl()).ifPresent(dataRecord::setBillTextUrl);
                    }
                    log.error(ex.getMessage());
                }
            }
        }


        AMENDMENT_STAGE_STRINGS.forEach(amendmentStageString -> {
            if (hasAmendmentForStage(amendmentStageString)) {
                log.info("Amendment stage {} already collected for {}", amendmentStageString, dataRecord.getRecordId());
            } else {
                try {
                    Map<String, String> amendmentTextInformation = getTextInformation(GazetteType.AMENDMENT, amendmentStageString, dataRecord.getBillId(), browser);
                    addAmendmentToRecord(amendmentTextInformation, amendmentStageString);
                } catch (DataCollectionException ex) {
                    if (ex instanceof GazetteDataCollectionException gex) {
                        Optional.ofNullable(gex.getUrl()).ifPresent(amendmentUrl -> addAmendmentToRecord(Map.of(GAZETTE_INFORMATION_URL_KEY, amendmentUrl, GAZETTE_INFORMATION_TEXT_KEY, gex.getMessage()), amendmentStageString));
                    }
                    log.error(ex.getMessage());
                }
            }
        });
        dataRecord.setAmendmentCount(dataRecord.getAmendments().size());
    }

    private String cleanBillText(String billText) {
        return billText.replaceAll("(?im)(ART[íÍ]CULO[\\s]*1[^\\d].*)ART[íÍ]CULO[\\s]*1[^\\d].*$", "$1");
    }

    private Map<String, String> getTextInformation(GazetteType type, String stageName, String identifier, WebDriverWrapper browser)
            throws DataCollectionException {
        log.info(String.format("%s text; Stage: %s", type, LEGISLATIVE_STAGE_TRANSLATIONS.get(stageName)));
        List<String> gazetteIssues = pageParser.getGazetteIssuesForStageByName(stageName);
        if (gazetteIssues.isEmpty()) {
            log.error("No gazettes");
        }

        GazetteWebpageHandler gazettePageHandler =
                new GazetteWebpageHandler(browser, readService, type, identifier, STAGES_TO_SOURCE_TYPES.get(stageName));
        for (String gazetteIssue : gazetteIssues) {
            try {
                return gazettePageHandler.getGazetteInformation(gazetteIssue, browser);
            } catch (DataCollectionException ex) {
                log.error(String.format("(Gazette: %s) => %s", gazetteIssue, ex.getMessage()));
            }
        }
        throw new GazetteDataCollectionException("No gazette found for this law -", gazettePageHandler.getUrl());
    }

    /**
     * @param stageName Colombian spelling of stagename
     */
    private boolean hasAmendmentForStage(String stageName) {
        return dataRecord.getAmendments()
                .stream()
                .map(Amendment::getStageName)
                .anyMatch(amendmentStageName -> amendmentStageName.equals(LEGISLATIVE_STAGE_TRANSLATIONS.get(stageName)));
    }

    private void addAmendmentToRecord(Map<String, String> textInformation, String stageName) {
        if (stageName.equals(FIRST_AND_THIRD_DEBATE)) {
            if (!hasAmendmentForStage(FIRST_DEBATE) && !hasAmendmentForStage(THIRD_DEBATE)) {
                createAmendmentsForJoinedFirstAndThirdDebate(textInformation)
                        .forEach(dataRecord.getAmendments()::add);
            } else {
                if (!hasAmendmentForStage(FIRST_DEBATE)) {
                    createAmendment(textInformation, FIRST_DEBATE);
                }
                if (!hasAmendmentForStage(THIRD_DEBATE)) {
                    createAmendment(textInformation, THIRD_DEBATE);
                }
            }
        } else {
            dataRecord.getAmendments().add(createAmendment(textInformation, stageName));
        }
    }

    private List<Amendment> createAmendmentsForJoinedFirstAndThirdDebate(Map<String, String> textInformation) {
        return List.of(
                createAmendment(textInformation, FIRST_DEBATE),
                createAmendment(textInformation, THIRD_DEBATE)
        );
    }

    private Amendment createAmendment(Map<String, String> textInformation, String stageName) {
        Amendment amendment = new Amendment();
        amendment.setDataRecord(dataRecord);
        amendment.setStageName(LEGISLATIVE_STAGE_TRANSLATIONS.get(stageName));
        amendment.setStageNumber(LEGISLATIVE_STAGES.get(stageName));
        try {
            amendment.setDate(getDateOfLegislativeStageByNumber(LEGISLATIVE_STAGES.get(stageName)));
        } catch (DataCollectionException ex) {
            log.warn("Could not find date for amendment", ex);
        }
        if (textInformation.containsKey(GAZETTE_INFORMATION_URL_KEY)) {
            amendment.setTextSourceUrl(textInformation.get(GAZETTE_INFORMATION_URL_KEY));
        }
        if (textInformation.containsKey(GAZETTE_INFORMATION_TEXT_KEY)) {
            amendment.setAmendmentText(textInformation.get(GAZETTE_INFORMATION_TEXT_KEY));
        }
        return amendment;
    }

    /**
     * There is a chance that the bill is labelled wrongly as Proyecto de Ley on the Congresso webpage.
     * Because of this bill texts have to be checked whether they contain the phrase 'el presente acto legislativo'.
     * If they do, they need to be discarded.
     */
    private boolean isLegislativeActByText() {
        if (Objects.nonNull(dataRecord.getBillText())) {
            return LEGISLATIVE_ACT_SEARCH_TEXT.matcher(dataRecord.getBillText()).find();
        }
        return false;
    }

    private void calculateAmendmentSizes() {
        AmendmentDiffCalculator amendmentCalculator = new AmendmentDiffCalculator(dataRecord);
        Map<String, Consumer<Integer>> setterMethods = Map.of(
                LEGISLATIVE_STAGE_TRANSLATIONS.get(FIRST_DEBATE), getCountryVariables()::setAmendmentSizeDebateOne,
                LEGISLATIVE_STAGE_TRANSLATIONS.get(SECOND_DEBATE), getCountryVariables()::setAmendmentSizeDebateTwo,
                LEGISLATIVE_STAGE_TRANSLATIONS.get(THIRD_DEBATE), getCountryVariables()::setAmendmentSizeDebateThree,
                LEGISLATIVE_STAGE_TRANSLATIONS.get(FOURTH_DEBATE), getCountryVariables()::setAmendmentSizeDebateFour
        );
        dataRecord.getAmendments()
                .forEach(amendment -> {
                    try {
                        int charDiff = amendmentCalculator.getCharDiffForAmendment(amendment);
                        setterMethods.get(amendment.getStageName()).accept(charDiff);
                    } catch (DataCollectionException ex) {
                       log.error(ex.getMessage());
                    }
                });
    }

    public List<PageSource> getSources() {
        List<PageSource> sources = new ArrayList<>();
        sources.add(billPage);
        if (Objects.nonNull(lawPageHandler) && lawPageHandler.getPageSource().isPresent()) {
            PageSource lawPage = lawPageHandler.getPageSource().get();
            sources.add(lawPage);
            sources.addAll(lawPageHandler.getTextPages());
        }
        return sources;
    }
}
