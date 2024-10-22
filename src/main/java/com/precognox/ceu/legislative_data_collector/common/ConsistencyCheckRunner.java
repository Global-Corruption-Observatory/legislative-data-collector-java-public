package com.precognox.ceu.legislative_data_collector.common;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.List;

@Slf4j
@Service
public class ConsistencyCheckRunner {

    private final EntityManager entityManager;

    @Autowired
    public ConsistencyCheckRunner(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void runChecks(String outputFolder) {
        String outFilePath = outputFolder + "/consistency_checks.txt";

        try (InputStream resourceAsStream = getClass().getResourceAsStream("/consistency_checks.csv");
                PrintWriter outWriter = new PrintWriter(outFilePath)) {
            if (resourceAsStream != null) {
                log.info("Writing consistency checks to file {}", outFilePath);

                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .build()
                        .parse(new InputStreamReader(resourceAsStream));

                List<CSVRecord> checks = parser.getRecords();
                outWriter.println("Running " + checks.size() + " checks");

                for (int i = 0; i < checks.size(); i++) {
                    CSVRecord record = checks.get(i);
                    String countQuery = record.get("query").toLowerCase().replace("select *", "select count(*)");
                    BigInteger count = (BigInteger) entityManager.createNativeQuery(countQuery).getSingleResult();

                    if (count.intValue() == 0) {
                        outWriter.printf("%d/%d %s: OK (0 results)%n", i + 1, checks.size(), record.get("description"));
                    } else {
                        String limitQuery = record.get("query") + " LIMIT 10";
                        Query nativeQuery = entityManager.createNativeQuery(limitQuery, LegislativeDataRecord.class);
                        List<LegislativeDataRecord> resultList = nativeQuery.getResultList();

                        outWriter.printf(
                                "%d/%d %s: %d results%n",
                                i + 1,
                                checks.size(),
                                record.get("description"),
                                count
                        );

                        resultList.forEach(
                                r -> outWriter.println("\t- " + r.getRecordId() + " " + r.getBillPageUrl())
                        );
                    }
                }
            }
        } catch (IOException | PersistenceException e) {
            log.error("Failed to run consistency checks", e);
        }
    }

}
