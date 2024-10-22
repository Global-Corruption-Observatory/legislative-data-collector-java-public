package com.precognox.ceu.legislative_data_collector.india.entities;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "india_spec_vars")
@ToString(exclude = "legislativeDataRecord")
public class IndiaCountrySpecificVariables {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "record_id")
    private LegislativeDataRecord legislativeDataRecord;
    private Boolean withdrawn;
    private Boolean lapsed;

}
