SELECT DISTINCT
    bmt.record_id,
    affecting_law_id,
    affecting_record_id
FROM affecting_laws_detailed
     JOIN bill_main_table bmt ON affecting_laws_detailed.record_id = bmt.id
ORDER BY record_id;
