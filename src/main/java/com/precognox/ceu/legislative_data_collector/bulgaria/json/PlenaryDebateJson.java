package com.precognox.ceu.legislative_data_collector.bulgaria.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlenaryDebateJson {

    @JsonProperty("Pl_Sten_sub")
    private String debateTitle;

    @JsonProperty("Pl_Sten_body")
    private String debateText;

    @JsonProperty("files")
    private List<DebateFile> files;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DebateFile {

        @JsonProperty("Pl_StenDfile")
        private String filePath;

        @JsonProperty("Pl_StenDname")
        private String name;

    }

}
