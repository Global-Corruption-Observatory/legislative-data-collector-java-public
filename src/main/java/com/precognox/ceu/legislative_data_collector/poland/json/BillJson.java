package com.precognox.ceu.legislative_data_collector.poland.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillJson {

    @JsonProperty("address")
    private String billAddressForPdfText;

    @JsonProperty("displayAddress")
    private String lawId;

    @JsonProperty("title")
    private String billTitle;

    @JsonProperty("status")
    private String billStatus;

    @JsonProperty("promulgation")
    private String datePassing;

    @JsonProperty("entryIntoForce")
    private String dateEnteringIntoForce;

    @JsonProperty("textHTML")
    private Boolean textHTML;

    @JsonProperty("prints")
    private List<PrintJson> prints;

    @JsonProperty("texts")
    private List<BillTextJson> billTexts;

    @JsonProperty("references")
    private BillJsonReference billReference;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PrintJson {

        @JsonProperty("link")
        private String linkToProcess;

        @JsonProperty("linkProcessAPI")
        private String linkToProcessApi;

        @JsonProperty("number")
        private String processNumber;

        @JsonProperty("term")
        private Integer term;

    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BillTextJson {

        @JsonProperty("fileName")
        private String billTextFileName;

        @JsonProperty("type")
        private String billTextFileType;

    }
}
