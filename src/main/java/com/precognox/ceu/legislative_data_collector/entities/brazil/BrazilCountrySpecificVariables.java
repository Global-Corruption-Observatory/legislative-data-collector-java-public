package com.precognox.ceu.legislative_data_collector.entities.brazil;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@NoArgsConstructor
@Table(name = "brazil_spec_vars")
public class BrazilCountrySpecificVariables {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "record_id")
    private LegislativeDataRecord legislativeDataRecord;

    private String camaraPageUrl;
    private String senadoPageUrl;
    private String votesPageUrl;
    private String stagesPageUrl;
    private LocalDate publicationDate;
    private String alternativeLawId;

    @ElementCollection
    @Column(name = "amendment_link")
    @CollectionTable(name = "br_amendment_page_links", joinColumns = @JoinColumn(name = "spec_vars_id"))
    private List<String> amendmentPageLinks = new ArrayList<>();

    @ElementCollection
    @Column(name = "alt_bill_id")
    @CollectionTable(name = "br_alternative_bill_ids", joinColumns = @JoinColumn(name = "spec_vars_id"))
    private List<String> alternativeBillIds = new ArrayList<>();

    @ElementCollection
    @Column(name = "affecting_law_id")
    @CollectionTable(name = "affecting_law_ids", joinColumns = @JoinColumn(name = "spec_vars_id"))
    private List<String> affectingLaws = new ArrayList<>();

    public BrazilCountrySpecificVariables(LegislativeDataRecord legislativeDataRecord) {
        this.legislativeDataRecord = legislativeDataRecord;
    }
}
