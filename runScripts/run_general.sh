#!/bin/bash
export DB_URL=jdbc:postgresql://<postgres-internal-docker-ip>:5437/legislative_data
export DB_USER=<POSTGRES-USER>
export DB_PASSWORD=<POSTGRES-PASSWORD>
export JAR_PATH=../build/libs/ceu-legislative-data-collector-0.0.1-SNAPSHOT.jar

# COUNTRY and DB_SCHEMA env variables must be set before this runs
if [ -z "$COUNTRY" ] || [ -z "$DB_SCHEMA" ]
then
  echo "COUNTRY or DB_SCHEMA environment variable is not set, please run one of the country-specific start scripts"
else
  java -Xmx6G -jar "$JAR_PATH" | tee "$COUNTRY-$(date '+%Y-%m-%d_%H:%M:%S').log"
fi
