package com.precognox.ceu.legislative_data_collector.entities.chile;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import lombok.Data;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "chile_spec_vars")
@ToString(exclude = "legislativeDataRecord")
public class ChileCountrySpecificVariables {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "record_id")
    private LegislativeDataRecord legislativeDataRecord;


    @Column(name = "law_id2")
    private String normId;
    private LocalDate datePublication;
    private String affectedOrganisms;
    private String lawTitle;
    private String billMainTopic;
    private String numberEnactedLaw;
    private String billSummary;
    private LocalDate dateFinalIntoForce;
    @Column(name = "bill_type_ch")
    private String billTypeChile;
    @Column(name = "bill_text_identification_error")
    private Boolean billTextError;
    private LocalDate terminationDate;
    @Column(name = "date_entering_into_force_last_version")
    private LocalDate dateEnteringForceLastVersion;

}
