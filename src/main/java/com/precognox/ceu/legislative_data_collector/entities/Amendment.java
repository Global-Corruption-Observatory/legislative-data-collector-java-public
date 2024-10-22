package com.precognox.ceu.legislative_data_collector.entities;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "AMENDMENTS")
@ToString(exclude = {"dataRecord", "amendmentText"})
public class Amendment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "record_id")
    private LegislativeDataRecord dataRecord;

    @Column(name = "amendment_page_url")
    private String pageUrl;

    private String amendmentId;

    private LocalDate date;

    @Column(name = "amendment_stage_number")
    private Integer stageNumber;

    @Column(name = "amendment_stage_name")
    private String stageName;

    @Column(name = "amendment_title")
    private String title;

    @Column(name = "amendment_committee_name")
    private String committeeName;

    @Embedded
    @ElementCollection
    @CollectionTable(name = "AMENDMENT_ORIGINATORS", joinColumns = @JoinColumn(name = "amendment_id"))
    private List<AmendmentOriginator> originators = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "amendment_plenary")
    private Plenary plenary;

    @Enumerated(EnumType.STRING)
    @Column(name = "amendment_outcome")
    private Outcome outcome;

    @Column(name = "amendment_vote_for")
    private Integer votesInFavor;

    @Column(name = "amendment_vote_against")
    private Integer votesAgainst;

    @Column(name = "amendment_vote_abst")
    private Integer votesAbstention;

    @Column(name = "amendment_text_url")
    private String textSourceUrl;

    @Column(name = "amendment_text")
    private String amendmentText;

    public enum Outcome {
        APPROVED, REJECTED, WITHDRAWN, IN_PROGRESS
    }

    public enum Plenary {
        LOWER, UPPER
    }

    public void setCommitteeName(String committeeName) {
        if (StringUtils.isNotEmpty(committeeName)) {
            this.committeeName = committeeName;
        }
    }
}
