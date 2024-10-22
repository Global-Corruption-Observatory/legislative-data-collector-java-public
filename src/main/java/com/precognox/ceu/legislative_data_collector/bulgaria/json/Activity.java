package com.precognox.ceu.legislative_data_collector.bulgaria.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Activity {

    @JsonProperty("L_Act_St_date")
    private String date;

    @JsonProperty("L_Act_St_name")
    private String eventName;

    @JsonProperty("L_Act_St2_name")
    private String stageName;

    @JsonProperty("Pl_Sten_id")
    private String plenarySessionId;

    @JsonProperty("A_ns_C_id")
    private String committeeId;

    @JsonProperty("A_ns_CL_value")
    private String committeeName;

    @JsonProperty("A_Cm_Stid")
    private String committeeTranscriptId;

}
