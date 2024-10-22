package com.precognox.ceu.legislative_data_collector.entities.usa;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Embeddable;

@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class UsaCountrySpecificVariables {
    private Integer cosponsorCount;
    private Integer relatedBillsCount;
    private Integer amendmentStagesCount;
}
