SELECT
    bmt.record_id,
    amendments.id,
    amendment_id,
    date AS amendment_date,
    amendment_plenary,
    amendment_stage_number,
    amendment_stage_name,
    amendment_committee_name,
    amendment_outcome,
    amendment_vote_for,
    amendment_vote_against,
    amendment_vote_abst,
    replace(amendment_text_url, 'á', 'a') as amendment_text_url
FROM amendments
    JOIN bill_main_table bmt on bmt.id = amendments.record_id
ORDER BY record_id, amendments.id;
