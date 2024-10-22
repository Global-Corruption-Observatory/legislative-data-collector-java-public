SELECT
    record_id,
    CASE WHEN withdrawn = true THEN 'true' WHEN withdrawn = false THEN 'false' END withdrawn,
    CASE WHEN lapsed = true THEN 'true' WHEN lapsed = false THEN 'false' END lapsed
FROM india_spec_vars
ORDER BY record_id;
