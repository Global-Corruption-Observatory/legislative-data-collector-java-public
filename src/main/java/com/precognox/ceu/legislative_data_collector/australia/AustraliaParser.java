package com.precognox.ceu.legislative_data_collector.australia;

import com.precognox.ceu.legislative_data_collector.australia.json.DataJson;
import com.precognox.ceu.legislative_data_collector.australia.json.Hearing;
import com.precognox.ceu.legislative_data_collector.entities.*;
import com.precognox.ceu.legislative_data_collector.entities.australia.AuCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.entities.australia.PublicHearing;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.precognox.ceu.legislative_data_collector.utils.DateUtils.toLocalDate;
import static com.precognox.ceu.legislative_data_collector.utils.TextUtils.toInteger;

@Slf4j
public class AustraliaParser {

    public static final String BILL_PAGE_BASE_URL = "https://www.aph.gov.au/Parliamentary_Business/Bills_Legislation/Bills_Search_Results/Result?bId=";
    public static final String DATE_FORMAT_1 = "dd/MM/yyyy";
    public static final String DATE_FORMAT_2 = "dd MMM yyyy";
    public static final String DATE_FORMAT_3 = "dd-MM-yyyy";
    public static final String GOVERNMENT = "Government";
    public static final String PRIVATE = "Private";

    public static LegislativeDataRecord toDataRecord(DataJson json) {
        LegislativeDataRecord record = new LegislativeDataRecord(Country.AUSTRALIA);
        AuCountrySpecificVariables countrySpecificVariables = new AuCountrySpecificVariables();
        countrySpecificVariables.setLegislativeDataRecord(record);
        record.setAuCountrySpecificVariables(countrySpecificVariables);

        record.setBillId(json.bill_id);
        record.setBillPageUrl(BILL_PAGE_BASE_URL + json.bill_id);
        record.setBillTitle(json.bill_title);
        if (json.bill_summary != null) {
            countrySpecificVariables.setBillSummary(json.bill_summary.replace("\n", " "));
        }
        record.setBillTextUrl(json.bill_text_url);
        record.setBillText(json.bill_text);
        record.setLawTextUrl(json.law_text_url);
        if (!StringUtils.contains(json.law_text, "Could not find the file '")) {
            record.setLawText(json.law_text);
        }


        record.setOriginalLaw(json.original_law);
        record.setOriginType(toOriginType(json.origin_type));
        record.setBillStatus(toBillStatus(json.bill_status));

        List<Originator> originators = new ArrayList<>();
        Originator originator = new Originator();
        originator.setName(json.origin_name);
        originator.setAffiliation(json.originator_affiliation);
        originators.add(originator);
        record.setOriginators(originators);
        countrySpecificVariables.setPortfolio(json.portfolio);

        setStages(record, json);

        record.setDatePassing(toLocalDate(json.date_passing, DATE_FORMAT_2, Locale.ENGLISH, true));

        record.setCommittees(getCommittees(json));
        record.setCommitteeCount(record.getCommittees().size());

        countrySpecificVariables.setPublicHearingCount(json.public_hearing.size());
        for (Hearing hearing : json.public_hearing) {
            PublicHearing publicHearing = new PublicHearing();
            publicHearing.setPublicHearingDate(toLocalDate(hearing.public_hearing_date, DATE_FORMAT_2, Locale.ENGLISH, true));
            countrySpecificVariables.getPublicHearingDate().add(publicHearing);
        }

        countrySpecificVariables.setPublicHearingSubmissionCount(toInteger(json.public_hearing_submission_count, null));
        countrySpecificVariables.setPublicHearingGovernmentResponseDate(toLocalDate(json.public_hearing_government_response_date, DATE_FORMAT_2, Locale.ENGLISH, true));

        boolean iaDummy = json.em_text != null && json.em_text.toLowerCase().contains("financial impact");
        record.setImpactAssessmentDone(iaDummy);
        if (iaDummy) {
            ImpactAssessment impactAssessment = new ImpactAssessment();
            impactAssessment.setDataRecord(record);
            impactAssessment.setTitle(json.em_title);
            impactAssessment.setText(json.em_text);
            TextUtils.getTextSize(json.em_text).ifPresent(impactAssessment::setSize);
            impactAssessment.setDate(toLocalDate(json.em_date, DATE_FORMAT_3, Locale.ENGLISH, true));
            record.getImpactAssessments().add(impactAssessment);
            countrySpecificVariables.setEmDummy(1);
        } else {
            countrySpecificVariables.setEmTitle(json.em_title);
            countrySpecificVariables.setEmText(json.em_text);
            TextUtils.getTextSize(json.em_text).ifPresent(countrySpecificVariables::setEmSize);
            countrySpecificVariables.setEmDate(toLocalDate(json.em_date, DATE_FORMAT_3, Locale.ENGLISH, true));
            countrySpecificVariables.setEmDummy(StringUtils.isNotBlank(countrySpecificVariables.getEmText()) ? 1 : 0);
        }


        record.setLawTextUrl(json.law_text_url);
        record.setLawText(json.law_text);

        record.setAmendments(getAmendments(json, record));

        record.setFinalVoteFor(json.final_vote_for);
        record.setFinalVoteAgainst(json.final_vote_against);

        return record;
    }

