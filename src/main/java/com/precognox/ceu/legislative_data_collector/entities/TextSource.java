package com.precognox.ceu.legislative_data_collector.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"id"})
public class TextSource {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private Country country;
    private String textType;
    private String textContent;
    private String textIdentifier;
    private String downloadUrl;
}
