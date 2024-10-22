package com.precognox.ceu.legislative_data_collector.bulgaria.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Report {

    @JsonProperty("A_Cm_Stan_date")
    private String date;

    @JsonProperty("A_ns_CL_value")
    private String committeeName;

}
