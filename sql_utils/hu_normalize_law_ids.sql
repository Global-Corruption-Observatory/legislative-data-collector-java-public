update affected_laws
set modified_law_id = regexp_replace(modified_law_id, '(\d{4})\.\s?évi ([IVXLCM]+)\.\s?törvény', '\1/\2')
where modified_law_id ~* '(\d{4})\.\s?évi ([IVXLCM]+)\.\s?törvény';

update bill_main_table bmt
set law_id = concat(date_part('year', date_passing), '/', law_id)
where law_id is not null and date_passing is not null;
