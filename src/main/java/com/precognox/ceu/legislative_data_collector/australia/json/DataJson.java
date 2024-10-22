package com.precognox.ceu.legislative_data_collector.australia.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataJson {
    public String url;
    public String bill_title;
    public String bill_id;
    public String bill_summary;
    public boolean original_law;
    public String origin_type;
    public String bill_status;
    public String origin_name;
    public String portfolio;
    public String government_link;
    public String originator_affiliation;
    public String date_stage1;
    public String date_stage2;
    public String date_stage3;
    public String date_stage4;
    public String date_stage5;
    public String date_stage6;
    public String date_passing;
    public String site_stage1_text;
    public String site_stage_2_text;
    public String site_stage_5_text;
    public String stage_type;
    public List<Committee> committee = new ArrayList<>();
    public String em_text;
    public boolean ia_dummy;
    public String em_title;
    public String em_date;
    public String bill_text_url;
    public String bill_text;
    public String law_text_url;
    public String law_text;
    public List<Hearing> public_hearing = new ArrayList<>();
    public String public_hearing_submission_count;
    public String public_hearing_government_response_date;
    public List<Amendment> amendments = new ArrayList<>();
    public Integer final_vote_for;
    public Integer final_vote_against;
    public Integer parliamentNumber;
    public Integer amendment_count;
}
