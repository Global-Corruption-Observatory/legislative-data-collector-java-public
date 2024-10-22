insert into bulgaria_new_pk.bill_main_table(
    record_id,
    country,
    bill_id,
    law_id,
    bill_page_url,
    bill_type,
    bill_title,
    origin_type,
    stages_count,
    bill_status,
    date_introduction,
    committee_date,
    date_passing,
    date_entering_into_force,
    committee_count,
    committee_hearing_count,
    committee_depth,
    ia_dummy,
    amendment_count,
    bill_text,
    bill_size,
    bill_text_url,
    law_text,
    law_text_url,
    law_size,
    modified_laws_count,
    affecting_laws_count,
    affecting_laws_first_date,
    procedure_type_standard,
    procedure_type_eng,
    procedure_type_national,
    plenary_size,
    final_vote_for,
    final_vote_against,
    final_vote_abst,
    raw_source_url,
    raw_source,
    has_royal_assent,
    has_programme_motion,
    has_money_resolution,
    has_ways_and_means_resolution,
    bill_text_general_justification
)
select
    record_id,
    country,
    bill_id,
    law_id,
    bill_page_url,
    bill_type,
    bill_title,
    origin_type,
    stages_count,
    bill_status,
    date_introduction,
    committee_date,
    date_passing,
    date_entering_into_force,
    committee_count,
    committee_hearing_count,
    committee_depth,
    ia_dummy,
    amendment_count,
    bill_text,
    bill_size,
    bill_text_url,
    law_text,
    law_text_url,
    law_size,
    modified_laws_count,
    affecting_laws_count,
    affecting_laws_first_date,
    procedure_type_standard,
    procedure_type_eng,
    procedure_type_national,
    plenary_size,
    final_vote_for,
    final_vote_against,
    final_vote_abst,
    raw_source_url,
    raw_source,
    has_royal_assent,
    has_programme_motion,
    has_money_resolution,
    has_ways_and_means_resolution,
    bill_text_general_justification
from bulgaria.bill_main_table;

insert into legislative_data.bulgaria_new_pk.affected_laws (
    select bmt.id, al.modified_law_id
    from legislative_data.bulgaria.affected_laws al
    join legislative_data.bulgaria_new_pk.bill_main_table bmt
    on al.record_id = bmt.record_id
);

insert into legislative_data.bulgaria_new_pk.bulgaria_spec_vars
    (record_id, unified_law, unified_law_references, gazette_number) (
        select bmt.id, orig.unified_law, orig.unified_law_references, orig.gazette_number
        from legislative_data.bulgaria.bulgaria_spec_vars orig
        join legislative_data.bulgaria_new_pk.bill_main_table bmt
        on orig.record_id = bmt.record_id
);

insert into legislative_data.bulgaria_new_pk.committees
    (record_id, committee_name, committee_role) (
        select bmt.id, orig.committee_name, orig.committee_role
        from legislative_data.bulgaria.committees orig
        join legislative_data.bulgaria_new_pk.bill_main_table bmt
        on orig.record_id = bmt.record_id
);

insert into legislative_data.bulgaria_new_pk.legislative_stages
    (record_id, stage_number, date, name, debate_size) (
        select bmt.id, orig.stage_number, orig.date, orig.name, orig.debate_size
        from legislative_data.bulgaria.legislative_stages orig
        join legislative_data.bulgaria_new_pk.bill_main_table bmt
        on orig.record_id = bmt.record_id
);

insert into legislative_data.bulgaria_new_pk.originators
    (record_id, originator_name, originator_affiliation) (
        select bmt.id, orig.originator_name, orig.originator_affiliation
        from legislative_data.bulgaria.originators orig
        join legislative_data.bulgaria_new_pk.bill_main_table bmt
        on orig.record_id = bmt.record_id
);
