SELECT record_id,
      bill_id,
      bill_page_url,
      bill_title,
      origin_type,
      CASE WHEN original_law = true THEN 'true' WHEN original_law = false THEN 'false' END original_law,
      procedure_type_standard,
      procedure_type_national,
      bill_type,
      law_type, --todo only used for usa, merge with bill type?
      type_of_law_eng,
      bill_status,
      law_id,
      modified_laws_count,
      affecting_laws_count,
      affecting_laws_first_date,
      date_introduction,
      stages_count,
      date_passing,
      date_entering_into_force,
      committee_count,
      committee_date,
      committee_hearing_count,
      ia_dummy,
      bill_size,
      replace(bill_text_url,'á','a') as bill_text_url,
      law_size,
      replace(law_text_url,'á','a') as law_text_url,
      amendment_count,
      plenary_size,
      final_vote_for,
      final_vote_against,
      final_vote_abst
FROM bill_main_table
ORDER BY record_id;
