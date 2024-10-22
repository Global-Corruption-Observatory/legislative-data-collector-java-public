SELECT
    bill_main_table.record_id,
    CASE WHEN unified_law = true THEN 'true' WHEN unified_law = false THEN 'false' END unified_law,
    unified_law_references,
    gazette_number
FROM bulgaria_spec_vars
    JOIN bill_main_table ON bill_main_table.id = bulgaria_spec_vars.record_id
ORDER BY bill_main_table.record_id;
