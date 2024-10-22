package com.precognox.ceu.legislative_data_collector.uk;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import com.precognox.ceu.legislative_data_collector.common.BillAndLawTextCollector;
import com.precognox.ceu.legislative_data_collector.common.Constants;
import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.BillVersion;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.ImpactAssessment;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord.BillStatus;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.RawPageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.DocumentDownloader;
import com.precognox.ceu.legislative_data_collector.utils.selenium.SeleniumUtils;
import kong.unirest.HttpResponse;
import kong.unirest.HttpStatus;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.http.HttpHeaders;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.precognox.ceu.legislative_data_collector.utils.DateUtils.parseUkDate;
import static com.precognox.ceu.legislative_data_collector.utils.JsonUtils.jsonArrayToObjectList;
import static com.precognox.ceu.legislative_data_collector.utils.JsonUtils.toObjectStream;
import static com.precognox.ceu.legislative_data_collector.utils.selenium.SeleniumUtils.getText;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

@Slf4j
@Service
public class UkDataCollector implements ScrapingController {

    private static final String BILL_DETAILS_ENDPOINT = CommonConstants.BILLS_API_BASE_URL + "/Bills/%s";
    private static final String LIST_PUBLICATIONS_ENDPOINT = CommonConstants.BILLS_API_BASE_URL + "/Bills/%s/Publications";
    private static final String LIST_STAGES_ENDPOINT = CommonConstants.BILLS_API_BASE_URL + "/Bills/%s/Stages";

    private static final int STAGE_ID_COMMONS_FIRST_READING = 6;
    private static final int STAGE_ID_COMMONS_COMMITTEE = 8;
    private static final int STAGE_ID_ROYAL_ASSENT = 11;
    private static final int STAGE_ID_PROGRAMME_MOTION = 14;
    private static final int STAGE_ID_MONEY_RESOLUTION = 15;
    private static final int STAGE_ID_WAYS_AND_MEANS_RESOLUTION = 36;

    private static final int PUBLICATION_TYPE_BILL = 5;
    private static final int PUBLICATION_TYPE_ACT_OF_PARLIAMENT = 6;
    private static final int PUBLICATION_TYPE_AMENDMENT = 7;
    private static final int PUBLICATION_TYPE_BILL_PROCEEDINGS_COMMONS = 8;
    private static final int PUBLICATION_TYPE_IMPACT_ASSESSMENT = 36;

    private static final int GOVERNMENT_BILL_TYPE_ID = 1;
    private static final int PRIVATE_BILL_TYPE_ID = 6;
    private static final List<Integer> INDIVIDUAL_MP_BILL_TYPE_IDS = List.of(2, 3, 4, 5, 7, 8);

    private static final Pattern ACT_ID_REGEX = Pattern.compile("(ukpga|ukla|uksi)/(18|19|20)\\d\\d/\\d+");
    private static final Pattern PDF_LINK_REGEX = Pattern.compile(
            "<a class=\"pdfLink\" href=\"(.+?)\">Original: King's Printer Version</a>", Pattern.CASE_INSENSITIVE
    );

    private static final String MODIFIED_LAWS_PAGE_URL_TEMPLATE =
            "https://www.legislation.gov.uk/changes/affecting/%s?results-count=10000";

    private static final DateTimeFormatter SITE_DATE_FORMAT = DateTimeFormatter.ofPattern("d LLLL yyyy", Locale.ENGLISH);

    @Autowired
    private PrimaryKeyGeneratingRepository legislativeRecordRepository;

    @Autowired
    private PageSourceRepository pageSourceRepository;

    @Autowired
    private RawSourceCollector rawSourceCollector;

    @Autowired
    private DocumentDownloader pdfDownloader;

    @Autowired
    private BillStatusScraper billStatusScraper;

    @Autowired
    private UkAffectingLawsCalculator ukAffectingLawsCalculator;

    @Autowired
    private BillVersionsCollector billVersionsCollector;

    @Autowired
    private BillAndLawTextCollector billAndLawTextCollector;

