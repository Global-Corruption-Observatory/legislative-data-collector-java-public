SELECT bmt.record_id, public_hearing_date
FROM public_hearing as ph
    JOIN australia_spec_vars asv ON ph.country_spec_id = asv.id
    JOIN bill_main_table bmt on bmt.id = asv.record_id
ORDER BY ph.public_hearing_date, public_hearing_date;
