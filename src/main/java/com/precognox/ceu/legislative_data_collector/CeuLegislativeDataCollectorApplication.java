package com.precognox.ceu.legislative_data_collector;

import com.precognox.ceu.legislative_data_collector.common.DatasetExporter;
import com.precognox.ceu.legislative_data_collector.common.DatasetReporter;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

import java.util.Arrays;
import java.util.List;

@Slf4j
@EnableRetry
@Configuration
@SpringBootApplication
public class CeuLegislativeDataCollectorApplication implements CommandLineRunner {

    private final BeanFactory beanFactory;
    private final DatasetReporter reporter;
    private final DatasetExporter datasetExporter;

    @Autowired
    public CeuLegislativeDataCollectorApplication(
            BeanFactory beanFactory, DatasetReporter reporter, DatasetExporter datasetExporter) {
        this.beanFactory = beanFactory;
        this.reporter = reporter;
        this.datasetExporter = datasetExporter;
    }

    public static void main(String[] args) {
        SpringApplication.run(CeuLegislativeDataCollectorApplication.class, args);
    }

    @Override
    public void run(String... args) {
        String cCode = System.getenv("COUNTRY");
        Country country = Country.fromCode(cCode);

        if (country != null) {
            List<String> argList = Arrays.asList(args);
            ScrapingController ctrl = beanFactory.getBean(country.getControllerClass());
            ctrl.runScraping(argList);

            if (argList.contains("report")) reporter.printReport();
            if (argList.contains("export")) datasetExporter.export();
        } else {
            System.err.println(
                    "No or wrong value specified in the COUNTRY env variable - must be a country code. Current value: " + cCode
            );
        }
    }
}
