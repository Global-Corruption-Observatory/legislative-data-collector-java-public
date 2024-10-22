package com.precognox.ceu.legislative_data_collector.common;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class Constants {

    public static final String EXPORT_CSV_PATH = "datasets/${countryCode}/${date}";

    public static final String CHROME_LOCATION = System.getenv().containsKey("CHROME_LOCATION")
            ? System.getenv("CHROME_LOCATION")
            : "/usr/bin/chromium-browser";

    public static final List<String> PROXY_LIST = initProxyList();

    private static List<String> initProxyList() {
        if (System.getenv().containsKey("PROXY_FILE_PATH")) {
            //newline-separated list of proxy IP addresses
            Path proxyFilePath = Path.of(System.getenv().get("PROXY_FILE_PATH"));

            if (Files.exists(proxyFilePath)) {
                try {
                    return Files.readAllLines(proxyFilePath).stream().filter(s -> !s.isBlank()).toList();
                } catch (IOException e) {
                    throw new IllegalArgumentException("Failed to read proxy list from: " + proxyFilePath, e);
                }
            } else {
                throw new IllegalArgumentException("Proxy configuration file does not exist: " + proxyFilePath);
            }
        }

        return Collections.emptyList();
    }

    public static String getExportFolder() {
        Map<String, String> exportFolderParams = Map.of(
                "countryCode", System.getenv("COUNTRY"),
                "date", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        );

        return StringSubstitutor.replace(EXPORT_CSV_PATH, exportFolderParams);
    }

}
