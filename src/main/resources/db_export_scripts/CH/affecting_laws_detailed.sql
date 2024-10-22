SELECT
    bmt.record_id,
    modified_article,
    affecting_law_id,
    affecting_record_id,
    affecting_article AS modifying_article
FROM affecting_laws_detailed
    JOIN bill_main_table bmt ON affecting_laws_detailed.record_id = bmt.id
ORDER BY record_id;
