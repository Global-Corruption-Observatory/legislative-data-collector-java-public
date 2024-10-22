package com.precognox.ceu.legislative_data_collector.australia.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Amendment {

    public String amendment_plenary;
    public String amendment_id;
    public String amendment_text;
    public String amendment_text_url;
    public List<AmendmentOriginator> amendment_originators = new ArrayList<>();
}


