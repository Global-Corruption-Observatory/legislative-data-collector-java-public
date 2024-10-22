package com.precognox.ceu.legislative_data_collector.georgia;

import com.jayway.jsonpath.DocumentContext;
import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.ge.GeCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.utils.JsonPathUtils;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import com.precognox.ceu.legislative_data_collector.utils.XmlUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.precognox.ceu.legislative_data_collector.utils.DateUtils.toLocalDate;
import static com.precognox.ceu.legislative_data_collector.utils.ParserHelper.getStringValue;

@Slf4j
public class GeorgianParser {
    private static final int ALL_PARLIAMENT_MEMBERS = 150;

    public static LegislativeDataRecord parseRowData(PageSource data) {
        log.info("Start  parsing: {}", data.getPageUrl());
        DocumentContext context = JsonPathUtils.parseJson(data.getRawSource());
        Map<String, Object> rawMap = JsonPathUtils.findByJsonPath(context, "$");

        Map<String, Object> listItem = (Map<String, Object>) rawMap.get("listItem");
        String id = getStringValue(listItem, "id");

        LegislativeDataRecord record = new LegislativeDataRecord();
        record.setCountry(Country.GEORGIA);
//        record.addPageSource(data);
        record.setBillId(getStringValue(listItem, "number"));
        record.setBillTitle(getStringValue(listItem, "title"));

//        Origin_type/Originator_name/Originator_Affiliation
        String initiator = getStringValue(listItem, "initiator");
        if (initiator != null) {
            String[] initiatorArray = initiator.split(": ");
            if (initiatorArray[0].contains("პარლამენტის წევრი")) {
                record.setOriginType(OriginType.INDIVIDUAL_MP);
            } else {
                record.setOriginType(OriginType.GOVERNMENT);
            }
            if (initiatorArray.length > 1) {
                record.setOriginators(List.of(new Originator(initiatorArray[1], initiatorArray[0])));
            }
        }

//        Map<String, Object> details = (Map<String, Object>) rawMap.get("details");
//        Map<String, Object> detailsData = (Map<String, Object>) details.get("data");

        String procedureTypeStandardValue = "";
        String procedureTypeNationalValue = "";
        Map<String, Object> billsExtraInfo = (Map<String, Object>) rawMap.get("billsExtraInfo");
        if(billsExtraInfo != null) {
            Map<String, Object> billPackage = (Map<String, Object>) billsExtraInfo.get("billPackage");
            if(billPackage != null) {
                procedureTypeStandardValue = Objects.toString(billPackage.get("confirmedProcedure"), null);

                if(billPackage.containsKey("reviewProcedureType") && billPackage.get("reviewProcedureType") != null) {
                    Map<String, Object> reviewProcedureType = (Map<String, Object>) billPackage.get("reviewProcedureType");
                    if (reviewProcedureType.containsKey("id")) {
                        procedureTypeNationalValue = Objects.toString(reviewProcedureType.get("id"), "");
                    }
                }
            }
        }

        record.setProcedureTypeStandard(toProcedureTypeStandard(procedureTypeStandardValue));
        record.setProcedureTypeNational(toProcedureTypeNational(procedureTypeNationalValue));

        record.setBillType(getStringValue(listItem, "type"));
        List<Map<String, Object>> reviewList = (List<Map<String, Object>>) rawMap.get("bill_review");
        record.setBillStatus(toBillStatus(reviewList));

        record.setDateIntroduction(toLocalDate(getStringValue(listItem, "date"), "dd-MM-yyyy"));

        List<Map<String, Object>> billReviewList = (List<Map<String, Object>>) rawMap.get("bill_review");
        if (!billReviewList.isEmpty()) {
            Map<String, Object> lastBillReview = billReviewList.get(billReviewList.size() - 1);

            GeCountrySpecificVariables geCountrySpecificVariables = Optional.ofNullable(record.getGeCountrySpecificVariables()).orElse(new GeCountrySpecificVariables());
//            sessionReviewDate": "21-05-2020",
//                    "billReviewSubmitDate": "22-05-2020",
//                    "validationDate": "29-05-2020",
//                    "appearanceDate": "28-05-2020",
            record.setDatePassing(toLocalDate(getStringValue(lastBillReview, "sessionReviewDate"), "dd-MM-yyyy"));
            geCountrySpecificVariables.setDateOfPresidentReview(toLocalDate(getStringValue(lastBillReview, "billReviewSubmitDate"), "dd-MM-yyyy"));

            record.setDateEnteringIntoForce(toLocalDate(getStringValue(lastBillReview, "validationDate"), "dd-MM-yyyy"));
            geCountrySpecificVariables.setDateOfPresidentReview(toLocalDate(getStringValue(lastBillReview, "appearanceDate"), "dd-MM-yyyy"));

            geCountrySpecificVariables.setDocumentNumber(getStringValue(lastBillReview, "documentNumber"));

            List<Map<String, Object>> billReviewContentList = (List<Map<String, Object>>) lastBillReview.get("bill_ReviewContent");
            if (!billReviewContentList.isEmpty()) {
                Map<String, Object> lastBillReviewContent = billReviewContentList.get(billReviewContentList.size() - 1);

                //        Modified_law_id/Modified_law_count
//        https://info.parliament.ge/law/1/billReview/1912/billReviewContent
//        https://info.parliament.ge/file/1/BillReviewContent/208671
//                record.getAffvectedLaws();
//        record.setModifiedLawsCount();

                String lastBillReviewContentAsXml = XmlUtils.xmlToText(getStringValue(lastBillReviewContent, "contentAsXml"));
                List<String> articleList = TextUtils.findTexts(lastBillReviewContentAsXml, "მუხლი ([\\d]+[^\n]*)[\n]");
                record.setModifiedLaws(new HashSet<>(articleList));
                record.setModifiedLawsCount(articleList.size());

//        Committee name/date/role/count
                List<Committee> committees = record.getCommittees();
//        https://info.parliament.ge/law/1/billReview/78412{billReviewId}/billCommittee
//        {"data":[{"id":43712,"billCommitteeType":{"id":1,"name":"\u10ec\u10d0\u10db\u10e7\u10d5\u10d0\u10dc\u10d8 \u10d9\u10dd\u10db\u10d8\u10e2\u10d4\u10e2\u10d8"},"billId":24024,"structureNodeId":50934,"nodeName":"\u10ef\u10d0\u10dc\u10db\u10e0\u10d7\u10d4\u10da\u10dd\u10d1\u10d8\u10e1 \u10d3\u10d0\u10ea\u10d5\u10d8\u10e1\u10d0 \u10d3\u10d0 \u10e1\u10dd\u10ea\u10d8\u10d0\u10da\u10e3\u10e0 \u10e1\u10d0\u10d9\u10d8\u10d7\u10ee\u10d7\u10d0 \u10d9\u10dd\u10db\u10d8\u10e2\u10d4\u10e2\u10d8"},{"id":43713,"billCommitteeType":{"id":2,"name":"\u10d9\u10dd\u10db\u10d8\u10e2\u10d4\u10e2\u10d8 \u10e0\u10dd\u10db\u10da\u10d8\u10e1 \u10d3\u10d0\u10e1\u10d9\u10d5\u10dc\u10d0\u10ea \u10e1\u10d0\u10d5\u10d0\u10da\u10d3\u10d4\u10d1\u10e3\u10da\u10dd\u10d0"},"billId":24024,"structureNodeId":50921,"nodeName":"\u10d0\u10d3\u10d0\u10db\u10d8\u10d0\u10dc\u10d8\u10e1 \u10e3\u10e4\u10da\u10d4\u10d1\u10d0\u10d7\u10d0 \u10d3\u10d0\u10ea\u10d5\u10d8\u10e1\u10d0 \u10d3\u10d0 \u10e1\u10d0\u10db\u10dd\u10e5\u10d0\u10da\u10d0\u10e5\u10dd \u10d8\u10dc\u10e2\u10d4\u10d2\u10e0\u10d0\u10ea\u10d8\u10d8\u10e1 \u10d9\u10dd\u10db\u10d8\u10e2\u10d4\u10e2\u10d8"},{"id":43714,"billCommitteeType":{"id":2,"name":"\u10d9\u10dd\u10db\u10d8\u10e2\u10d4\u10e2\u10d8 \u10e0\u10dd\u10db\u10da\u10d8\u10e1 \u10d3\u10d0\u10e1\u10d9\u10d5\u10dc\u10d0\u10ea \u10e1\u10d0\u10d5\u10d0\u10da\u10d3\u10d4\u10d1\u10e3\u10da\u10dd\u10d0"},"billId":24024,"structureNodeId":50924,"nodeName":"\u10d3\u10d0\u10e0\u10d2\u10dd\u10d1\u10e0\u10d8\u10d5\u10d8 \u10d4\u10d9\u10dd\u10dc\u10dd\u10db\u10d8\u10d9\u10d8\u10e1\u10d0 \u10d3\u10d0 \u10d4\u10d9\u10dd\u10dc\u10dd\u10db\u10d8\u10d9\u10e3\u10e0\u10d8 \u10de\u10dd\u10da\u10d8\u10e2\u10d8\u10d9\u10d8\u10e1 \u10d9\u10dd\u10db\u10d8\u10e2\u10d4\u10e2\u10d8"},{"id":43715,"billCommitteeType":{"id":2,"name":"\u10d9\u10dd\u10db\u10d8\u10e2\u10d4\u10e2\u10d8 \u10e0\u10dd\u10db\u10da\u10d8\u10e1 \u10d3\u10d0\u10e1\u10d9\u10d5\u10dc\u10d0\u10ea \u10e1\u10d0\u10d5\u10d0\u10da\u10d3\u10d4\u10d1\u10e3\u10da\u10dd\u10d0"},"billId":24024,"structureNodeId":50927,"nodeName":"\u10d7\u10d0\u10d5\u10d3\u10d0\u10ea\u10d5\u10d8\u10e1\u10d0 \u10d3\u10d0 \u10e3\u10e8\u10d8\u10e8\u10e0\u10dd\u10d4\u10d1\u10d8\u10e1 \u10d9\u10dd\u10db\u10d8\u10e2\u10d4\u10e2\u10d8"},{"id":43716,"billCommitteeType":{"id":2,"name":"\u10d9\u10dd\u10db\u10d8\u10e2\u10d4\u10e2\u10d8 \u10e0\u10dd\u10db\u10da\u10d8\u10e1 \u10d3\u10d0\u10e1\u10d9\u10d5\u10dc\u10d0\u10ea \u10e1\u10d0\u10d5\u10d0\u10da\u10d3\u10d4\u10d1\u10e3\u10da\u10dd\u10d0"},"billId":24024,"structureNodeId":50928,"nodeName":"\u10d8\u10e3\u10e0\u10d8\u10d3\u10d8\u10e3\u10da \u10e1\u10d0\u10d9\u10d8\u10d7\u10ee\u10d7\u10d0 \u10d9\u10dd\u10db\u10d8\u10e2\u10d4\u10e2\u10d8"},{"id":43717,"billCommitteeType":{"id":2,"name":"\u10d9\u10dd\u10db\u10d8\u10e2\u10d4\u10e2\u10d8 \u10e0\u10dd\u10db\u10da\u10d8\u10e1 \u10d3\u10d0\u10e1\u10d9\u10d5\u10dc\u10d0\u10ea \u10e1\u10d0\u10d5\u10d0\u10da\u10d3\u10d4\u10d1\u10e3\u10da\u10dd\u10d0"},"billId":24024,"structureNodeId":50932,"nodeName":"\u10e1\u10d0\u10e4\u10d8\u10dc\u10d0\u10dc\u10e1\u10dd-\u10e1\u10d0\u10d1\u10d8\u10e3\u10ef\u10d4\u10e2\u10dd \u10d9\u10dd\u10db\u10d8\u10e2\u10d4\u10e2\u10d8"}]}

                List<List<Map<String, Object>>> billCommitteeList = (List<List<Map<String, Object>>>) lastBillReview.get("bill_committee");
                if (!billCommitteeList.isEmpty()) {
                    List<Map<String, Object>> committeeList = billCommitteeList.get(billCommitteeList.size() - 1);
                    for (Map<String, Object> committeeMap : committeeList) {
                        Committee committee = new Committee();
                        committee.setName(Objects.toString(committeeMap.get("nodeName"), null));
                        Map<String, Object> billCommitteeType = (Map<String, Object>) committeeMap.get("billCommitteeType");
                        committee.setRole(Objects.toString(billCommitteeType.get("name"), null));
                        committees.add(committee);
                    }
                }
                record.setCommitteeCount(committees.size());
            }

        }
        List<LegislativeStage> stages = record.getStages();
        record.setStagesCount(reviewList.size());
//        int stageNumber = 1;
        for (Map<String, Object> review : reviewList) {
            LegislativeStage legislativeStage = new LegislativeStage();
            legislativeStage.setStageNumber(((Number) review.get("index")).intValue());
//            stageNumber++;
            legislativeStage.setName(Objects.toString(review.get("reviewTypeName"), null));
            legislativeStage.setDate(toLocalDate(getStringValue(review, "bureauReviewDate"), "dd-MM-yyyy"));
            stages.add(legislativeStage);
        }

//        bill_ReviewContent
//        record.setBillSize();
//        Bill_size/Bill_text/Bill_text_url
        Optional<Map<String, Object>> initiated = reviewList.stream().filter(review -> Objects.toString(review.get("reviewTypeName")).equalsIgnoreCase("ინიციირებული ვარიანტი")).findAny();
        if (initiated.isPresent()) {
            List<Map<String, Object>> billReviewContentList = (List<Map<String, Object>>) initiated.get().get("bill_ReviewContent");
            Optional<Map<String, Object>> georgiaLegislativeProjectFile = billReviewContentList.stream()
                    .filter(billReviewContent -> JsonPathUtils.findTextInObject(billReviewContent, "$.billReviewAttachmentType.name")
                            .equalsIgnoreCase("საქართველოს კანონის პროექტი"))
                    .findAny();
            if (georgiaLegislativeProjectFile.isPresent()) {
                record.setBillTextUrl(Objects.toString(georgiaLegislativeProjectFile.get().get("contentUrl")));
                String georgiaLegislativeProjectFileContentAsXml = Objects.toString(georgiaLegislativeProjectFile.get().get("contentAsXml"));
                String georgiaLegislativeProjectFileContent = XmlUtils.xmlToText(georgiaLegislativeProjectFileContentAsXml);
                record.setBillText(georgiaLegislativeProjectFileContent);
                List<String> articleList = TextUtils.findTexts(georgiaLegislativeProjectFileContent, "მუხლი ([\\d]+[^\n]*)[\n]");
                record.setBillSize(articleList.size());
            }
        }

//        Law_size/Law_text
        Optional<Map<String, Object>> law = reviewList.stream().filter(review ->
                Objects.toString(review.get("reviewTypeName")).equalsIgnoreCase("კანონი"))
                .findAny();
        if (law.isPresent()) {
            List<Map<String, Object>> billReviewContentList = (List<Map<String, Object>>) initiated.get().get("bill_ReviewContent");
            if (!billReviewContentList.isEmpty()) {
                Map<String, Object> lawFile = billReviewContentList.get(0);
                record.setLawTextUrl(Objects.toString(lawFile.get("contentUrl")));
                String contentAsXml = Objects.toString(lawFile.get("contentAsXml"));
                record.setLawText(contentAsXml);
                List<String> articleList = TextUtils.findTexts(contentAsXml, "მუხლი ([\\d]+[^\n]*)[\n]");
                record.setLawSize(articleList.size());
            }
        }

//        Amendment_STAGES/AMENDMENTS/Amendment Outcomes
        List<Amendment> amendments = record.getAmendments();
        List<Map<String, Object>> amendmentList = reviewList.stream().filter(review ->
                        Objects.toString(review.get("reviewTypeName")).contains("I მოსმენა"))
                .collect(Collectors.toList());
        int amendmentStageNumber = 1;
        for (Map<String, Object> amendmentMap : amendmentList) {
            Amendment amendment = new Amendment();
            amendment.setStageName(Objects.toString(amendmentMap.get("reviewTypeName")));
            amendment.setStageNumber(amendmentStageNumber);
            amendmentStageNumber++;

            Number voteY = Optional.ofNullable((Number) amendmentMap.get("voteY")).orElse(0);
            amendment.setVotesInFavor(voteY.intValue());

            Number voteN = Optional.ofNullable((Number) amendmentMap.get("voteN")).orElse(0);
            amendment.setVotesAgainst(voteN.intValue());

            amendment.setVotesAbstention(ALL_PARLIAMENT_MEMBERS - (amendment.getVotesInFavor() + amendment.getVotesAgainst()));

            if (amendment.getStageName().equalsIgnoreCase("III მოსმენა")) {
                record.setFinalVoteFor(amendment.getVotesInFavor());
                record.setFinalVoteAgainst(amendment.getVotesAgainst());
                record.setFinalVoteAbst(amendment.getVotesAbstention());
            }
            amendments.add(amendment);
        }


        return record;
    }

