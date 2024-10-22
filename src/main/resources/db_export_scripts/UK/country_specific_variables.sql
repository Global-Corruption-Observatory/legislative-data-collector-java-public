SELECT
    record_id,
    has_royal_assent,
    has_programme_motion,
    has_money_resolution,
    has_ways_and_means_resolution
FROM bill_main_table
ORDER BY record_id;
