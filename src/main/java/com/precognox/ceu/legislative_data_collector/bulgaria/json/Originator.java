package com.precognox.ceu.legislative_data_collector.bulgaria.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Originator {

    @JsonProperty("A_ns_MP_id")
    private Integer mpId;

    @JsonProperty("A_ns_MPL_Name1")
    private String mpName1;

    @JsonProperty("A_ns_MPL_Name2")
    private String mpName2;

    @JsonProperty("A_ns_MPL_Name3")
    private String mpName3;

}
