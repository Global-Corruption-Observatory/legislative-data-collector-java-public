package com.precognox.ceu.legislative_data_collector.entities;

import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Getter
@Setter
@Embeddable
@NoArgsConstructor
public class AmendmentOriginator {

    @Column(name = "originator_name")
    private String name;

    @Column(name = "originator_affiliation")
    private String affiliation;

    public AmendmentOriginator(String name) {
        this.name = TextUtils.cleanName(name);
    }

    public AmendmentOriginator(String name, String affiliation) {
        this.name = TextUtils.cleanName(name);
        this.affiliation = affiliation;
    }
}
