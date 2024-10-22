SELECT record_id,
    bill_text_general_justification
FROM bill_main_table
WHERE bill_text_general_justification IS NOT NULL
ORDER BY record_id;
