package com.precognox.ceu.legislative_data_collector.entities.colombia;

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
public class ColombiaOriginatorVariables {

    private String education;

    private Integer age;

    private String gender;

    private String constituency;

    private Boolean investigationsDummy;

    private Integer investigationsCount;

    public enum Gender {
        MALE,
        FEMALE
    }
}
