package com.precognox.ceu.legislative_data_collector.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@NoArgsConstructor
@ToString(exclude = {"dataRecord", "text"})
@Table(name = "IMPACT_ASSESSMENTS")
public class ImpactAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ia_title")
    private String title;

    @Column(name = "ia_date")
    private LocalDate date;

    private String originalUrl;

    @Column(name = "ia_text")
    private String text;

    @Column(name = "ia_size")
    private Integer size;

    @ManyToOne
    @JoinColumn(name = "record_id")
    private LegislativeDataRecord dataRecord;

    public ImpactAssessment(String title, LocalDate date, String text, Integer size) {
        this.title = title;
        this.date = date;
        this.text = text;
        this.size = size;
    }
}
