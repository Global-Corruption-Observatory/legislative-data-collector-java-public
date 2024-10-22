SELECT bmt.record_id, originator_support_name
FROM originator_support_names
  JOIN bill_main_table bmt on bmt.id = originator_support_names.record_id
ORDER BY record_id;
