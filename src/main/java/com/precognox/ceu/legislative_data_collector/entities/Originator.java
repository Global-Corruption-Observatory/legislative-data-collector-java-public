package com.precognox.ceu.legislative_data_collector.entities;

import com.precognox.ceu.legislative_data_collector.entities.colombia.ColombiaOriginatorVariables;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;

@Getter
@Setter
@Embeddable
@ToString(exclude = {"colombiaVariables"})
@NoArgsConstructor
public class Originator {

    @Column(name = "originator_name")
    private String name;

    @Column(name = "originator_affiliation")
    private String affiliation;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride( name = "education", column = @Column(name = "originator_education")),
            @AttributeOverride( name = "age", column = @Column(name = "originator_age")),
            @AttributeOverride( name = "gender", column = @Column(name = "originator_gender")),
            @AttributeOverride( name = "constituency", column = @Column(name = "originator_constituency")),
            @AttributeOverride( name = "investigationsDummy", column = @Column(name = "originator_investigations_dummy")),
            @AttributeOverride( name = "investigationsCount", column = @Column(name = "originator_investigations_count"))
    })
    private ColombiaOriginatorVariables colombiaVariables;

    public Originator(String name) {
        this.name = TextUtils.cleanName(name);
    }

    public Originator(String name, String affiliation) {
        this.name = TextUtils.cleanName(name);
        this.affiliation = affiliation.strip();
    }
}
