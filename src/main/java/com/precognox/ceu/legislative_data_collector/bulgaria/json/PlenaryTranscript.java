package com.precognox.ceu.legislative_data_collector.bulgaria.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlenaryTranscript {

    @JsonProperty("Pl_Sten_id")
    private String transcriptId;

    @JsonProperty("L_Act_St_date")
    private String date;

    @JsonProperty("L_Act_St2_name")
    private String stageName;

    @JsonProperty("L_Act_St_name")
    private String eventName;

}
