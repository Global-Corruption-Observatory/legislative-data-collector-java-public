## Pyetl Config Guide
A python script reads the config file and execute the commands. These config files are written in **json**. <br>
The basic idea is to read one record from a table where the page sources are stored, parse it according to the config and save to the result table.

- config_name
    - docker-compose env
- config_template
    - Common parts of the config files, can be moved to a template
- connection_data
    - Database connection information (ip address, port, user, password, database nane, schema name)
    - Sql query (psql) to get the page sources from the database.
- target_connection_data
    - Database connection information (ip address, port, user, password, database nane, schema name)
    - Insert query for storing the parsed page in the result table.
- items_content_script
    - column of the result query you want to refer.
    - Example: dbRow[0]
    - items
    - List of key-value pairs, each refers to one variable or an array of variables.

#### Items
- key
    - Refers to the name of the column in the database table.
    - Required
- path
    - jsonPath
    - xpath
    - css
    - regex
    - static
    - column and column_contains
        - use with script: html_table_to_dict_list(text)
- script
    - Run python script on the result of the path field
    - Chance to refer to query result (dbRow[0])
    - Not required
- multi_value
    - Set true if another object is need to be parsed
    - Not required, default value: false
- multi_item
    - Set true to save each item as a new database instance.
    - Not required, default value: false
- default_value