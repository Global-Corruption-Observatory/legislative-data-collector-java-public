select record_id,
       cosponsor_count,
       related_bills_count,
       amendment_stages_count
from bill_main_table
ORDER BY record_id;
