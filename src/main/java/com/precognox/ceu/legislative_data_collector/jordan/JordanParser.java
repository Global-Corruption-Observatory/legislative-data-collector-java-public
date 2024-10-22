package com.precognox.ceu.legislative_data_collector.jordan;

import com.jayway.jsonpath.DocumentContext;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.jo.JoCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.utils.JsonPathUtils;
import com.precognox.ceu.legislative_data_collector.utils.JsonUtils;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import com.precognox.ceu.legislative_data_collector.utils.XmlUtils;
import kong.unirest.GetRequest;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.util.Pair;

import java.time.LocalDate;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.precognox.ceu.legislative_data_collector.jordan.JoDataCollector.*;
import static com.precognox.ceu.legislative_data_collector.utils.ParserHelper.getStringValue;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

@Slf4j
public class JordanParser {

    public static final String IS_MOD_TRUE = ",isMod:true";

    public static Pair<LegislativeDataRecord, PageSource> parseRowData(
            PageSource pageSource, BiFunction<List<Map<String, Object>>, String, List<PageSource>> downloadModifiedLaws) {
        LegislativeDataRecord data = new LegislativeDataRecord();
        data.setCountry(Country.JORDAN);
        Unirest.config().verifySsl(false);

        Pair<LegislativeDataRecord, PageSource> pair = Pair.of(data, pageSource);
        boolean isPageSourceChanged = false;

        DocumentContext context = JsonPathUtils.parseJson(pageSource.getRawSource());
        Map<String, Object> rawMap = JsonPathUtils.findByJsonPath(context, "$");

        Map<String, Object> listItem = (Map<String, Object>) rawMap.get("listItem");
        String legislationID = getStringValue(listItem, "pmk_ID");
        String legislationType = getStringValue(listItem, "Type");
        String isMod = getStringValue(listItem, "isMod");

        String url = DETAILS_URL_TEMPLATE
                .replace("LEGISLATION_ID", legislationID)
                .replace("LEGISLATION_TYPE", legislationType);

        if (isMod.equalsIgnoreCase(Boolean.TRUE.toString())) {
            url += IS_MOD_TRUE;
            data.setOriginalLaw(false);
        } else {
            data.setOriginalLaw(true);
        }

        data.setBillPageUrl(url);
        data.setLawTextUrl(url);

        data.setBillId(getStringValue(listItem, "pmk_ID"));
        data.setBillTitle(getStringValue(listItem, "Name", "").replaceAll("\u0000", ""));
        data.setBillStatus(null);

        JoCountrySpecificVariables jordanCountrySpecificVariables = new JoCountrySpecificVariables();
        jordanCountrySpecificVariables.setLegislativeDataRecord(data);
        data.setJordanCountrySpecificVariables(jordanCountrySpecificVariables);

//        JoCountrySpecificVariables jordanCountrySpecificVariables = data.getJordanCountrySpecificVariables();
//        data.setJordanCountrySpecificVariables(jordanCountrySpecificVariables);

        String legStatus = getStringValue(listItem, "Status_AR");
        jordanCountrySpecificVariables.setLegStatus(legStatus);

        DocumentContext detailsContext = JsonPathUtils.parseJson(Objects.toString(rawMap.get("details")));
        String magazineDate = JsonPathUtils.findByJsonPath(detailsContext, "$.Value.Legislation..Magazine_Date");
        data.setDatePassing(toLocalDate(magazineDate));

        List<Map<String, Object>> articles =
                JsonPathUtils.findByJsonPath(detailsContext, "$.Value.Articles", Collections.EMPTY_LIST);
        data.setLawText(articles.stream().map(m -> toArticle(m)).collect(Collectors.joining(",")));

        data.setDatePassing(toLocalDate(JsonPathUtils.findText(detailsContext, "$.Value.Legislation..Magazine_Date")));
        data.setDateEnteringIntoForce(toLocalDate(JsonPathUtils.findText(detailsContext, "$.Value.Legislation..Active_Date")));

        String categoryId = legislationType;
        if (categoryId != null) {
            //category 2=Law; 3=System
            String category = categoryId.equals("2") ? "Law" : "System";
            jordanCountrySpecificVariables.setCategory(category);
        }

        String modifiedValueJson = Objects.toString(rawMap.get("modifiedValue"), null);

        if (StringUtils.isNotBlank(modifiedValueJson)) {
            DocumentContext modifiedContext = JsonPathUtils.parseJson(modifiedValueJson);
            List<Map<String, Object>> modifiedValue =
                    JsonPathUtils.findByJsonPath(modifiedContext, "$.Value", Collections.EMPTY_LIST);

            int affectingLawsCount = modifiedValue.size();
            data.setAffectingLawsCount(affectingLawsCount);

            if (!modifiedValue.isEmpty()) {
                Optional<List<String>> affectedLaws = Optional.ofNullable((List<String>)rawMap.get("affectedLaws"));

                if (affectedLaws.isPresent()) {
//                        && modifiedValue.size() == affectedLaws.get().size()) {
                    data.getModifiedLaws().addAll(affectedLaws.get());

                    Optional.ofNullable((Number) rawMap.get("affectingLawsSizeTotal")).ifPresent(
                            number -> jordanCountrySpecificVariables.setAffectingLawsSizeTotal(number.intValue()));

                    Optional.ofNullable((Number) rawMap.get("affectingLawsTotalArticleCount")).ifPresent(
                            number -> jordanCountrySpecificVariables.setAffectingLawsTotalArticleCount(number.intValue()));
                }

                if (data.getModifiedLaws().isEmpty()) {
                    int affectingLawsSizeTotal = 0;
                    int affectingLawsTotalArticleCount = 0;

                    List<PageSource> affectedLawPageSourceList =
                            downloadModifiedLaws.apply(modifiedValue, pageSource.getPageUrl());

                    for (PageSource affectedLawsSource : affectedLawPageSourceList) {
                        DocumentContext affectedLawsContext = JsonPathUtils.parseJson(affectedLawsSource.getRawSource());
                        Map<String, Object> affectedLawRawMap = JsonPathUtils.findByJsonPath(affectedLawsContext, "$");
                        Map<String, Object> affectedLawListItem = (Map<String, Object>) affectedLawRawMap.get("listItem");
                        String affectedLawName = getStringValue(affectedLawListItem, "Name");
                        data.getModifiedLaws().add(affectedLawName);

                        DocumentContext affectedLawDetailsContext =
                                JsonPathUtils.parseJson(Objects.toString(affectedLawRawMap.get("details")));

                        List<Map<String, Object>> affectedLawArticles = JsonPathUtils.findByJsonPath(
                                affectedLawDetailsContext, "$.Value.Articles", Collections.EMPTY_LIST
                        );

                        String affectedLawText = affectedLawArticles.stream()
                                .map(JordanParser::toArticle)
                                .collect(Collectors.joining(","));

                        int affectedLawTextSize = TextUtils.getLengthWithoutWhitespace(affectedLawText);
                        affectingLawsSizeTotal += affectedLawTextSize;
                        affectingLawsTotalArticleCount += affectedLawArticles.size();
                    }

                    jordanCountrySpecificVariables.setAffectingLawsSizeTotal(affectingLawsSizeTotal);
                    jordanCountrySpecificVariables.setAffectingLawsTotalArticleCount(affectingLawsTotalArticleCount);

                    isPageSourceChanged = true;
                    rawMap.put("affectedLaws", data.getModifiedLaws());
                    rawMap.put("affectingLawsSizeTotal", affectingLawsSizeTotal);
                    rawMap.put("affectingLawsTotalArticleCount", affectingLawsTotalArticleCount);
                }
            } else {
                jordanCountrySpecificVariables.setAffectingLawsSizeTotal(0);
                jordanCountrySpecificVariables.setAffectingLawsTotalArticleCount(0);
            }

            Optional<Map<String, Object>> affectingLawsMap =
                    Optional.ofNullable(modifiedValue.isEmpty() ? null : modifiedValue.get(0));

            if (affectingLawsCount != 0 && affectingLawsMap.isPresent()) {
                String affectingLawsDetails;
                if (rawMap.containsKey("affectingLawsDetails")) {
                    affectingLawsDetails = Objects.toString(rawMap.get("affectingLawsDetails"));
                } else {
                    String affectingLegislationID = Objects.toString(affectingLawsMap.get().get("ModLeg"));
                    String affectingLawsLegislationType = Objects.toString(affectingLawsMap.get().get("ModLegType"));

                    GetRequest affectingLawsDetailsRequest = Unirest.get(LAWS_API_DETAILS_URL)
                            .queryString("LangID", 0)
                            .queryString("LegislationID", affectingLegislationID)
                            .queryString("LegislationType", affectingLawsLegislationType)
                            .queryString("isMod", true);

                    log.info("affectingLawsDetails:{}", affectingLawsDetailsRequest.getUrl());
                    affectingLawsDetails = affectingLawsDetailsRequest.asJson().getBody().toString();
                    rawMap.put("affectingLawsDetails", affectingLawsDetails);
                    isPageSourceChanged = true;
                }

                DocumentContext affectingLawsDetailsContext = JsonPathUtils.parseJson(affectingLawsDetails);
                String affectingLawsMagazineDate = JsonPathUtils.findByJsonPath(
                        affectingLawsDetailsContext, "$.Value.Legislation..Magazine_Date");

                data.setAffectingLawsFirstDate(toLocalDate(affectingLawsMagazineDate));
            }
        }

//        Related_total
        String relatedTotalPage = getRelatedTotalPage(rawMap, "-1", legislationID, legislationType);
        jordanCountrySpecificVariables.setRelatedTotal(getTotalResultFromJson(relatedTotalPage));
        String relatedRegulationTotalPage = getRelatedTotalPage(rawMap, "3", legislationID, legislationType);
        jordanCountrySpecificVariables.setRelatedRegulation(getTotalResultFromJson(relatedRegulationTotalPage));
        String relatedDirectionsTotalPage = getRelatedTotalPage(rawMap, "5", legislationID, legislationType);
        jordanCountrySpecificVariables.setRelatedDirections(getTotalResultFromJson(relatedDirectionsTotalPage));
        String relatedDecisionsTotalPage = getRelatedTotalPage(rawMap, "6", legislationID, legislationType);
        jordanCountrySpecificVariables.setRelatedDecisions(getTotalResultFromJson(relatedDecisionsTotalPage));
        String relatedExplanationTotalPage = getRelatedTotalPage(rawMap, "7", legislationID, legislationType);
        jordanCountrySpecificVariables.setRelatedExplanation(getTotalResultFromJson(relatedExplanationTotalPage));


//        if(isPageSourceChanged) {
            pageSource.setRawSource(JsonUtils.toString(rawMap));
//        }
        log.info("Finish parsing in BillId: {}", data.getBillId());

        return pair;
    }

