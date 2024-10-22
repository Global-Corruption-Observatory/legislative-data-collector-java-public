SELECT
    bmt.record_id,
    amendments.id,
    replace(amendment_text_url, 'á', 'a') AS amendment_text_url,
    amendment_text
FROM amendments
    JOIN bill_main_table bmt ON bmt.id = amendments.record_id
WHERE amendment_text IS NOT NULL
ORDER BY record_id, amendments.id;
