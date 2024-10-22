package com.precognox.ceu.legislative_data_collector.entities;

import com.precognox.ceu.legislative_data_collector.entities.australia.AuCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.entities.bg.BgCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.entities.brazil.BrazilCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.entities.chile.AffectingLawDetailed;
import com.precognox.ceu.legislative_data_collector.entities.chile.ChileCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.entities.colombia.ColombiaCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.entities.ge.GeCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.entities.jo.JoCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.entities.sa.SouthAfricaCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.entities.swe.SwedenCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.entities.uk.UkCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.entities.usa.UsaCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.india.entities.IndiaCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.utils.PdfParser;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "BILL_MAIN_TABLE")
@ToString(exclude = {"committees", "billVersions", "billText", "lawText", "amendments", "impactAssessments",
        "billTextGeneralJustification", "errors", "modifiedLaws", "affectingLawsDetailed", "stages"})
public class LegislativeDataRecord {

    @Id
    @GeneratedValue
    private Long id;
    private String recordId;

    @Enumerated(EnumType.STRING)
    private Country country;

    private String billType;
    private String lawType;
    private String typeOfLawEng;
    private String billId;
    private String lawId;
    private String billTitle;
    private String billPageUrl;
    private Boolean originalLaw;

    @Enumerated(EnumType.STRING)
    private OriginType originType;
    private Integer stagesCount;

    @Enumerated(EnumType.STRING)
    private BillStatus billStatus;

    @Embedded
    @ElementCollection
    @LazyCollection(LazyCollectionOption.FALSE)
    @CollectionTable(name = "ORIGINATORS", joinColumns = @JoinColumn(name = "record_id"))
    private List<Originator> originators = new ArrayList<>();

    private Integer billSize;
    private String billText;
    private String billTextUrl;
    private String lawText;
    private String lawTextUrl;
    private Integer lawSize;
    private LocalDate dateIntroduction;
    private LocalDate committeeDate;
    private LocalDate datePassing;
    private LocalDate dateEnteringIntoForce;
    private Integer committeeCount;
    private Integer committeeHearingCount;
    private Integer committeeDepth;

    @Embedded
    @ElementCollection
    @LazyCollection(LazyCollectionOption.FALSE)
    @CollectionTable(name = "COMMITTEES", joinColumns = @JoinColumn(name = "record_id"))
    private List<Committee> committees = new ArrayList<>();

    @Embedded
    @ElementCollection
    @LazyCollection(LazyCollectionOption.FALSE)
    @CollectionTable(name = "BILL_VERSIONS", joinColumns = @JoinColumn(name = "record_id"))
    private List<BillVersion> billVersions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private ProcedureType procedureTypeStandard;
    private String procedureTypeEng;
    private String procedureTypeNational;

    @Column(name = "ia_dummy")
    private Boolean impactAssessmentDone;
    private Integer amendmentCount;

    @LazyCollection(LazyCollectionOption.FALSE)
    @OneToMany(mappedBy = "dataRecord", cascade = CascadeType.ALL)
    private List<Amendment> amendments = new ArrayList<>();

    @LazyCollection(LazyCollectionOption.FALSE)
    @OneToMany(mappedBy = "dataRecord", cascade = CascadeType.ALL)
    private List<ImpactAssessment> impactAssessments = new ArrayList<>();

    @LazyCollection(LazyCollectionOption.FALSE)
    @ElementCollection
    @Column(name = "modified_law_id")
    @CollectionTable(name = "AFFECTED_LAWS", joinColumns = @JoinColumn(name = "record_id"))
    private Set<String> modifiedLaws = new HashSet<>();

    private Integer modifiedLawsCount;
    private Integer affectingLawsCount;
    private LocalDate affectingLawsFirstDate;

    @LazyCollection(LazyCollectionOption.FALSE)
    @OneToMany(mappedBy = "dataRecord", cascade = CascadeType.ALL)
    private List<AffectingLawDetailed> affectingLawsDetailed = new ArrayList<>();

    @Embedded
    @ElementCollection
    @LazyCollection(LazyCollectionOption.FALSE)
    @CollectionTable(name = "LEGISLATIVE_STAGES", joinColumns = @JoinColumn(name = "record_id"))
    private List<LegislativeStage> stages = new ArrayList<>();

    @ElementCollection
    @Column(name = "originator_support_name")
    @LazyCollection(LazyCollectionOption.FALSE)
    @CollectionTable(name = "ORIGINATOR_SUPPORT_NAMES", joinColumns = @JoinColumn(name = "record_id"))
    private Set<String> originatorSupportNames = new HashSet<>();

    @Embedded
    @ElementCollection
    @LazyCollection(LazyCollectionOption.FALSE)
    @CollectionTable(name = "RELATED_BILLS", joinColumns = @JoinColumn(name = "record_id"))
    private List<RelatedBill> relatedBills = new ArrayList<>();

