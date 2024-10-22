package com.precognox.ceu.legislative_data_collector.entities.jo;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "jordan_spec_vars")
@ToString(exclude = "legislativeDataRecord")
public class JoCountrySpecificVariables {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "record_id")
    private LegislativeDataRecord legislativeDataRecord;

    private String category;
    private String legStatus;
    private Integer relatedTotal;
    private Integer relatedRegulation;
    private Integer relatedDirections;
    private Integer relatedDecisions;
    private Integer relatedExplanation;
    private Integer affectingLawsSizeTotal;
    private Integer affectingLawsTotalArticleCount;
}
