package com.precognox.ceu.legislative_data_collector.entities.change_detector;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import lombok.*;

import javax.persistence.*;
import java.util.Date;
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"id"})
@Table(name = "PAGE_SOURCE_DIFF_RESULTS")
public class PageSourceDiffResults {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "country")
    private Country country;

    @Column(name = "page_type")
    private String pageType;

    @Column(name = "affected_pages")
    private int affectedPages;

    @Column(name = "comparison_date")
    private Date comparison_date;

    public PageSourceDiffResults(Country country, String pageType, int affectedPages) {
        this.country=country;
        this.pageType = pageType;
        this.affectedPages = affectedPages;
    }
}
