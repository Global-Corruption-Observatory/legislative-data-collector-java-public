package com.precognox.ceu.legislative_data_collector.entities.sa;
import lombok.Data;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.JoinColumn;
import java.util.ArrayList;
import java.util.List;

@Data
public class SouthAfricaCountrySpecificVariables {
    private Integer publicHearingCount;
    private String lawTitle;
    private String govPageUrl;

    @Embedded
    @ElementCollection
    @LazyCollection(LazyCollectionOption.FALSE)
    @CollectionTable(name = "SA_PUBLIC_HEARINGS", joinColumns = @JoinColumn(name = "record_id"))
    private List<PublicHearing> publicHearings = new ArrayList<>();
}