    @Autowired
    private CommitteesDepthCollector committeesDepthCollector;

    @Autowired
    private StageDebateCollector stageDebateCollector;

    @Autowired
    private AmendmentTextDownloader amendmentTextDownloader;

    @Autowired
    private ImpactAssessmentTextDownloader impactAssessmentTextDownloader;

    @Autowired
    private EntityManager entityManager;

    @Override
    public void runScraping(List<String> args) {
        rawSourceCollector.collectBillLists();
        processStoredApiResponses();
        billStatusScraper.collectBillStatusFromSite();
        ukAffectingLawsCalculator.fillAffectingLaws();
        billAndLawTextCollector.collectTexts(Country.UK);
        committeesDepthCollector.collectCommitteesDepth();
        stageDebateCollector.collectStageDebateSizes();
        billVersionsCollector.collectBillVersionTexts();
        amendmentTextDownloader.downloadAmendmentTexts();
        impactAssessmentTextDownloader.downloadIaTexts();

        log.info("Processing finished");
    }

    public void processStoredApiResponses() {
        List<PageSource> storedSources = pageSourceRepository.findByCountryAndPageType(
                Country.UK, PageType.BILL_LIST_API_RESPONSE.name().toUpperCase()
        );

        for (PageSource source : storedSources) {
            JSONObject json = new JSONObject(source.getRawSource());
            toObjectStream(json.getJSONArray("items"))
                    .filter(bill -> !isBillStored(bill.getInt("billId")))
                    .map(this::processBillJson)
                    .forEach(this::persistEntity);
        }
    }

    private boolean isBillStored(int billId) {
        return legislativeRecordRepository.existsByBillIdAndCountry(String.valueOf(billId), Country.UK);
    }

    @Transactional
    public void collectLawTextUrls() {
        TypedQuery<LegislativeDataRecord> query = entityManager.createQuery(
                "SELECT r FROM LegislativeDataRecord r WHERE r.lawTextUrl IS NULL", LegislativeDataRecord.class);

        query
                .getResultStream()
                .peek(this::collectLawTextUrl)
                .peek(record -> {
                    if (record.getLawTextUrl() != null) {
                        log.info("Found law text URL for record: {}", record.getRecordId());
                    } else {
                        log.info("URL not found for record: {}", record.getRecordId());
                    }
                })
                .filter(record -> record.getLawTextUrl() != null)
                .forEach(record -> entityManager.merge(record));
    }

    public LegislativeDataRecord processSingleBill(String billApiUrl) {
        HttpResponse<JsonNode> response = Unirest.get(billApiUrl).asJson();
        return processBillJson(response.getBody().getObject());
    }

    private LegislativeDataRecord processBillJson(JSONObject json) {
        int billId = json.optInt("billId");

        LegislativeDataRecord result = new LegislativeDataRecord();
        result.setCountry(Country.UK);
        result.setBillId(String.valueOf(billId));
        result.setBillTitle(json.optString("shortTitle"));
        result.setBillPageUrl(CommonConstants.BILL_PAGE_TEMPLATE.formatted(billId));

        fillPassedStatus(result, json);
        fillOriginalLaw(result);
        fillStageDates(result);
        addDataFromBillDetails(result);
        addDataFromPublications(result);
        addModifiedLawIds(result);

        return result;
    }

    private void fillPassedStatus(LegislativeDataRecord result, JSONObject json) {
        if (json.optBoolean("isAct")) {
            result.setBillStatus(BillStatus.PASS);
        }

        JSONObject currentStage = json.optJSONObject("currentStage");

        if (currentStage != null) {
            if ("Royal Assent".equals(currentStage.optString("description"))) {
                result.getCountrySpecificVariables().setHasRoyalAssent(true);
            }
        }
    }

    private void fillOriginalLaw(LegislativeDataRecord record) {
        boolean amendmentBill = record.getBillTitle()
                .toLowerCase()
                .replace("(", "")
                .replace(")", "")
                .contains("amendment bill");

        record.setOriginalLaw(!amendmentBill);
    }

