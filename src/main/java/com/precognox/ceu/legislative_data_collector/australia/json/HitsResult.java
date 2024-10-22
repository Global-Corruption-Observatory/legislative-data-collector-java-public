package com.precognox.ceu.legislative_data_collector.australia.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HitsResult {
    public DataJson _source;
}
