package com.precognox.ceu.legislative_data_collector.india.new_website.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponse {

    @JsonProperty("_metadata")
    private Metadata metadata;
    private List<Record> records;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        public int perPageSize;
        public int currentPageNumber;
        public int totalElements;
        public int totalPages;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Record {
        private String billNumber;
        private String billName;
        private String billType;
        private String billCategory;
        private String ministryName;
        private Integer billYear;
        private String billIntroducedInHouse;
        private String billIntroducedBy;
        private String billIntroducedDate;
        private String billIntroducedFile;
        private String billPassedInBothHousesFile;
        private String status;
        private String billAssentedDate;
        private String billPassedInLSDate;
        private String billPassedInRSDate;
        private String actNo;
        private Integer actYear;
    }

}
