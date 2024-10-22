SELECT bmt.record_id,
       committee_name,
       committee_role,
       committees.committee_date,
       committees.committee_hearing_count,
       number_of_public_hearings_committee
FROM committees
         JOIN bill_main_table bmt
              ON bmt.id = committees.record_id
ORDER BY bmt.record_id;