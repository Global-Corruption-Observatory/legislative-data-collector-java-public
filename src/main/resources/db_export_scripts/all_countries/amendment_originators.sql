SELECT
    bmt.record_id,
    amendments.amendment_id,
    originator_name AS amendment_originator,
    originator_affiliation AS amendment_originator_aff
FROM amendment_originators
    JOIN amendments ON amendment_originators.amendment_id = amendments.id
    JOIN bill_main_table bmt ON bmt.id = amendments.record_id
ORDER BY bmt.record_id, amendments.amendment_id, originator_name;
