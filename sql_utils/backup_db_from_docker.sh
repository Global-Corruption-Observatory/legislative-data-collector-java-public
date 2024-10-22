# change these depending on the country
container_name="legislative-postgres"
user="root"
db="legislative_data"
schema="legislative_data_usa"
compression=6
records_file="usa_records.sql"
sources_file="usa_page_sources.sql"

echo "Dumping records..."
docker exec $container_name pg_dump \
        --username=$user \
        --dbname=$db \
        --schema=$schema \
        --exclude-table=page_source \
        --exclude-table=errors \
        --exclude-table=databasechangelock \
        --exclude-table=databasechangeloglock \
        --format=custom \
        --compress=$compression \
        -f $records_file

echo "Copying dump file from container..."
docker cp $container_name:/$records_file .

echo "Deleting copied file from container"
docker exec $container_name rm $records_file

echo "Dumping page sources..."
docker exec $container_name pg_dump \
        --username=$user \
        --dbname=$db \
        --schema=$schema \
        --table=page_source \
        --format=custom \
        --compress=$compression \
        -f $sources_file

echo "Copying dump file from container..."
docker cp $container_name:/$sources_file .

echo "Deleting copied file from container"
docker exec $container_name rm $sources_file