    private static Integer getTotalResultFromJson(String json) {
        DocumentContext affectingLawsDetailsContext = JsonPathUtils.parseJson(json);
        Number totalResult = JsonPathUtils.findByJsonPath(affectingLawsDetailsContext, "$.Value.TotalResult");

        return totalResult != null ? totalResult.intValue() : 0;
    }

    private static String getRelatedTotalPage(
            Map<String, Object> rawMap, String searchType, Object legislationID, Object legislationType) {
        String relatedTotalPage;
        String mapKey = "relatedTotal" + searchType;

        if (rawMap.containsKey(mapKey)) {
            relatedTotalPage = Objects.toString(rawMap.get(mapKey));
        } else {
            String url = LAWS_API_RELATED_URL_TEMPLATE.replace("RELATED_LEGISLATION_TYPE", searchType);

            GetRequest relatedTotalRequest = Unirest.get(url)
                    .queryString("LangID", 0)
                    .queryString("LegislationID", legislationID)
                    .queryString("LegislationType", legislationType);

            log.info("getRelatedTotalPage:{}", relatedTotalRequest.getUrl());
            relatedTotalPage = relatedTotalRequest.asJson().getBody().toString();
            rawMap.put(mapKey, relatedTotalPage);
        }

        return relatedTotalPage;
    }

    private static String toArticle(Map<String, Object> m) {
        String text = XmlUtils.xmlToText(Objects.toString(m.get("Article"), ""));
        return String.format("Article %s\n%s\n\n", m.get("Article_Number"), text);
    }

    private static LocalDate toLocalDate(String date) {
        if (StringUtils.isBlank(date)) {
            return null;
        }

        return LocalDate.parse(date, ISO_LOCAL_DATE_TIME);
    }
}