    private static List<Amendment> getAmendments(DataJson json, LegislativeDataRecord record) {
        List<Amendment> amendments = new ArrayList<>();
        for (com.precognox.ceu.legislative_data_collector.australia.json.Amendment amendmentJson : json.amendments) {
            Amendment amendment = new Amendment();
            amendment.setDataRecord(record);
            if (amendmentJson.amendment_id != null) {
                amendment.setAmendmentId(amendmentJson.amendment_id.replace("[sheet ", "").replace("]", ""));
            }
            Amendment.Plenary plenary = amendmentJson.amendment_plenary.equals("Senate") ? Amendment.Plenary.UPPER : Amendment.Plenary.LOWER;
            amendment.setPlenary(plenary);
            amendment.setTextSourceUrl(amendmentJson.amendment_text_url);
            amendment.setAmendmentText(amendmentJson.amendment_text);
            List<AmendmentOriginator> originators = new ArrayList<>();
            for (com.precognox.ceu.legislative_data_collector.australia.json.AmendmentOriginator amendmentOriginatorJson : amendmentJson.amendment_originators) {
                AmendmentOriginator originator = new AmendmentOriginator();
                originator.setName(amendmentOriginatorJson.name);
                originator.setAffiliation(amendmentOriginatorJson.aff);
                originators.add(originator);
            }
            amendment.setOriginators(originators);
            amendments.add(amendment);
        }
        record.setAmendmentCount(json.amendments.size());
        return amendments;
    }

    private static List<Committee> getCommittees(DataJson json) {
        List<Committee> committees = new ArrayList<>();
        for (com.precognox.ceu.legislative_data_collector.australia.json.Committee committeeJson : json.getCommittee()) {
            Committee committee = new Committee();
            if (StringUtils.isNotBlank(committeeJson.committee_name)) {
                if (committeeJson.committee_name.contains("\n")) {
                    committee.setName(committeeJson.committee_name.split("\n")[0].trim());
                } else {
                    committee.setName(committeeJson.committee_name);
                }
            }
            committee.setDate(toLocalDate(committeeJson.committee_date, DATE_FORMAT_1));
            committees.add(committee);
        }
        return committees;
    }

