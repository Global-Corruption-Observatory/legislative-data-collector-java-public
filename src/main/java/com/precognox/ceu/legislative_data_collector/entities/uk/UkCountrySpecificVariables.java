package com.precognox.ceu.legislative_data_collector.entities.uk;

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
public class UkCountrySpecificVariables {
    private Boolean hasRoyalAssent = false;
    private Boolean hasProgrammeMotion = false;
    private Boolean hasMoneyResolution = false;
    private Boolean hasWaysAndMeansResolution = false;
}