    private Integer plenarySize;

    private Integer finalVoteFor;
    private Integer finalVoteAgainst;
    private Integer finalVoteAbst;

    @Embedded
    private UkCountrySpecificVariables countrySpecificVariables = new UkCountrySpecificVariables();

    @OneToOne(mappedBy = "legislativeDataRecord", cascade = CascadeType.ALL)
    private BrazilCountrySpecificVariables brazilCountrySpecificVariables;

    @OneToOne(mappedBy = "legislativeDataRecord", cascade = CascadeType.ALL)
    private ColombiaCountrySpecificVariables colombiaCountrySpecificVariables;

    @Embedded
    private UsaCountrySpecificVariables usaCountrySpecificVariables;

    @OneToOne(mappedBy = "legislativeDataRecord", cascade = CascadeType.ALL)
    private JoCountrySpecificVariables jordanCountrySpecificVariables;

    @Embedded
    private GeCountrySpecificVariables geCountrySpecificVariables;

    @OneToOne(mappedBy = "legislativeDataRecord", cascade = CascadeType.ALL)
    private IndiaCountrySpecificVariables indiaCountrySpecificVariables;

    @OneToOne(mappedBy = "legislativeDataRecord", cascade = CascadeType.ALL)
    private BgCountrySpecificVariables bgSpecificVariables;

    @OneToOne(mappedBy = "legislativeDataRecord", cascade = CascadeType.ALL)
    private ChileCountrySpecificVariables chileCountrySpecificVariables;

    @OneToOne(mappedBy = "legislativeDataRecord", cascade = CascadeType.ALL)
    private AuCountrySpecificVariables auCountrySpecificVariables;

    @OneToOne(mappedBy = "legislativeDataRecord", cascade = CascadeType.ALL)
    private SwedenCountrySpecificVariables swedenCountrySpecificVariables;

    @Embedded
    private SouthAfricaCountrySpecificVariables southAfricaCountrySpecificVariables;

    private LocalDateTime dateProcessed;

    private String billTextGeneralJustification;

    private String altBillPageUrl;

    @Embedded
    private RawPageSource rawPageSource;

    //workaround for kotlin tests - will be removed later
    public LocalDate getDatePassing() {
        return datePassing;
    }

    public Integer getFinalVoteFor() {
        return finalVoteFor;
    }

    public Integer getFinalVoteAgainst() {
        return finalVoteAgainst;
    }

    public Integer getFinalVoteAbst() {
        return finalVoteAbst;
    }

    public Integer getStagesCount() {
        return stagesCount;
    }

    public Integer getCommitteeCount() {
        return committeeCount;
    }

    public Integer getCommitteeHearingCount() {
        return committeeHearingCount;
    }

    public List<Originator> getOriginators() {
        return originators;
    }

    @ElementCollection
    @Column(name = "error")
    @CollectionTable(name = "ERRORS", joinColumns = @JoinColumn(name = "record_id"))
    private Set<String> errors = new HashSet<>();

    public enum BillStatus {
        PASS, REJECT, ONGOING
    }

    public enum ProcedureType {
        REGULAR, EXCEPTIONAL
    }

    public enum BillNature {
        NEW_BILL, AMENDMENT_BILL
    }

    public LegislativeDataRecord(Country country) {
        this.country = country;
    }

    public void setBillText(String billText) {
        this.billText = billText;

        if (!PdfParser.ERROR_LABEL.equals(billText) && !PdfParser.SCANNED_LABEL.equals(billText)) {
            this.billSize = TextUtils.getLengthWithoutWhitespace(billText);
        }
    }

    public void setLawText(String lawText) {
        this.lawText = lawText;

        if (!PdfParser.ERROR_LABEL.equals(lawText) && !PdfParser.SCANNED_LABEL.equals(lawText)) {
            this.lawSize = TextUtils.getLengthWithoutWhitespace(lawText);
        }
    }

    public void setAffectingLawsDetailed(List<AffectingLawDetailed> affectingLaws) {
        if (this.affectingLawsDetailed == null) {
            this.affectingLawsDetailed = affectingLaws;
        } else {
            affectingLaws.stream()
                    .filter(Objects::nonNull)
                    .filter(newAffectingLaw -> !this.affectingLawsDetailed.contains(newAffectingLaw))
                    .forEach(this.affectingLawsDetailed::add);
        }
    }

    public BrazilCountrySpecificVariables getBrazilCountrySpecificVariables() {
        if (brazilCountrySpecificVariables == null) {
            brazilCountrySpecificVariables = new BrazilCountrySpecificVariables(this);
        }

        return brazilCountrySpecificVariables;
    }
}
