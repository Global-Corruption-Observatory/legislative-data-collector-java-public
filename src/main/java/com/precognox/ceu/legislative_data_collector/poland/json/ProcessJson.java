package com.precognox.ceu.legislative_data_collector.poland.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProcessJson {

    @JsonProperty("number")
    private String number;

    @JsonProperty("processStartDate")
    private String introductionDate;

    @JsonProperty("term")
    private String termOfOffice;

    @JsonProperty("title")
    private String processTitle;
}
