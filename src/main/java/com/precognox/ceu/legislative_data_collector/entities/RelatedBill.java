package com.precognox.ceu.legislative_data_collector.entities;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Embeddable;

@Data
@Embeddable
@Getter
@Setter
@NoArgsConstructor

public class RelatedBill {

    private String relatedBillId;

    private String relatedBillTitle;

    private String relatedBillRelationship;
}
