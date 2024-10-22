package com.precognox.ceu.legislative_data_collector.common;

import com.opencsv.CSVWriter;
import com.opencsv.ResultSetHelperService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Exports the current database's contents to CSV files for delivery. The SQL scripts for the current country
 * must be present under the resources of this app, see the EXPORT_SCRIPT_PATH variable. The results will be placed
 * in the current directory's datasets/ folder.
 */
@Slf4j
@Service
public class DatasetExporter {

    private final JdbcTemplate jdbcTemplate;
    private final String dbSchema;
    private final ResultSetHelperService resultSetHelperService;
    private final PathMatchingResourcePatternResolver resourceResolver;
    private final String country;
    private final ConsistencyCheckRunner consistencyCheckRunner;

    //path of scripts under resources folder
    private static final String EXPORT_SCRIPTS_PATH = "/db_export_scripts/all_countries";
    private static final String COUNTRY_SPEC_EXPORT_SCRIPTS_PATH = "/db_export_scripts/${countryCode}";

    //date format inside CSV files
    private static final String CSV_DATE_FORMAT = "yyyy-MM-dd";

    //other CSV params
    private static final boolean TRIM = true;
    private static final boolean INCLUDE_COLUMN_NAMES = true;
    private static final boolean APPLY_QUOTES_TO_ALL = false;

    @Autowired
    public DatasetExporter(
            JdbcTemplate jdbcTemplate,
            ConsistencyCheckRunner consistencyCheckRunner,
            @Value("${spring.jpa.properties.hibernate.default_schema}") String dbSchema) {
        this.jdbcTemplate = jdbcTemplate;
        this.dbSchema = dbSchema;
        this.consistencyCheckRunner = consistencyCheckRunner;

        this.resultSetHelperService = new ResultSetHelperService();
        this.resultSetHelperService.setDateFormat(CSV_DATE_FORMAT);
        this.resourceResolver = new PathMatchingResourcePatternResolver(getClass().getClassLoader());

        this.country = System.getenv("COUNTRY");
    }

    @SneakyThrows
    @Transactional
    public void export() {
        log.info("Exporting dataset to CSV...");

        String csvFolder = Constants.getExportFolder();
        Files.createDirectories(Path.of(csvFolder));

        runChecks(csvFolder);
        exportCsv(csvFolder);
    }

    private void runChecks(String csvFolder) {
        consistencyCheckRunner.runChecks(csvFolder);
    }

    @SneakyThrows
    private void exportCsv(String csvFolder) {
        List<Resource> exportScripts = new ArrayList<>();
        Collections.addAll(exportScripts, resourceResolver.getResources(EXPORT_SCRIPTS_PATH + "/*.sql"));

        String countrySpecFolder = StringSubstitutor.replace(
                COUNTRY_SPEC_EXPORT_SCRIPTS_PATH, Map.of("countryCode", country)
        );

        if (resourceResolver.getResource(countrySpecFolder).exists()) {
            Collections.addAll(exportScripts, resourceResolver.getResources(countrySpecFolder + "/*.sql"));
        }

        Connection dbConnection = jdbcTemplate.getDataSource().getConnection();
        dbConnection.setSchema(dbSchema);

        for (Resource exportScript : exportScripts) {
            String csvName = exportScript.getFilename().replace(".sql", ".csv");
            String csvPath = csvFolder + "/" + csvName;
            log.info("Exporting {}...", csvPath);

            try (CSVWriter csvWriter = new CSVWriter(new FileWriter(csvPath));
                 InputStream scriptFileStream = exportScript.getInputStream()) {
                csvWriter.setResultService(resultSetHelperService);
                String querySql = IOUtils.toString(scriptFileStream, StandardCharsets.UTF_8);

                try {
                    ResultSet sqlResultSet = dbConnection
                            .createStatement()
                            .executeQuery(querySql);

                    csvWriter.writeAll(sqlResultSet, INCLUDE_COLUMN_NAMES, TRIM, APPLY_QUOTES_TO_ALL);
                } catch (IOException e) {
                    log.error("Failed to write file: " + csvPath, e);
                } catch (SQLException e) {
                    log.error("Exception for query: " + querySql, e);
                }
            }
        }
    }

}
