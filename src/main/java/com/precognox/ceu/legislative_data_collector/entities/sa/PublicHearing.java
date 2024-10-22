package com.precognox.ceu.legislative_data_collector.entities.sa;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.time.LocalDate;
@Data
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class PublicHearing {

    @Column(name = "hearing_title")
    private String hearingTitle;

    @Column(name = "hearing_date")
    private LocalDate hearingDate;

    @Column(name = "hearing_submission_count")
    private Integer hearingSubmissionCount;

    public PublicHearing(String hearingTitle, LocalDate hearingDate, Integer hearingSubmissionCount) {
        this.hearingTitle = hearingTitle;
        this.hearingDate = hearingDate;
        this.hearingSubmissionCount = hearingSubmissionCount;
    }
}
