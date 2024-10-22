-- second join is needed to include the modified law's record ID
SELECT bmt1.record_id, modified_law_id, bmt2.record_id AS modified_record_id
FROM affected_laws
    JOIN bill_main_table bmt1 ON bmt1.id = affected_laws.record_id
    LEFT JOIN bill_main_table bmt2 ON bmt2.law_id = affected_laws.modified_law_id
ORDER BY record_id, modified_record_id;
