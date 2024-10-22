SELECT DISTINCT
    bmt2.record_id AS record_id,
    bmt1.law_id AS modified_law_id,
    bmt1.record_id AS modified_record_id
FROM chile_data_update.affecting_laws_detailed aff
     INNER JOIN chile_data_update.bill_main_table bmt1 on aff.record_id = bmt1.id
     INNER JOIN chile_data_update.bill_main_table bmt2 on aff.affecting_record_id = bmt2.id
ORDER BY bmt2.record_id;
