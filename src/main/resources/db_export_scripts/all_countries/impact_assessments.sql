SELECT
    bmt.record_id,
    ia_title,
    ia_date,
    ia_size,
    original_url
FROM impact_assessments
    JOIN bill_main_table bmt on bmt.id = impact_assessments.record_id
ORDER BY record_id;
