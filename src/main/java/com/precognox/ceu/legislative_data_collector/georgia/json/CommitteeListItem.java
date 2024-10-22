package com.precognox.ceu.legislative_data_collector.georgia.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommitteeListItem {

    private Integer id;
    private String nodeName;
    private CommitteeType billCommitteeType;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommitteeType {
        private Integer id;
        private String name;
    }

    public Committee toEntity() {
        return new Committee(nodeName, billCommitteeType.name);
    }

}