    private void fillStageDates(LegislativeDataRecord record) {
        HttpResponse<JsonNode> stagesResp =
                Unirest.get(String.format(LIST_STAGES_ENDPOINT, record.getBillId())).asJson();

        if (stagesResp.isSuccess()) {
            JSONArray stages = stagesResp.getBody().getObject().optJSONArray("items");

            List<JSONObject> sortedStages = toObjectStream(stages)
                    .sorted(comparing(json -> json.optString("sortOrder")))
                    .toList();

            record.setStagesCount(sortedStages.size());

            for (JSONObject stage : sortedStages) {
                LegislativeStage stageEntity = buildStageObject(stage);

                if (stageEntity.getDate() != null) {
                    record.getStages().add(stageEntity);
                }

                switch (stage.optInt("stageId")) {
                    case STAGE_ID_COMMONS_COMMITTEE -> {
                        Optional<LocalDate> firstSitting = toObjectStream(stage.optJSONArray("stageSittings"))
                                .map(sitting -> parseUkDate(sitting.optString("date")))
                                .min(naturalOrder());

                        firstSitting.ifPresent(record::setCommitteeDate);
                    }
                    case STAGE_ID_MONEY_RESOLUTION -> record.getCountrySpecificVariables().setHasMoneyResolution(true);
                    case STAGE_ID_PROGRAMME_MOTION -> record.getCountrySpecificVariables().setHasProgrammeMotion(true);
                    case STAGE_ID_WAYS_AND_MEANS_RESOLUTION -> record.getCountrySpecificVariables().setHasWaysAndMeansResolution(true);
                    case STAGE_ID_ROYAL_ASSENT -> record.getCountrySpecificVariables().setHasRoyalAssent(true);
                }
            }

            Optional<LocalDate> earliestStageDate = record.getStages().stream()
                    .map(LegislativeStage::getDate)
                    .min(comparing(Function.identity()));

            earliestStageDate.ifPresent(record::setDateIntroduction);

            if (record.getBillStatus() == null) {
                record.setBillStatus(
                        record.getCountrySpecificVariables().getHasRoyalAssent() ? BillStatus.PASS : BillStatus.REJECT
                );
            }

            if (record.getBillStatus() == BillStatus.PASS) {
                //set date passing
                JSONObject lastSage = sortedStages.get(sortedStages.size() - 1);

                if ("Royal Assent".equals(lastSage.optString("description"))) {
                    JSONObject secondToLastStage = sortedStages.get(sortedStages.size() - 2);

                    Optional<LocalDate> lastSitting = toObjectStream(secondToLastStage.optJSONArray("stageSittings"))
                            .map(sittingObj -> parseUkDate(sittingObj.optString("date")))
                            .max(Comparator.naturalOrder());

                    lastSitting.ifPresent(record::setDatePassing);
                }

                if (record.getDatePassing() == null) {
                    getDatePassingFromSite(record);
                }
            }

            getProcedureType(stages).ifPresent(record::setProcedureTypeStandard);
        }
    }

    private void getDatePassingFromSite(LegislativeDataRecord record) {
        //get from webpage if it was not available on the API
        String billPageUrl = String.format(CommonConstants.BILL_PAGE_TEMPLATE, record.getBillId()) + "/stages";

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.setBinary(Constants.CHROME_LOCATION);
        ChromeDriver chromeDriver = new ChromeDriver(chromeOptions);

        try {
            chromeDriver.get(billPageUrl);
            SeleniumUtils.checkCaptcha(chromeDriver);

            List<WebElement> cards = chromeDriver.findElements(By.cssSelector("div.card-inner"));

            if (cards.size() > 1) {
                WebElement secondToLastCard = cards.get(1);
                List<WebElement> dateDivs = secondToLastCard.findElements(By.cssSelector("div.indicators-left"));

                if (dateDivs.size() == 1) {
                    String dateStr = dateDivs.get(0).getText();
                    String cleanDate = dateStr.replace("From", "").trim();
                    LocalDate parsed = LocalDate.parse(cleanDate, SITE_DATE_FORMAT);
                    record.setDatePassing(parsed);
                }
            }
        } catch (Exception e) {
            log.error("", e);
        } finally {
            chromeDriver.close();
        }
    }

