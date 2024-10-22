-- copy
--     (
--     SELECT b.record_id AS record_id, affecting AS affecting_law_id FROM
--         (SELECT a.modified_law_id AS affected, b.law_id AS affecting, b.record_id AS affecting_rid FROM legislative_data.affected_laws a INNER JOIN legislative_data.bill_main_table b ON b.record_id = a.record_id) AS affecting_with_law_ids
--         INNER JOIN legislative_data.bill_main_table b ON affecting_with_law_ids.affected = b.law_id
--     ) to '/shared/affecting_laws.csv' delimiter ',' CSV HEADER ENCODING 'utf-8';