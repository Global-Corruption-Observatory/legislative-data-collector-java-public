package com.precognox.ceu.legislative_data_collector.entities.australia;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "australia_spec_vars")
@ToString(exclude = {"legislativeDataRecord", "publicHearingDate"})
@NoArgsConstructor
@AllArgsConstructor
public class AuCountrySpecificVariables {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "record_id")
    private LegislativeDataRecord legislativeDataRecord;

    private String billSummary;
    private String portfolio;
    private Integer publicHearingCount;
    private Integer publicHearingSubmissionCount;
    private LocalDate publicHearingGovernmentResponseDate;

    private String emText;
    private String emTitle;
    private LocalDate emDate;
    private Integer emDummy;
    private Integer emSize;

    private Integer relatedBillsCount;
    private String actNumber;

    @Embedded
    @ElementCollection
    @CollectionTable(name = "public_hearing", joinColumns = @JoinColumn(name = "country_spec_id"))
    private List<PublicHearing> publicHearingDate = new ArrayList<>();
}
