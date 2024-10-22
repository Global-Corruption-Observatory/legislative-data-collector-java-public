SELECT bmt.record_id, c.bill_summary
FROM colombia_spec_vars c
INNER JOIN bill_main_table bmt
    ON c.record_id = bmt.id
ORDER BY bmt.record_id;