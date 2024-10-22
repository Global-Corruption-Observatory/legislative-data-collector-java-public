package com.precognox.ceu.legislative_data_collector.georgia.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillListItem {

    private Integer id;
    private String number;
    private String date;
    private String title;
    private String author;
    private String initiator;
    private String type;

}