    private static LegislativeDataRecord.ProcedureType toProcedureTypeStandard(String procedureTypeStandardValue) {
        if (procedureTypeStandardValue == null) {
            return LegislativeDataRecord.ProcedureType.REGULAR;
        }
        //        confirmedProcedure: true OR false
        LegislativeDataRecord.ProcedureType procedureTypeStandard;
        switch (procedureTypeStandardValue) {
            case "false":
            case "FALSE":
                procedureTypeStandard = LegislativeDataRecord.ProcedureType.REGULAR;
                break;
            case "true":
            case "TRUE":
                procedureTypeStandard = LegislativeDataRecord.ProcedureType.EXCEPTIONAL;
                break;
            default:
                procedureTypeStandard = LegislativeDataRecord.ProcedureType.REGULAR;
                break;
        }
        return procedureTypeStandard;
    }

    private static String toProcedureTypeNational(String procedureTypeNationalValue) {
        String procedureTypeNational = "";
        switch (procedureTypeNationalValue) {
            case "1":
                procedureTypeNational = "sped up procedure";
                break;
            case "2":
                procedureTypeNational = "eased procedure";
                break;
            default:
                procedureTypeNational = "standard";
                break;
        }
        return procedureTypeNational;
    }

    private static LegislativeDataRecord.BillStatus toBillStatus(List<Map<String, Object>> reviewTypes) {
        LegislativeDataRecord.BillStatus billStatus = LegislativeDataRecord.BillStatus.ONGOING;


        return billStatus;
    }
}
