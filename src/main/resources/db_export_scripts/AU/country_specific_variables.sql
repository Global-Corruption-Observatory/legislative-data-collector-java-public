SELECT
    bmt.record_id,
    asv.bill_summary,
    asv.portfolio,
    asv.em_dummy,
    asv.related_bills_count,
    asv.public_hearing_count,
    asv.public_hearing_submission_count,
    asv.public_hearing_government_response_date,
    asv.act_number
FROM bill_main_table bmt
    LEFT JOIN australia_spec_vars asv ON bmt.id = asv.record_id
ORDER BY bmt.record_id;
