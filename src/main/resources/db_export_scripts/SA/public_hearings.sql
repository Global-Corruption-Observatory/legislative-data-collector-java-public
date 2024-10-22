SELECT bmt.record_id,
       hearing_title,
       hearing_date,
       hearing_submission_count
FROM sa_public_hearings
         JOIN bill_main_table bmt
              ON cast(sa_public_hearings.record_id as integer) = bmt.id
ORDER BY record_id;