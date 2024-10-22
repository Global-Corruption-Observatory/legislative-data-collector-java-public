package com.precognox.ceu.legislative_data_collector.australia.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Hits {
    public List<HitsResult> hits = new ArrayList<>();
}
