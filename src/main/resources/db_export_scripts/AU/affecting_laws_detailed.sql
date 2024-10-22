SELECT DISTINCT
    bmt.record_id,
    ald.modified_article,
    ald.affecting_law_id,
    bmt2.record_id AS affecting_record_id,
    ald.affecting_article AS modifying_article,
    ald.affecting_date
FROM affecting_laws_detailed as ald
    RIGHT JOIN bill_main_table bmt ON bmt.id = ald.record_id
    RIGHT JOIN bill_main_table bmt2 ON bmt2.law_id = ald.affecting_law_id
ORDER BY bmt.record_id;
