package com.precognox.ceu.legislative_data_collector.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Getter
@Setter
@Embeddable
@NoArgsConstructor
public class RawPageSource {

    @Column(name = "raw_source_url")
    private String url;
    private String rawSource;

}