    private Optional<LegislativeDataRecord.ProcedureType> getProcedureType(JSONArray stages) {
        LocalDate secondReading = null;
        LocalDate thirdReading = null;

        for (int i = 0; i < stages.length() && (secondReading == null || thirdReading == null); i++) {
            JSONObject stage = stages.getJSONObject(i);

            if ("2nd reading".equals(stage.optString("description")) && "Commons".equals(stage.optString("house"))) {
                Optional<LocalDate> min = toObjectStream(stage.getJSONArray("stageSittings"))
                        .map(s -> parseUkDate(s.optString("date")))
                        .min(naturalOrder());

                if (min.isPresent()) {
                    secondReading = min.get();
                }
            }

            if ("3rd reading".equals(stage.optString("description")) && "Commons".equals(stage.optString("house"))) {
                Optional<LocalDate> min = toObjectStream(stage.getJSONArray("stageSittings"))
                        .map(s -> parseUkDate(s.optString("date")))
                        .min(naturalOrder());

                if (min.isPresent()) {
                    thirdReading = min.get();
                }
            }
        }

        if (secondReading != null && thirdReading != null) {
            LegislativeDataRecord.ProcedureType type =
                    secondReading.equals(thirdReading)
                    ? LegislativeDataRecord.ProcedureType.EXCEPTIONAL
                            : LegislativeDataRecord.ProcedureType.REGULAR;

            return Optional.of(type);
        }

        return Optional.empty();
    }

    private LegislativeStage buildStageObject(JSONObject json) {
        LegislativeStage stage = new LegislativeStage();
        stage.setStageNumber(json.optInt("sortOrder"));
        stage.setName(parseStageName(json));

        Optional<LocalDate> firstSittingDate = jsonArrayToObjectList(json.optJSONArray("stageSittings"))
                .stream()
                .map(s -> s.optString("date"))
                .map(DateUtils::parseUkDate)
                .sorted()
                .findFirst();

        firstSittingDate.ifPresent(stage::setDate);

        return stage;
    }

    private String parseStageName(JSONObject json) {
        String stageName;

        if (json.isNull("house") || json.getString("house").equals("Unassigned")) {
            stageName = json.optString("description");
        } else {
            stageName = String.format("%s %s", json.optString("house"), json.optString("description"));
        }

        return stageName;
    }

    private void addDataFromBillDetails(LegislativeDataRecord entity) {
        String apiEndpoint = String.format(BILL_DETAILS_ENDPOINT, entity.getBillId());
        HttpResponse<JsonNode> billDetailsResp = Unirest.get(apiEndpoint).asJson();

        if (billDetailsResp.isSuccess()) {
            RawPageSource rawPageSource = new RawPageSource();
            rawPageSource.setUrl(apiEndpoint);
            rawPageSource.setRawSource(billDetailsResp.getBody().toString());
            entity.setRawPageSource(rawPageSource);

            //origin type
            int billTypeId = billDetailsResp.getBody().getObject().optInt("billTypeId");

            if (billTypeId == GOVERNMENT_BILL_TYPE_ID) {
                entity.setOriginType(OriginType.GOVERNMENT);
            } else if (INDIVIDUAL_MP_BILL_TYPE_IDS.contains(billTypeId)) {
                entity.setOriginType(OriginType.INDIVIDUAL_MP);
            } else if (billTypeId == PRIVATE_BILL_TYPE_ID) {
                entity.setOriginType(OriginType.PRIVATE);
            } else {
                entity.getErrors().add("Unhandled bill type ID: " + billTypeId);
            }

            //originators
            JSONArray sponsorsArray = billDetailsResp.getBody().getObject().optJSONArray("sponsors");

            List<Originator> originators = toObjectStream(sponsorsArray)
                    .map(this::buildOriginator)
                    .collect(Collectors.toList());

            entity.setOriginators(originators);
        } else {
            entity.getErrors().add("Failed to get bill details");
        }
    }

