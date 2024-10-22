package com.precognox.ceu.legislative_data_collector.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.time.LocalDate;

@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class Committee {

    @Column(name = "committee_name")
    private String name;

    @Column(name = "committee_role")
    private String role;

    @Column(name = "committee_date")
    private LocalDate date;

    //South africa country specific variable
    @Column(name = "committee_hearing_count")
    private Integer committeeHearingCount;

    //South africa country specific variable
    @Column(name = "number_of_public_hearings_committee")
    private Integer numberOfPublicHearingsCommittee;

    public static final Committee WHOLE_HOUSE = new Committee("Whole House", null);
    public static final Committee PUBLIC_BILL_COMMITTEE = new Committee("Public Bill Committee", null);

    public Committee(String name, String role) {
        this.name = name;
        this.role = role;
    }

    public Committee(String name, String role, LocalDate date) {
        this.name = name;
        this.role = role;
        this.date = date;
    }
}
