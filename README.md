## Important documentation
- Explanation of core legislative variables: https://docs.google.com/spreadsheets/d/1eaKGA1OQB9O--ojl6k7i42UG45Ag80p3RqcW8vJXsUk/edit?gid=0#gid=0
- Annotations by country: https://drive.google.com/drive/folders/1aeMTFyErbBqnaxkoHDg8xx8v7p183jZ9

## Architecture
Scraping is implemented in Java with a Spring Boot application. The collected data is stored in PostgreSQL. We used APIs or plain HTTP requests + JSoup where possible, and Selenium where pages were rendered with Javascript. Some tests are implemented in Kotlin. 

## Requirements
- OpenJDK 17: Install (https://adoptium.net/installation/linux/) or use [SDKMAN](https://sdkman.io/)
- Desktop environment required (to allow starting of browsers): `sudo apt install xfce4`
- Chromium browser: `sudo apt-get install chromium-browser`
- Chromedriver (matching version with Chromium): `sudo apt-get install chromium-chromedriver`
- PostgreSQL database running separately from the application: Install docker (https://docs.docker.com/engine/install/ubuntu/), then the database can be started based on the `docker-compose.yml` file in the root folder of the project

### Guide to upgrade Chrome and Chromedriver
- [Chrome & Chromedriver upgrade](docs/CHROMEDRIVER_UPGRADE)

## Build
- Use `build.sh`. 

## Running
- Edit `docker-compose.yml`, fill username and password
- `docker compose up -d` to start the database (if it's not already running somewhere else)
- Set the database parameters in `runScripts/run_general.sh` You can find the IP of the Postgres container with `docker inspect legislative-postgres` command, NetworkSettings section and set the IP to the `DB_URL` variable in the `run_general.sh` script
- From the `runScripts` folder, run a script for the country to scrape. All country-specific scripts reference the `run_general.sh` file, which sets the environment variables 
- The database schema will be created automatically and the steps of the scraping will start

## Environment variables
- `COUNTRY`: Two-letter code of the country to scrape 
- `DB_PASSWORD, DB_SCHEMA, DB_URL, DB_USER`: Parameters for the database (required)
- `PYETL_URL`, `PYETL_USER`, `PYETL_PASSWORD`: Parameters for the PyETL API used for scraping the Australian dataset (optional)

These are included in the run scripts with default values.

##  Country-specific environment variables:
- [Australia](docs/AU.md)

## Overview of the scraping process
General steps for all countries:
1. Collect the links for individual bill pages (these are stored in the `bill_links` table)
2. Using the links, download and store the raw sources of the bill pages (to the `page_source` table)
3. Parse the variables out of the stored sources and generate the legislative records (to the `bill_main_table` and related tables)
4. Parse the more complex variables (affected laws, amendments, etc.) after the main records are there - this is usually implemented in separate classes
5. Download texts of bills, laws, amendments, impact assessments fom PDF files separately, (the URLs are stored in previous steps)

There can be exceptions to this process, e.g. India doesn't have bill pages. It's recommended to fully complete the steps in this order. Handling interruptions and duplication filtering is built into all steps - records are identified and duplicates are filtered by the bill page URL, so the same records are not processed twice. Therefore, the processing can be stopped and restarted at any time, and already handled records are skipped.

## Updating the dataset/Collecting new bills
To collect the newest bills, the application must be started the same way as for the full collection. Existing bills will be skipped, and only the new ones will be downloaded and processed.

## Checking the dataset 
The `common/DatasetReporter` class calculates and prints statistics (percentages of empty variables) after the processing is finished. This calculation is added as the last step of the collection process for every country - it runs automatically after the bills have been collected.

## Delivering/exporting the dataset
There are SQL files `export_scripts` for producing the CSV files from the database. Minor changes might be necessary before running them (like changing the schema name). This step also runs automatically at the end of the collection process.

## Explanation of source code

### Significant classes 

#### entities/LegislativeDataRecord.java
This is the main class which represents a bill. It's fields correspond to the legislative variables. 

#### Application class (CeuLegislativeDataCollectorApplication.java)

#### Controllers

## Rough collection times for each country:
- Australia: 5 days
- Brazil: 5 days 
- Bulgaria: 4 hours 
- Chile: 1 week 
- Colombia: 3 days
- Germany: 3 days
- Hungary: 1 week 
- India: 4 hours 
- Poland: 4 days
- South Africa: 2 days
- Sweden: 1 day 
- UK: 3 days
- USA: 1 week

## Troubleshooting

### Jaunt error

When scraping the Hungarian parliament website, if you encounter an error about Jaunt being expired, please download the latest version from the [official website](https://jaunt-api.com/download.htm), replace the `jaunt1.6.1.jar` file in the `lib` folder, and rebuild the application. This library expires every month, so it might be necessary to update it.

For additional guides, please check the `docs` folder.
