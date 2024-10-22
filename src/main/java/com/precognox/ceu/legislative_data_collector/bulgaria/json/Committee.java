package com.precognox.ceu.legislative_data_collector.bulgaria.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Committee {

    @JsonProperty("A_ns_CL_value")
    private String committeeName;

    @JsonProperty("L_Act_DTL_value")
    private String committeeRole;

}
