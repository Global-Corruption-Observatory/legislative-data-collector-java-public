package com.precognox.ceu.legislative_data_collector.entities.change_detector;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"id"})
@Table(name = "PAGE_SOURCE_DIFF")
public class PageSourceDiff {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "country")
    private Country country;

    @Column(name = "page_type")
    private String pageType;

    @Column(name = "page_url")
    private String pageUrl;

    @Column(name = "diff_operation")
    private String diffOperation;

    @Column(name = "diff_text")
    private String diffText;

}
