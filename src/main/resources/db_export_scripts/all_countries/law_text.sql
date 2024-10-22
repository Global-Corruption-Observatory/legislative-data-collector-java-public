SELECT record_id, law_text_url, law_size, law_text
FROM bill_main_table
WHERE law_text IS NOT NULL
ORDER BY record_id;
