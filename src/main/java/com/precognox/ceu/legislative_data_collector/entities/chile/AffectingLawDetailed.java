package com.precognox.ceu.legislative_data_collector.entities.chile;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "AFFECTING_LAWS_DETAILED")
@ToString(exclude = "dataRecord")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AffectingLawDetailed {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "record_id")
    @EqualsAndHashCode.Include
    private LegislativeDataRecord dataRecord;
    @EqualsAndHashCode.Include
    private String modifiedArticle;

    @ManyToOne
    @JoinColumn(name = "affecting_record_id")
    private LegislativeDataRecord affectingRecord;
    @EqualsAndHashCode.Include
    private String affectingLawId;
    @EqualsAndHashCode.Include
    private String affectingArticle;
    @EqualsAndHashCode.Include
    private LocalDate affectingDate;
}
