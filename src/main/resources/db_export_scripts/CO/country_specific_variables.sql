SELECT bmt.record_id,
       bill_status_co,
       origin_type_co,
       bill_type_co,
       bill_main_topic,
       bill_secondary_topic,
       house_bill_id,
       senate_bill_id,
       procedural_defect_dummy,
       gazette_number_stage_0,
       gazette_number_stage_1,
       gazette_number_stage_2,
       gazette_number_stage_3,
       gazette_number_stage_4,
       gazette_number_stage_5,
       gazette_number_stage_6,
       amendment_stage_1,
       amendment_stage_2,
       amendment_stage_3,
       amendment_stage_4
FROM colombia_spec_vars c
INNER JOIN bill_main_table bmt
    ON c.record_id = bmt.id
ORDER BY bmt.record_id;
