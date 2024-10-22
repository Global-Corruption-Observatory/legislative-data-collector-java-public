--copy
--  (
--SELECT record_id, raw_source_url, raw_source FROM legislative_data_br.bill_main_table
--) to '/shared/raw_sources.csv' delimiter ',' CSV HEADER ENCODING 'utf-8';

--copy
--  (
--SELECT amendment_id, amendment_originator, amendment_originator_aff FROM legislative_data_br.amendments
--) to '/shared/amendment_originators.csv' delimiter ',' CSV HEADER ENCODING 'utf-8';
