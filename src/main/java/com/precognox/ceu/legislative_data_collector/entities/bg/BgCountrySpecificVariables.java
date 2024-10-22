package com.precognox.ceu.legislative_data_collector.entities.bg;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bulgaria_spec_vars")
@ToString(exclude = "legislativeDataRecord")
public class BgCountrySpecificVariables {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "record_id")
    private LegislativeDataRecord legislativeDataRecord;

    private Boolean unifiedLaw;
    private String unifiedLawReferences;
    private String gazetteNumber;

}
