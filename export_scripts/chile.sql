copy
    (
        SELECT DISTINCT
            affecting_record_id AS record_id,
            bmt.law_id AS modified_law_id,
            bmt.record_id AS modified_record_id
        FROM legislative_data.affecting_laws_detailed aff
        INNER JOIN legislative_data.bill_main_table bmt on aff.record_id = bmt.record_id
        ORDER BY affecting_record_id
    ) to '/tmp/modified_laws.csv' delimiter ',' CSV HEADER ENCODING 'utf-8';
