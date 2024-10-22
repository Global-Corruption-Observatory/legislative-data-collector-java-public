package com.precognox.ceu.legislative_data_collector.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"id"})
public class PageSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private Country country;
    private String pageType;
    private String pageUrl;
    private String cleanUrl;
    private LocalDate collectionDate;
    private Integer size;
    private String rawSource;
    private String metadata;

    public PageSource(Country country, String pageType, String pageUrl, String rawSource) {
        this.country = country;
        this.pageType = pageType;
        this.pageUrl = pageUrl;
        this.rawSource = rawSource;

        this.size = rawSource.length();
        this.collectionDate = LocalDate.now();
    }

}