    private Originator buildOriginator(JSONObject sponsorObj) {
        String sponsorName = null;
        String party = null;
        String organisation = null;

        if (!sponsorObj.isNull("member")) {
            JSONObject member = sponsorObj.getJSONObject("member");

            sponsorName = member.getString("name");
            party = member.optString("party");
        }

        if (!sponsorObj.isNull("organisation")) {
            organisation = sponsorObj.getJSONObject("organisation").optString("name");
        }

        return new Originator(sponsorName, firstNonNull(organisation, party));
    }

    private void addDataFromPublications(LegislativeDataRecord dataRecord) {
        HttpResponse<JsonNode> publicationsResp =
                Unirest.get(String.format(LIST_PUBLICATIONS_ENDPOINT, dataRecord.getBillId())).asJson();

        if (publicationsResp.isSuccess()) {
            JSONArray publicationsArray = publicationsResp.getBody().getObject().optJSONArray("publications");

            List<JSONObject> publicationsSorted = toObjectStream(publicationsArray)
                    .sorted(comparing(pubJson -> parseUkDate(pubJson.optString("displayDate"))))
                    .toList();

            //get commons committee names
            boolean publicBillCommittee = publicationsSorted.stream()
                    .filter(this::isCommonsProceedingPublication)
                    .map(pub -> pub.getString("title"))
                    .anyMatch(title -> title.contains("Public Bill Committee"));

            boolean wholeHouseCommittee = publicationsSorted.stream()
                    .filter(this::isCommonsProceedingPublication)
                    .map(pub -> pub.getString("title"))
                    .anyMatch(title -> title.contains("Committee of the Whole House"));

            if (wholeHouseCommittee) {
                dataRecord.setCommittees(Collections.singletonList(Committee.WHOLE_HOUSE));
            } else if (publicBillCommittee) {
                dataRecord.setCommittees(Collections.singletonList(Committee.PUBLIC_BILL_COMMITTEE));
            }

            //get introduction date, bill text
            List<JSONObject> billPublicationsSorted = publicationsSorted
                    .stream()
                    .filter(this::isBillPublication)
                    .collect(Collectors.toList());

            JSONObject originalPublication = !billPublicationsSorted.isEmpty() ? billPublicationsSorted.get(0) : null;

            addDataFromOrigPublication(dataRecord, originalPublication);

            if (dataRecord.getDateIntroduction() == null) {
                dataRecord.getStages().stream()
                        .filter(stage -> stage.getStageNumber().equals(1))
                        .map(LegislativeStage::getDate)
                        .findFirst()
                        .ifPresent(dataRecord::setDateIntroduction);
            }

            dataRecord.setBillVersions(getBillVersions(billPublicationsSorted));

            //get amendments
            List<Amendment> amendments = publicationsSorted.stream()
                    .filter(pub -> pub.optJSONObject("publicationType").optInt("id") == PUBLICATION_TYPE_AMENDMENT)
                    .filter(pub -> !pub.optString("title").startsWith("Notices of"))
                    .map(pubJson -> buildAmendmentEntity(pubJson, dataRecord))
                    .collect(Collectors.toList());

            dataRecord.setAmendments(amendments);
            dataRecord.setAmendmentCount(amendments.size());

            //get IAs
            List<ImpactAssessment> impactAssessments = publicationsSorted.stream()
                    .filter(pub -> pub.optJSONObject("publicationType").optInt("id") == PUBLICATION_TYPE_IMPACT_ASSESSMENT)
                    .map(pubJson -> buildImpactAssessmentEntity(pubJson, dataRecord))
                    .collect(Collectors.toList());

            if (!impactAssessments.isEmpty()) {
                dataRecord.setImpactAssessmentDone(Boolean.TRUE);
                dataRecord.setImpactAssessments(impactAssessments);
            } else {
                dataRecord.setImpactAssessmentDone(Boolean.FALSE);
            }

            collectLawTextUrl(dataRecord);
        } else {
            dataRecord.getErrors().add("Failed to list publications - can not get bill text");
        }
    }

