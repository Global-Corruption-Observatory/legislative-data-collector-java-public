package com.precognox.ceu.legislative_data_collector.entities.colombia;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import lombok.Data;
import lombok.ToString;

import javax.persistence.*;


@Data
@Entity
@Table(name = "colombia_spec_vars")
@ToString(exclude = "legislativeDataRecord")
public class ColombiaCountrySpecificVariables {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "record_id")
    private LegislativeDataRecord legislativeDataRecord;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type_co")
    private OriginTypeColombia originTypeColombia;
    private String billMainTopic;
    private String billSecondaryTopic;
    private String billSummary;
    @Column(name = "bill_status_co")
    private String billStatusColombia;
    private String houseBillId;
    private String senateBillId;
    @Column(name = "bill_type_co")
    private String billTypeColombia;

    private Boolean proceduralDefectDummy;
    @Column(name = "gazette_number_stage_0")
    private String filingGazette;
    @Column(name = "gazette_number_stage_1")
    private String publicationGazette;
    @Column(name = "gazette_number_stage_2")
    private String firstDebateGazette;
    @Column(name = "gazette_number_stage_3")
    private String secondDebateGazette;
    @Column(name = "gazette_number_stage_4")
    private String thirdDebateGazette;
    @Column(name = "gazette_number_stage_5")
    private String fourthDebateGazette;
    @Column(name = "gazette_number_stage_6")
    private String sanctionGazette;

    @Column(name = "amendment_stage_1")
    private Integer amendmentSizeDebateOne;
    @Column(name = "amendment_stage_2")
    private Integer amendmentSizeDebateTwo;
    @Column(name = "amendment_stage_3")
    private Integer amendmentSizeDebateThree;
    @Column(name = "amendment_stage_4")
    private Integer amendmentSizeDebateFour;

}
