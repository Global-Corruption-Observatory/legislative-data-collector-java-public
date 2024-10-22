package com.precognox.ceu.legislative_data_collector.entities.australia;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Embeddable;
import java.time.LocalDate;

@Getter
@Setter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class PublicHearing {

    LocalDate publicHearingDate;
}
