SELECT bmt.record_id, ia_text
FROM impact_assessments
JOIN bill_main_table bmt
    ON bmt.id = impact_assessments.record_id
ORDER BY bmt.record_id;
