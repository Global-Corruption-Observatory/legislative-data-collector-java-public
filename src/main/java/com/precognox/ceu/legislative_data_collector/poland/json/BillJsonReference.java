package com.precognox.ceu.legislative_data_collector.poland.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillJsonReference {

    @JsonProperty("Akty zmieniające")
    private List<ImplementingAct> implementingActs;

    @JsonProperty("Akty zmienione")
    private List<AmendedAct> amendedActs;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AmendedAct {

        @JsonProperty("id")
        private String amendedActId;

        @JsonProperty("date")
        private String amendedActDate;

    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImplementingAct {

        @JsonProperty("id")
        private String implementingActId;

        @JsonProperty("date")
        private String implementingActDate;

    }
}
