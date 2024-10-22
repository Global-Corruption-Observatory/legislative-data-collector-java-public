-- edit the schema name before use
insert into hungary_data_update.amendments
(record_id,
 amendment_id,
 date,
 amendment_plenary,
 amendment_stage_number,
 amendment_stage_name,
 amendment_committee_name,
 amendment_outcome,
 amendment_vote_for,
 amendment_vote_against,
 amendment_vote_abst,
 amendment_text_url,
 amendment_text,
 amendment_page_url,
 amendment_title)
select bmt_new.id,
       amd_old.amendment_id,
       amd_old.date,
       amd_old.amendment_plenary,
       amd_old.amendment_stage_number,
       amd_old.amendment_stage_name,
       amd_old.amendment_committee_name,
       amd_old.amendment_outcome,
       amd_old.amendment_vote_for,
       amd_old.amendment_vote_against,
       amd_old.amendment_vote_abst,
       amd_old.amendment_text_url,
       amd_old.amendment_text,
       amd_old.amendment_page_url,
       amd_old.amendment_title
from hungary.amendments amd_old
         join hungary.bill_main_table bmt_old on bmt_old.id = amd_old.record_id
         join hungary_data_update.bill_main_table bmt_new on bmt_new.bill_id = bmt_old.bill_id
         left join hungary_data_update.amendments amd_new on amd_old.amendment_id = amd_new.amendment_id
where amd_new.amendment_id is null;
