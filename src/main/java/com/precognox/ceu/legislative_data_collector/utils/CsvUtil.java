package com.precognox.ceu.legislative_data_collector.utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class CsvUtil {

    public static List<Map<String, String>> readCsv(String path) throws IOException {
        List<Map<String, String>> result = new ArrayList<>();
        readCsv(path, row -> result.add(row));
        return result;
    }

    public static void readCsv(String path, Consumer<Map<String, String>> rowConverter) throws IOException {
        CSVFormat csvFormat = CSVFormat.EXCEL.builder()
                .setDelimiter(',')
                .setQuote('"')
                .setRecordSeparator("\n")
                .setIgnoreEmptyLines(true)
                .setAllowDuplicateHeaderNames(true)
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();
        try (
                Reader reader = Files.newBufferedReader(Paths.get(path));

                CSVParser csvParser = new CSVParser(reader, csvFormat);
        ) {
//            Map<String, Integer> keys = csvParser.getHeaderMap();
            for (CSVRecord csvRecord : csvParser) {
                rowConverter.accept(csvRecord.toMap());
            }
        }
    }
}
