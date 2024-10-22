package com.precognox.ceu.legislative_data_collector.bulgaria.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillJson {

    @JsonProperty("L_Act_id")
    private String actId;

    @JsonProperty("L_ActL_title")
    private String billTitle;

    @JsonProperty("L_ActL_final")
    private String lawTitle;

    @JsonProperty("L_Act_dv_iss")
    private String gazetteNumber;

    @JsonProperty("L_Act_dv_year")
    private String gazetteNumberYear;

    @JsonProperty("imp_list")
    private List<Originator> originators;

    @JsonProperty("imp_list_min")
    private List<GovOriginator> govOriginators;

    @JsonProperty("stan_list")
    private List<Report> reports;

    @JsonProperty("stan_list2")
    private List<Report> reports2;

    @JsonProperty("stan_list2_1")
    private List<Report> reports2_1;

    @JsonProperty("activity")
    private List<Activity> activities;

    @JsonProperty("steno_hall")
    private List<PlenaryTranscript> plenaryTranscripts;

    @JsonProperty("dist_list")
    private List<Committee> committees;

    @JsonProperty("L_Act_date")
    private String dateIntroduction;

    @JsonProperty("L_Act_date2")
    private String datePassing;

    @JsonProperty("union_list")
    private List<RelatedBillListItem> unionList;

    @JsonProperty("trans_label")
    private String transLabel;

    @JsonProperty("trans_list")
    private List<RelatedBillListItem> transList;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelatedBillListItem {
        @JsonProperty("L_Act_id")
        private Integer billId;
        @JsonProperty("L_ActL_title")
        private String billTitle;
    }

}