    private void collectLawTextUrl(LegislativeDataRecord record) {
        HttpResponse<JsonNode> publicationsResp =
                Unirest.get(String.format(LIST_PUBLICATIONS_ENDPOINT, record.getBillId())).asJson();

        if (publicationsResp.isSuccess()) {
            //get acts
            JSONArray publications = publicationsResp.getBody().getObject().optJSONArray("publications");

            List<JSONObject> actLinks = toObjectStream(publications)
                    .filter(pub -> pub.optJSONObject("publicationType").optInt("id") == PUBLICATION_TYPE_ACT_OF_PARLIAMENT)
                    .flatMap(pub -> toObjectStream(pub.optJSONArray("links")))
                    .toList();

            if (!actLinks.isEmpty()) {
                //collect html and pdf links separately
                Map<String, List<String>> linksByContentType = actLinks.stream()
                        .map(linkObj -> linkObj.optString("url"))
                        .collect(Collectors.groupingBy(this::classifyLink));

                if (linksByContentType.containsKey("html")) {
                    linksByContentType.get("html").stream()
                            .map(this::parseActIdFromUrl)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .findFirst()
                            .ifPresent(record::setLawId);
                }

                //get law text
                if (linksByContentType.containsKey("pdf")) {
                    String pdfUrl = linksByContentType.get("pdf").get(0);
                    record.setLawTextUrl(pdfUrl);
                } else if (linksByContentType.containsKey("html")) {
                    String pageUrl = linksByContentType.get("html").get(0);
                    Optional<String> lawTextUrl = getBillUrlTextFromPage(pageUrl);

                    lawTextUrl.ifPresent(record::setLawTextUrl);
                }
            }
        }
    }

    private boolean isCommonsProceedingPublication(JSONObject pubJson) {
        return !pubJson.isNull("publicationType")
                && !pubJson.getJSONObject("publicationType").isNull("id")
                && pubJson.getJSONObject("publicationType").getInt("id") == PUBLICATION_TYPE_BILL_PROCEEDINGS_COMMONS;
    }