    private static void setStages(LegislativeDataRecord record, DataJson json) {
        List<LegislativeStage> stages = new ArrayList<>();

        LegislativeStage stage1 = new LegislativeStage();
        stage1.setStageNumber(1);
        stage1.setName("House of Representatives - First Reading");
        stage1.setDate(toLocalDate(json.date_stage1, DATE_FORMAT_2, Locale.ENGLISH, true));
        TextUtils.getTextSize(json.site_stage1_text).ifPresent(stage1::setDebateSize);
        stages.add(stage1);

        if (record.getDateIntroduction() == null) {
            record.setDateIntroduction(stage1.getDate());
        }

        if (StringUtils.isNotBlank(json.date_stage2) || StringUtils.isNotBlank(json.site_stage_2_text)) {
            LegislativeStage stage2 = new LegislativeStage();
            stage2.setStageNumber(2);
            stage2.setName("House of Representatives - Second Reading");
            stage2.setDate(toLocalDate(json.date_stage2, DATE_FORMAT_2, Locale.ENGLISH, true));
            TextUtils.getTextSize(json.site_stage_2_text).ifPresent(stage2::setDebateSize);
            stages.add(stage2);
        }

        if (StringUtils.isNotBlank(json.date_stage3)) {
            LegislativeStage stage3 = new LegislativeStage();
            stage3.setStageNumber(3);
            stage3.setName("House of Representatives  - Third Reading ");
            stage3.setDate(toLocalDate(json.date_stage3, DATE_FORMAT_2, Locale.ENGLISH, true));
            stages.add(stage3);
        }

        if (StringUtils.isNotBlank(json.date_stage4)) {
            LegislativeStage stage4 = new LegislativeStage();
            stage4.setStageNumber(4);
            stage4.setName("Senate - First Reading");
            stage4.setDate(toLocalDate(json.date_stage4, DATE_FORMAT_2, Locale.ENGLISH, true));
            stages.add(stage4);
        }

        if (StringUtils.isNotBlank(json.date_stage5) || StringUtils.isNotBlank(json.site_stage_5_text)) {
            LegislativeStage stage5 = new LegislativeStage();
            stage5.setStageNumber(5);
            stage5.setName("Senate - Second Reading");
            stage5.setDate(toLocalDate(json.date_stage5, DATE_FORMAT_2, Locale.ENGLISH, true));
            TextUtils.getTextSize(json.site_stage_5_text).ifPresent(stage5::setDebateSize);
            stages.add(stage5);
        }

        if (StringUtils.isNotBlank(json.date_stage6)) {
            LegislativeStage stage6 = new LegislativeStage();
            stage6.setStageNumber(6);
            stage6.setName("Senate - Third Reading");
            stage6.setDate(toLocalDate(json.date_stage6, DATE_FORMAT_2, Locale.ENGLISH, true));
            stages.add(stage6);
        }

        record.setStages(stages);
        record.setStagesCount(stages.size());
    }

    private static LegislativeDataRecord.BillStatus toBillStatus(String billStatusParam) {
        String billStatus;
        if (StringUtils.isBlank(billStatusParam)) {
            return null;
        }
        billStatus = billStatusParam.toLowerCase();
        if (billStatus.equals("Not Proceeding".toLowerCase())) {
            return LegislativeDataRecord.BillStatus.REJECT;
        }
        if (billStatus.equals("Act".toLowerCase()) || billStatus.equals("Assent".toLowerCase())) {
            return LegislativeDataRecord.BillStatus.PASS;
        }
        if (billStatus.equals("Passed Both houses".toLowerCase())
                || billStatus.equals("Before the House of Representatives".toLowerCase())
                || billStatus.equals("Before Senate".toLowerCase())
                || billStatus.equals("Before Reps".toLowerCase())) {
            return LegislativeDataRecord.BillStatus.ONGOING;
        }
        if (StringUtils.isNotBlank(billStatus)) {
            log.error("Invalid BillStatus:'" + billStatus + "'");
        }
        return null;
    }

    private static OriginType toOriginType(String originType) {
        if (StringUtils.isBlank(originType)) {
            return null;
        }
        if (originType.equals(GOVERNMENT)) {
            return OriginType.GOVERNMENT;
        }
        if (originType.equals(PRIVATE)) {
            return OriginType.PRIVATE;
        }
        if (StringUtils.isNotBlank(originType)) {
            throw new UnsupportedOperationException("Invalid OriginType:" + originType + "'");
        }
        return null;
    }
}
