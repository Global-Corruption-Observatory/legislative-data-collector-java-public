package com.precognox.ceu.legislative_data_collector.georgia.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillList {

    private Integer total;
    private List<BillListItem> data = new ArrayList<>();

}
