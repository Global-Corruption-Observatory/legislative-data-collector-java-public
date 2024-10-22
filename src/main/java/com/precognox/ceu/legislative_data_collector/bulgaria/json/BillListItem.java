package com.precognox.ceu.legislative_data_collector.bulgaria.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillListItem {

    @JsonProperty("L_Act_id")
    private String actId;

    @JsonProperty("L_ActL_title")
    private String actTitle;

    @JsonProperty("L_Act_date")
    private String actDate;

    @JsonProperty("imp_list")
    private List<Originator> originatorList;

    @JsonProperty("imp_list_min")
    private List<GovOriginator> govOriginatorList;

}
