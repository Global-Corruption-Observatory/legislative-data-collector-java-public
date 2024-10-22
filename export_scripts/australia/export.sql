copy
(
    SELECT DISTINCT
                  bmt.record_id,
                  affecting_law_id,
                  bmt2.record_id as affecting_record_id
              FROM legislative_data_au.affecting_laws_detailed
                    right JOIN legislative_data_au.bill_main_table bmt on bmt.id = affecting_laws_detailed.record_id
                    right JOIN legislative_data_au.bill_main_table bmt2 on bmt2.id = affecting_laws_detailed.affecting_record_id
                    ORDER BY bmt.record_id
    ) to '/shared/affecting_laws.csv' delimiter ',' CSV HEADER ENCODING 'utf-8';
