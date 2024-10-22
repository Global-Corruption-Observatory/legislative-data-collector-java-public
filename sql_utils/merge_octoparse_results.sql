SELECT bill_id, status,
       CASE
           WHEN status = 'Granted' THEN 'PASS'
           WHEN status = 'Bill withdrawn' THEN 'REJECT'
           WHEN status LIKE 'Fell%' THEN 'REJECT'
           WHEN status = 'Awaiting' THEN 'ONGOING'
           WHEN status LIKE 'Next Stage:%' THEN 'ONGOING' END AS mapped_bill_status
FROM legislative_data_5.octoparse_results;
