## Russia

### Recommended run mode
A VPN is required to access the Russian website, e.g. a working one is `https://www.vyprvpn.com/`

Manual steps of the collection process:
1. Open in browser: `https://sozd.duma.gov.ru/oz/c?document_search%5BName%5D=&document_search%5BPlainText%5D=&document_search%5BTypeOfDocument%5D=&auth_cond%5BAuthor%5D=any&document_search%5BEventTypes%5D=&document_search%5BPlanningTypes%5D=&document_search%5BOtherTypes%5D=&document_search%5BOzDocsTypes%5D=&document_search%5BObjectOfLawmakingNumber%5D=&document_search%5BFormOfAct%5D=&date_period_from_DateStartEnd=&date_period_to_DateStartEnd=&document_search%5BDateStartEnd%5D=&document_search%5BSession%5D=&date_period_from_DateCreated=&date_period_to_DateCreated=&document_search%5BDateCreated%5D=&document_search%5Btype%5D=1#data_source_tab_pch`
2. Click to: `Законопроекты` -> `Найти` (Green button) -> `Printer icon` -> `Документ формата xlsx` (Sometimes the download is interrupted, in which case repeat the last two steps.)
3. Set the `EXPORTED_FILE_PATH` env variable to the path of the downloaded file
4. If you've previously ran the collection, and don't want to keep the old data, run the SQL command: `truncate legislative_data_ru.bill_main_table cascade; select setval('legislative_data_ru.ru_generic_id_seq', 1)`
5. Run the application

### Environment Variables:
- `EXPORTED_FILE_PATH` - Contains the path of the downloaded export (xlsx).

### Related links:
- Annotation: `https://docs.google.com/document/d/13QHZLFidGsl-gWjimvVX5NJKa9jvHBWoREER4Kv73hg/edit#heading=h.gjdgxs`
- Website: `https://sozd.duma.gov.ru/oz?b%5BNumberSpec%5D=&b%5BAnnotation%5D=&date_period_from_Year=&date_period_to_Year=&b%5BYear%5D=&cond%5BClassOfTheObjectLawmaking%5D=any&cond%5BThematicBlockOfBills%5D=any&cond%5BPersonDeputy%5D=any&cond%5BFraction%5D=any&b%5BFzNumber%5D=&b%5BNameComment%5D=&b%5BResolutionnumber%5D=&cond%5BRelevantCommittee%5D=any&b%5BfirstCommitteeCond%5D=and&cond%5BResponsibleCommittee%5D=any&b%5BsecondCommitteeCond%5D=and&cond%5BHelperCommittee%5D=any&cond%5BExistsEvents%5D=any&date_period_from_ExistsEventsDate=&date_period_to_ExistsEventsDate=&b%5BExistsEventsDate%5D=&cond%5BLastEvent%5D=any&date_period_from_MaxDate=&date_period_to_MaxDate=&b%5BMaxDate%5D=&cond%5BExistsDecisions%5D=any&date_period_from_DecisionsDateOfCreate=&date_period_to_DecisionsDateOfCreate=&b%5BDecisionsDateOfCreate%5D=&cond%5BLastDecisions%5D=any&cond%5BQuestionOfReference%5D=any&cond%5BSubjectOfReference%5D=any&b%5BconclusionRG%5D=&date_period_from_dateEndConclusionRG=&date_period_to_dateEndConclusionRG=&b%5BdateEndConclusionRG%5D=&cond%5BFormOfTheObjectLawmaking%5D=any&cond%5BinSz%5D=any&date_period_from_ResponseDate=&date_period_to_ResponseDate=&b%5BResponseDate%5D=&date_period_from_AmendmentsDate=&date_period_to_AmendmentsDate=&b%5BAmendmentsDate%5D=&b%5BSectorOfLaw%5D=&b%5BClassOfTheObjectLawmakingId%5D=34f6ae40-bdf0-408a-a56e-e48511c6b618#data_source_tab_b`