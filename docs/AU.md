## Australia

### Recommended run mode
1. Run sql script: `truncate legislative_data_au.bill_main_table cascade; select setval('legislative_data_au.au_generic_id_seq', 1)`
2. Set `COLLECT_AND_PARSE_WEB_1` to `TRUE`
3. If there is no Web 2 data yet, set COLLECT_WEB_2 to `TRUE`.
4. Set `PARSE_WEB_2` to `TRUE`
5. run the project

### Environment Variables:
- `COLLECT_AND_PARSE_WEB_1` (default value: `TRUE`) - In this case, Opensearch is not necessary, but it is much slower.
    - `ENABLE_WEB1_CONFIG_RESULT_CACHE` (default value: `TRUE`)
    - `CONFIG_SERVICE_URL` Python downloader API url
- `PARSE_WEB_1` (default value: `FALSE`) - It works with data saved in Opensearch saved by running config.
    - `OPENSEARCH_URL` 
    - `WEBSITE_1_TOTAL_RECORD_FROM_OPENSEARCH` (default value: `5897`) - Set this value after recollection. (The number of records on the page must also be the same. That value can also be copied.) It is important because of paging.
- `HEADLESS_PLAYWRIGHT` (default value: `TRUE`)
- `COLLECT_WEB_2` (default value: `TRUE`) - It is recommended to run with HEADLESS_PLAYWRIGHT=FALSE, as the collection is often interrupted. In this case, it is worth stopping and turning the pages manually.
- `RECOLLECT_WEB_2_LINKS` (default value: `FALSE`)
- `PARSE_WEB_2` (default value: `TRUE`)

### Related links:
- Annotation: `https://docs.google.com/document/d/1Z9KOrN2bNDvg1v7WRuJvOQGckH4mH16F`
- Website 1: `https://www.aph.gov.au/Parliamentary_Business/Bills_Legislation/Bills_Search_Results?t=A`
- Website 1 Members: `https://www.aph.gov.au/Senators_and_Members/Parliamentarian_Search_Results?q=&mem=1&sen=1&par=-1&gen=0&ps=12`
- Website 2: `https://www.legislation.gov.au/`
