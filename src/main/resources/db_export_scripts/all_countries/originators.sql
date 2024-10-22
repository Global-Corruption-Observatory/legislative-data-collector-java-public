SELECT bmt.record_id, originator_name, originator_affiliation
FROM originators
  JOIN bill_main_table bmt on bmt.id = originators.record_id
ORDER BY bmt.record_id, originator_name;
