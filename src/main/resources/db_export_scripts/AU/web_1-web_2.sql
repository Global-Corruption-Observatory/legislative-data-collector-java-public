SELECT
    record_id,
    bill_id,
    law_id AS web_2_link,
    bill_page_url,
    bill_title
FROM bill_main_table WHERE law_id IS NOT NULL
ORDER BY record_id;