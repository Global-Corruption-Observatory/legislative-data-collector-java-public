package com.precognox.ceu.legislative_data_collector.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Embeddable;
import java.time.LocalDate;

@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class LegislativeStage {
    private Integer stageNumber;
    private LocalDate date;
    private String name;
    private Integer debateSize;

    public LegislativeStage(Integer stageNumber, LocalDate date, String name) {
        this.stageNumber = stageNumber;
        this.date = date;
        this.name = name;
    }

    public LegislativeStage(Integer stageNumber, String name) {
        this.stageNumber = stageNumber;
        this.name = name;
    }
}
