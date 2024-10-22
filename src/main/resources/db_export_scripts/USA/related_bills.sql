SELECT bmt.record_id, related_bill_id, related_bill_title, related_bill_relationship
FROM related_bills
  JOIN bill_main_table bmt on bmt.id = related_bills.record_id
ORDER BY bmt.record_id;
