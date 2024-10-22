package com.precognox.ceu.legislative_data_collector.georgia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precognox.ceu.legislative_data_collector.ScrapingController;
import com.precognox.ceu.legislative_data_collector.common.BillAndLawTextCollector;
import com.precognox.ceu.legislative_data_collector.common.SequentialIdMapper;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.georgia.json.BillList;
import com.precognox.ceu.legislative_data_collector.georgia.json.BillListItem;
import com.precognox.ceu.legislative_data_collector.georgia.json.CommitteeListItem;
import com.precognox.ceu.legislative_data_collector.georgia.json.ReviewListItem;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.JsonUtils;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.text.MessageFormat.format;

@Slf4j
@Service
public class GeDataCollectorV2 implements ScrapingController {

    @Autowired
    private LegislativeDataRepository legislativeDataRepository;

    private static String START_PAGE = "https://parliament.ge/legislation/find-legislation";
    private static String BILL_LIST_API_URL_TEMPLATE = "https://web-api.parliament.ge/api/bills?page={0}";
    private static String BILL_API_URL_TEMPLATE = "https://info.parliament.ge/law/1/bill/{0}";
    private static String BILL_URL_TEMPLATE = "https://parliament.ge/legislation/{0}";

    private static final String BILL_REVIEWS_URL_TEMPLATE = "https://info.parliament.ge/law/1/bill/{0}/billReview";
    private static final String BILL_REVIEW_CONTENT_URL_TEMPLATE =
            "https://info.parliament.ge/law/1/billReview/{0}/billReviewContent";
    private static final String BILL_REVIEW_COMMITTEES_URL_TEMPLATE = "https://info.parliament.ge/law/1/billReview/{0}/billCommittee";
    private static final String FILE_URL_TEMPLATE = "https://info.parliament.ge/file/1/BillReviewContent/{0}";

    public static final String INITIATION_STAGE_NAME = "ინიციირებული ვარიანტი";
    public static final String LAW_STAGE_NAME = "კანონი";

    @Autowired
    private BillAndLawTextCollector billAndLawTextCollector;

    @Autowired
    private PageSourceRepository pageSourceRepository;

    @Autowired
    private PrimaryKeyGeneratingRepository recordRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private SequentialIdMapper sequentialIdMapper;

    @Override
    @Transactional
    public void runScraping(List<String> args) {
        collectBillListRawData();
        processStoredBillListResponses();
        processAllReviews();
        downloadBillAndLawTexts();
        downloadPagesForAllBills();
        //processStoredPages();
        sequentialIdMapper.reassignIds(Country.GEORGIA);
    }

    public void collectBillListRawData() {
        log.info("Collecting API responses for Georgia...");

        int maxPage = 1993;

        for (int currentPage = 1; currentPage < maxPage; currentPage++) {
            String url = format(BILL_LIST_API_URL_TEMPLATE, String.valueOf(currentPage));

            if (pageSourceRepository.existsByPageUrl(url)) {
                log.info("Skipping downloaded page: {}", url);
            } else {
                HttpResponse<String> resp = Unirest.get(url)
                        .header(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType())
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .asString();

                if (resp.isSuccess()) {
                    PageSource pageSource = new PageSource();
                    pageSource.setRawSource(resp.getBody());
                    pageSource.setPageUrl(url);
                    pageSource.setCountry(Country.GEORGIA);

                    transactionTemplate.executeWithoutResult(
                            transactionStatus -> pageSourceRepository.save(pageSource));

                    log.info("Stored page: {}", url);
                } else {
                    log.error("Error response {} for URL {}", resp.getStatus(), url);
                }
            }
        }

        log.info("Finished collecting API responses");
    }

    public void downloadPagesForAllBills() {
        legislativeDataRepository.streamAll(Country.GEORGIA)
                .map(LegislativeDataRecord::getBillPageUrl)
                .filter(url -> !pageSourceRepository.existsByPageUrl(url))
                .forEach(this::downloadPage);
    }

    public void downloadBillAndLawTexts() {
        billAndLawTextCollector.collectTexts(Country.GEORGIA);
    }

    private void downloadPage(String url) {
        HttpResponse<String> resp = Unirest.get(url).asString();

        if (resp.isSuccess()) {
            PageSource pageSource = new PageSource();
            pageSource.setCountry(Country.GEORGIA);
            pageSource.setPageUrl(url);
            pageSource.setPageType("BILL_PAGE");
            pageSource.setRawSource(resp.getBody());

            transactionTemplate.executeWithoutResult(transactionStatus -> pageSourceRepository.save(pageSource));

            log.info("Stored page: {} with size: {}", url, resp.getBody().length());
        } else {
            log.error("Error response ({}) for URL: {}", resp.getStatus(), url);
        }
    }

    @SneakyThrows
    private void processStoredBillListResponses() {
        log.info("Processing stored API responses...");

        List<PageSource> sources =
                pageSourceRepository.findAllByCountryAndPageType(Country.GEORGIA, "BILL_LIST_API_RESPONSE");

        ObjectMapper objectMapper = new ObjectMapper();

        for (PageSource source : sources) {
            BillList parsedList = objectMapper.readValue(source.getRawSource(), BillList.class);

            for (BillListItem item : parsedList.getData()) {
                String billPageUrl = format(BILL_URL_TEMPLATE, item.getId().toString());

                if (recordRepository.existsByBillPageUrl(billPageUrl)) {
                    log.info("Skipping bill: {}", billPageUrl);
                } else {
                    processBillJson(item, billPageUrl);
                }
            }
        }

        log.info("Finished processing stored API responses");
    }

