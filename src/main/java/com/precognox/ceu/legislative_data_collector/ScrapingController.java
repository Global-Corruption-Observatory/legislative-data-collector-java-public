package com.precognox.ceu.legislative_data_collector;

import java.util.List;

/**
 * Interface for classes containing the scraping steps for a given country.
 */
public interface ScrapingController {
    /**
     * Entry point to the scraping process.
     */
    void runScraping(List<String> args);
}
