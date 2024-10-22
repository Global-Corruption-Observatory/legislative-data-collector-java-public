SELECT bmt.record_id, modified_law_id
FROM legislative_data_pl2.affected_laws
JOIN legislative_data_pl2.bill_main_table bmt
    ON bmt.id = affected_laws.record_id
ORDER BY bmt.record_id;