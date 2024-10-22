SELECT
    bmt.record_id,
    committee_name,
    committee_role,
    committees.committee_date
FROM committees
  JOIN bill_main_table bmt on bmt.id = committees.record_id
ORDER BY bmt.record_id;
