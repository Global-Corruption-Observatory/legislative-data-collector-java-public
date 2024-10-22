SELECT record_id, bill_text_url, bill_size, bill_text
FROM bill_main_table
WHERE bill_text IS NOT NULL
ORDER BY record_id;
