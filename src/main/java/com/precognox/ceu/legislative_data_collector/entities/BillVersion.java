package com.precognox.ceu.legislative_data_collector.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class BillVersion {

    private String text;
    private String textSourceUrl;
    private Integer textSizeChars;

    @Enumerated(EnumType.STRING)
    private House house;

    public enum House {
        LOWER, UPPER
    }
}
