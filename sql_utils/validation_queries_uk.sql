-- should be 0
select * from legislative_data_5.bill_main_table where country = 'UK' and bill_id is null;
select * from legislative_data_5.bill_main_table where country = 'UK' and bill_title is null;
select * from legislative_data_5.bill_main_table where country = 'UK' and date_introduction is null;
select * from legislative_data_5.bill_main_table where country = 'UK' and bill_text_url is not null and bill_text is null;
select * from legislative_data_5.bill_main_table where country = 'UK' and law_text_url is not null and law_text is null;
select * from legislative_data_5.bill_main_table where country = 'UK' and bill_status = 'PASS' and law_id is null;
select * from legislative_data_5.bill_main_table where country = 'UK' and bill_status = 'REJECT' and law_id is not null;

-- should be > 1
select * from legislative_data_5.bill_main_table where country = 'UK' and law_id is not null;
select * from legislative_data_5.bill_main_table where country = 'UK' and law_text is not null;
select * from legislative_data_5.bill_main_table where country = 'UK' and bill_text is not null;
select * from legislative_data_5.bill_main_table where country = 'UK' and affecting_laws_count is not null;
select * from legislative_data_5.bill_main_table where country = 'UK' and affecting_laws_first_date is not null;
select * from legislative_data_5.bill_main_table where country = 'UK' and bill_status = 'PASS';
select * from legislative_data_5.bill_main_table where country = 'UK' and bill_status = 'REJECT';

-- todo calculate missing percentages for variables?