    private List<BillVersion> getBillVersions(List<JSONObject> billPublicationsSorted) {
        if (billPublicationsSorted.size() > 1) {
            //has updated bill versions
            return billPublicationsSorted
                    .stream()
                    .skip(1)
                    .filter(pub -> pub.isNull("title") || !pub.optString("title").contains("as introduced"))
                    .map(this::mapToBillVersion)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private BillVersion mapToBillVersion(JSONObject publicationObj) {
        BillVersion billVersion = new BillVersion();

        BillVersion.House house = switch (publicationObj.optString("house")) {
            case "Commons" -> BillVersion.House.LOWER;
            case "Lords" -> BillVersion.House.UPPER;
            default -> null;
        };

        billVersion.setHouse(house);

        Optional<String> pdfLink = getPdfLink(publicationObj);
        pdfLink.ifPresent(billVersion::setTextSourceUrl);

        return billVersion;
    }

    private Amendment buildAmendmentEntity(JSONObject pubJson, LegislativeDataRecord dataRecord) {
        Amendment amendment = new Amendment();
        amendment.setDataRecord(dataRecord);
        amendment.setAmendmentId(String.valueOf(pubJson.optInt("id")));

        getPdfLink(pubJson).ifPresent(amendment::setTextSourceUrl);

        return amendment;
    }

    private Optional<String> getPdfLink(JSONObject publicationJson) {
        return toObjectStream(publicationJson.optJSONArray("links"))
                .map(linkObj -> linkObj.optString("url"))
                .filter(Objects::nonNull)
                .filter(url -> url.endsWith(".pdf"))
                .findFirst();
    }

    private void addDataFromOrigPublication(LegislativeDataRecord record, JSONObject originalPublication) {
        if (originalPublication != null) {
            if (record.getDateIntroduction() == null) {
                record.setDateIntroduction(parseUkDate(originalPublication.optString("displayDate")));
            }

            getPdfLink(originalPublication).ifPresent(record::setBillTextUrl);
        }

        if (record.getBillTextUrl() == null) {
            //go to website for pdf
            String pubsPage = CommonConstants.BILL_PUBLICATIONS_PAGE_TEMPLATE.formatted(record.getBillId());

            ChromeOptions chromeOptions = new ChromeOptions();
            chromeOptions.setBinary(Constants.CHROME_LOCATION);
            ChromeDriver chromeDriver = new ChromeDriver(chromeOptions);

            try {
                chromeDriver.get(pubsPage);
                SeleniumUtils.checkCaptcha(chromeDriver);

                List<WebElement> billsDiv =
                        chromeDriver.findElements(By.cssSelector("div[id^='collapse-publication-bill']"));

                if (billsDiv.size() == 1) {
                    List<WebElement> cards = billsDiv.get(0).findElements(By.cssSelector("div.card-inner"));

                    cards.stream()
                            .filter(card -> getText(card).contains("as introduced") || getText(card).contains("as deposited"))
                            .map(card -> card.findElements(By.tagName("a")))
                            .flatMap(List::stream)
                            .filter(link -> getText(link).contains("PDF"))
                            .map(link -> link.getAttribute("href"))
                            .findFirst()
                            .ifPresent(record::setBillTextUrl);
                }
            } catch (Exception e) {
                log.error("Failed to process page: " + pubsPage, e);
            } finally {
                chromeDriver.close();
            }
        }
    }

    private String classifyLink(String url) {
        if (url.endsWith(".pdf")) {
            return "pdf";
        }

        return "html";
    }

    private Optional<String> parseActIdFromUrl(String actUrl) {
        Matcher matcher = ACT_ID_REGEX.matcher(actUrl);

        if (matcher.find()) {
            return Optional.of(matcher.group());
        } else {
            Unirest.config().reset();
            Unirest.config().followRedirects(false);
            HttpResponse<byte[]> httpResponse = Unirest.get(actUrl).asBytes();
            Unirest.config().reset();
            Unirest.config().followRedirects(true);

            if (httpResponse.getStatus() == HttpStatus.MOVED_PERMANENTLY && httpResponse.getHeaders().containsKey(HttpHeaders.LOCATION)) {
                return ACT_ID_REGEX.matcher(httpResponse.getHeaders().get(HttpHeaders.LOCATION).get(0))
                        .results()
                        .map(MatchResult::group)
                        .findFirst();
            }
        }

        return Optional.empty();
    }

    private Optional<String> getBillUrlTextFromPage(String htmlUrl) {
        try {
            HttpResponse<String> stringHttpResponse = Unirest.get(htmlUrl).asString();
            Matcher matcher = PDF_LINK_REGEX.matcher(stringHttpResponse.getBody());

            if (stringHttpResponse.isSuccess() && matcher.find()) {
                String pdfRelativeUrl = matcher.group(1);
                String originalHost = getHost(htmlUrl);
                String pdfUrl = originalHost + pdfRelativeUrl;

                return Optional.of(pdfUrl);
            }
        } catch (UnirestException e) {
            log.error("Failed to get page: {}", htmlUrl);
        }

        return Optional.empty();
    }

    private String getHost(String htmlUrl) {
        try {
            URL url = new URL(htmlUrl);
            return url.getProtocol() + "://" + url.getHost();
        } catch (MalformedURLException e) {
            log.error("Failed to parse URL", e);
        }

        return null;
    }

    private ImpactAssessment buildImpactAssessmentEntity(JSONObject iaPublication, LegislativeDataRecord originalBill) {
        ImpactAssessment result = new ImpactAssessment();

        result.setDataRecord(originalBill);
        result.setTitle(iaPublication.optString("title"));
        result.setDate(parseUkDate(iaPublication.optString("displayDate")));

        getPdfLink(iaPublication).ifPresent(result::setOriginalUrl);

        return result;
    }

    private boolean isBillPublication(JSONObject publicationObj) {
        JSONObject publicationType = publicationObj.optJSONObject("publicationType");

        return publicationType != null
                && Objects.equals(publicationType.optInt("id"), PUBLICATION_TYPE_BILL);
    }

    private void addModifiedLawIds(LegislativeDataRecord entity) {
        if (entity.getLawId() == null || entity.getBillStatus() != BillStatus.PASS) {
            return;
        }

        String referencedLawsPageUrl = String.format(MODIFIED_LAWS_PAGE_URL_TEMPLATE, entity.getLawId());

        try {
            Document document = Jsoup.connect(referencedLawsPageUrl).get();
            Elements tables = document.body().getElementsByTag("table");

            if (tables.isEmpty()) {
                entity.setModifiedLawsCount(0);
                log.debug("No referenced laws found on page: " + referencedLawsPageUrl);

                return;
            }

            Elements rows = tables.first().getElementsByTag("tbody").first().getElementsByTag("tr");

            Set<String> modifiedLawIds = rows.stream()
                    .map(row -> row.getElementsByTag("td").get(1))
                    .map(td -> td.getElementsByTag("a").first().attr("href"))
                    .map(link -> {
                        Matcher matcher = UkDataCollector.ACT_ID_REGEX.matcher(link);
                        if (matcher.find()) {
                            return matcher.group();
                        }

                        entity.getErrors().add("No law ID parsed from link: " + link);

                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            entity.setModifiedLaws(modifiedLawIds);
            entity.setModifiedLawsCount(modifiedLawIds.size());

            if (!modifiedLawIds.isEmpty()) {
                entity.setOriginalLaw(false);
            }
        } catch (IOException e) {
            String errorMsg =
                    "Failed to get referenced law IDs from page: " + referencedLawsPageUrl + ", reason" + e.getMessage();
            log.error(errorMsg, e);

            entity.getErrors().add(errorMsg);
        }
    }

    private void persistEntity(LegislativeDataRecord entity) {
        try {
            legislativeRecordRepository.save(entity);
            log.info("Persisted bill with ID " + entity.getBillId());
        } catch (Exception e) {
            log.error("Error when persisting bill " + entity.getBillId(), e);
        }
    }

    @SneakyThrows
    @Transactional
    public void copyDebateSizesFromFile() {
        String commDebatesVar = "committees_depth";
        String plenarySizeVar = "size_of_plenary_debates_accumulated";
        String filePath = "db-export/uk_11-17/legislative_data_records-with-text.csv";

        CSVFormat parser = CSVFormat.RFC4180.withNullString("").withFirstRecordAsHeader();

        Stream<CSVRecord> nonEmptyRecords = parser.parse(new FileReader(filePath))
                .stream()
                .filter(line -> !"".equals(line.get(plenarySizeVar)) || !"".equals(line.get(commDebatesVar)) || !"".equals(line.get("bill_text")));

        nonEmptyRecords.forEach(record -> {
            Optional<LegislativeDataRecord> storedBill =
                    legislativeRecordRepository.findByCountryAndBillId(Country.UK, record.get("bill_id"));

            storedBill.ifPresent(bill -> {
                Integer plenarySize = record.get(plenarySizeVar) != null ? Integer.valueOf(record.get(plenarySizeVar)) : null;
                Integer committeeDepth = record.get(commDebatesVar) != null ? Integer.valueOf(record.get(commDebatesVar)) : null;

                bill.setPlenarySize(plenarySize);
                bill.setCommitteeDepth(committeeDepth);

                String billTextInCsv = record.get("bill_text");
                if (bill.getBillText() == null && billTextInCsv != null) {
                    bill.setBillText(billTextInCsv);
                }

                String lawTextInCsv = record.get("law_text");
                if (bill.getLawText() == null && lawTextInCsv != null) {
                    bill.setLawText(lawTextInCsv);
                    log.info("Set law text for bill {}", bill.getRecordId());
                }

                log.info("Set plenary size: {}, committee depth: {} for bill {}",
                        bill.getPlenarySize(), bill.getCommitteeDepth(), bill.getBillId());
            });
        });
    }

}
