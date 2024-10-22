-- 3 example queries to remove duplicate records from database tables. Change the schema and table names as needed.
delete
from legislative_data_india.bill_main_table
where record_id in (select record_id
                    from (select record_id, bill_id, row_number() over (order by bill_id) as row
                          from legislative_data_india.bill_main_table
                          where bill_id in (select bill_id
                                            from legislative_data_india.bill_main_table
                                            group by bill_id
                                            having count(*) > 1
                                            order by count(*) desc)) q
                    where q.row % 2 = 0);

-- only if the table has primary key:
DELETE FROM sweden2.sweden_spec_vars
WHERE ID NOT IN
    (
    SELECT MAX(ID) AS MaxRecordID
    FROM sweden2.sweden_spec_vars
    GROUP BY record_id
);

-- delete by ctid: only deletes duplicates of two (run repeatedly if records are duplicated more than twice)
DELETE FROM brazil_new.page_source
WHERE ctid IN (SELECT max(ctid)
               FROM   brazil_new.page_source
               GROUP BY clean_url
               HAVING count(*) > 1
);

DELETE FROM india_new_site.originators
WHERE ctid NOT IN (
  SELECT MIN(ctid)
  FROM india_new_site.originators
  GROUP BY record_id, originator_affiliation  -- Replace or add columns that determine duplicates
) and originator_name is null;
