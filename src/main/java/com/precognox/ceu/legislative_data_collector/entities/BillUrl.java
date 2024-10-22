package com.precognox.ceu.legislative_data_collector.entities;

import com.precognox.ceu.legislative_data_collector.hungary.Utils;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@Entity
@NoArgsConstructor
@Table(name = "bill_links")
public class BillUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Integer id;

    @Enumerated(EnumType.STRING)
    private Country country;

    private String url;

    /**
     * User for duplication filtering of bills, the transient parameters are removed from the URL (session IDs,
     * auth tokens, etc.).
     */
    private String cleanUrl;

    public BillUrl(Country country, String url) {
        this.country = country;
        this.url = url;
        this.cleanUrl = Utils.cleanUrl(url);
    }

}
