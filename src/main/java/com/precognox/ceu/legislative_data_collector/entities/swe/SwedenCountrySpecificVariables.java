package com.precognox.ceu.legislative_data_collector.entities.swe;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import lombok.Data;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Data
@Entity
@Table(name = "sweden_spec_vars")
@ToString(exclude = "legislativeDataRecord")
public class SwedenCountrySpecificVariables {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "record_id")
    private LegislativeDataRecord legislativeDataRecord;

    private String forslagspunkterPageUrl;

    @Column(name = "stage1_text_url")
    private String stage1TextUrl;

    private String affectingLawsPageUrl;

    private String reportId;

}
