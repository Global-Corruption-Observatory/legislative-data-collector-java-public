package com.precognox.ceu.legislative_data_collector.bulgaria.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MpJson {

    @JsonProperty("A_ns_CoalL_value")
    private String affiliation;

}
