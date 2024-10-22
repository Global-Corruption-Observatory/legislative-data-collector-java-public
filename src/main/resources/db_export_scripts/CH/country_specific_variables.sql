SELECT
    bmt.record_id,
    law_id,
    law_id2,
    date_publication,
    affected_organisms,
    law_title,
    bill_main_topic,
    number_enacted_law,
    date_final_into_force,
    bill_summary,
    bill_type_ch
FROM bill_main_table bmt
    INNER JOIN chile_spec_vars c ON bmt.id = c.record_id
ORDER BY record_id;
