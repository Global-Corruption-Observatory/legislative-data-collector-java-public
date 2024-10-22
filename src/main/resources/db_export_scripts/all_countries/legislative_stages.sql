SELECT bmt.record_id, stage_number, date, name, debate_size AS size_stage
FROM legislative_stages
   JOIN bill_main_table bmt on bmt.id = legislative_stages.record_id
ORDER BY bmt.record_id, stage_number;
