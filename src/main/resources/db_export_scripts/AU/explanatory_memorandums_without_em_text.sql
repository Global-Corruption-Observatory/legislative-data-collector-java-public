SELECT bmt.record_id, em_title, em_date, em_dummy, em_size
FROM australia_spec_vars asv
    JOIN bill_main_table bmt on bmt.id = asv.record_id
ORDER BY bmt.record_id;
