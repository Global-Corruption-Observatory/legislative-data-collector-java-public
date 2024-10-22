package com.precognox.ceu.legislative_data_collector.georgia.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewListItem {

    private Integer id;
    private Integer index;
    private String reviewTypeName;
    private ReviewType billReviewType;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReviewType {
        private Integer id;
        private String name;
    }

    public LegislativeStage toEntity() {
        return new LegislativeStage(index, null, reviewTypeName);
    }

}
