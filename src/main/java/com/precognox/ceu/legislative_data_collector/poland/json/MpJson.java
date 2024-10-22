package com.precognox.ceu.legislative_data_collector.poland.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MpJson {

    @JsonProperty("club")
    private String party;

    @JsonProperty("firstLastName")
    private String fullName;
}
