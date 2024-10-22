package com.precognox.ceu.legislative_data_collector.bulgaria.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommitteeDebateJson {

    @JsonProperty("A_Cm_St_date")
    private String date;

    @JsonProperty("A_Cm_St_sub")
    private String titleText;

    @JsonProperty("A_Cm_St_text")
    private String debateText;

}
