SELECT bmt_affected.record_id  AS record_id,
       bmt_modifying.bill_id    AS affecting_bill_id,
       bmt_modifying.record_id AS affecting_record_id
FROM affected_laws
         JOIN bill_main_table bmt_modifying ON bmt_modifying.id = affected_laws.record_id
         JOIN bill_main_table bmt_affected ON bmt_affected.bill_id = affected_laws.modified_law_id
ORDER BY record_id, affecting_record_id;