    private void processBillJson(BillListItem item, String billPageUrl) {
        LegislativeDataRecord record = new LegislativeDataRecord();
        record.setCountry(Country.GEORGIA);
        record.setBillPageUrl(billPageUrl);
        record.setBillId(item.getNumber());
        record.setBillTitle(item.getTitle());
        record.setDateIntroduction(DateUtils.toLocalDate(item.getDate(), "dd-MM-yyyy"));

        if (StringUtils.isNotBlank(item.getAuthor())) {
            Originator originator = new Originator();
            originator.setName(item.getAuthor());
            record.getOriginators().add(originator);

            if (originator.getName().contains("პარლამენტის წევრი")) {
                record.setOriginType(OriginType.INDIVIDUAL_MP);
            } else {
                record.setOriginType(OriginType.GOVERNMENT);
            }
        }

        recordRepository.save(record);
    }

    public void processBillDetails(LegislativeDataRecord record) {
        String billDetailsUrl = format(BILL_URL_TEMPLATE, getApiId(record));

        HttpResponse<JsonNode> resp = Unirest.get(billDetailsUrl).asJson();

        if (resp.isSuccess()) {
            JSONObject detailsObj = resp.getBody().getArray().getJSONObject(0);
            record.setBillType(detailsObj.getJSONObject("billType").getString("name"));

            JSONObject billPackage = detailsObj.getJSONObject("billPackage");
            String dateIntro = billPackage.getString("registrationDate");
            record.setDateIntroduction(DateUtils.toLocalDate(dateIntro, "dd-MM-yyyy"));

            if ("true".equalsIgnoreCase(billPackage.getString("confirmedProcedure"))) {
                record.setProcedureTypeStandard(LegislativeDataRecord.ProcedureType.EXCEPTIONAL);
            } else {
                record.setProcedureTypeStandard(LegislativeDataRecord.ProcedureType.REGULAR);
            }
        }
    }

    public void processAllReviews() {
        legislativeDataRepository.streamAll(Country.GEORGIA)
                .parallel()
                .peek(this::processReviews)
                .peek(record -> log.info("Processed reviews for {}", record.getRecordId()))
                .forEach(record -> transactionTemplate.executeWithoutResult(status -> entityManager.merge(record)));

        log.info("Processed all reviews");
    }

    private void processReviews(LegislativeDataRecord record) {
        String billIdOnApi = getApiId(record);
        String reviewsUrl = format(BILL_REVIEWS_URL_TEMPLATE, billIdOnApi);
        HttpResponse<String> reviewsResp = Unirest.get(reviewsUrl).asString();

        if (reviewsResp.isSuccess()) {
            List<ReviewListItem> reviews =
                    JsonUtils.parseToObject(reviewsResp.getBody(), new TypeReference<>() {});
            List<Committee> committees = new ArrayList<>();
            List<LegislativeStage> stages = new ArrayList<>();

            for (ReviewListItem review : reviews) {
                int reviewId = review.getId();

                switch (review.getReviewTypeName()) {
                    case LAW_STAGE_NAME -> record.setLawTextUrl(getFileUrlFromReview(reviewId));
                    case INITIATION_STAGE_NAME -> record.setBillTextUrl(getFileUrlFromReview(reviewId));
                }

                stages.add(review.toEntity());
                committees.addAll(processCommittees(reviewId));
            }

            record.setStages(stages);
            record.setStagesCount(reviews.size());
            record.setCommittees(StreamEx.of(committees).distinct(Committee::getName).toList());
            record.setCommitteeCount(record.getCommittees().size());
        } else {
            log.error("Error response - {} for call: {}", reviewsUrl, reviewsResp.getStatus());
        }
    }

    @NotNull
    private static String getApiId(LegislativeDataRecord record) {
        return record.getBillPageUrl().substring(record.getBillPageUrl().lastIndexOf("/") + 1);
    }

    @Nullable
    private static String getFileUrlFromReview(Integer reviewId) {
        String reviewContentUrl = format(BILL_REVIEW_CONTENT_URL_TEMPLATE, reviewId.toString());
        HttpResponse<JsonNode> reviewContentResp = Unirest.get(reviewContentUrl).asJson();

        if (reviewContentResp.isSuccess() && reviewContentResp.getBody().isArray()) {
            JSONArray filesArray = reviewContentResp.getBody().getArray();

            if (!filesArray.isEmpty()) {
                Integer fileId = filesArray.getJSONObject(0).getInt("id");

                return format(FILE_URL_TEMPLATE, fileId.toString());
            }
        } else {
            log.error("Error response {} for URL: {}", reviewContentResp.getStatus(), reviewContentUrl);
        }

        return null;
    }

    public List<Committee> processCommittees(Integer reviewId) {
        HttpResponse<String> committeesResp =
                Unirest.get(format(BILL_REVIEW_COMMITTEES_URL_TEMPLATE, reviewId.toString())).asString();

        if (committeesResp.isSuccess()) {
            List<CommitteeListItem> committeesJsons =
                    JsonUtils.parseToObject(committeesResp.getBody(), new TypeReference<>() {});

            return committeesJsons.stream()
                    .map(CommitteeListItem::toEntity)
                    .toList();
        }

        return Collections.emptyList();
    }

}
