package com.precognox.ceu.legislative_data_collector.colombia;

import com.precognox.ceu.legislative_data_collector.ScrapingController;
import com.precognox.ceu.legislative_data_collector.colombia.affecting.AffectedLawParser;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;
import com.precognox.ceu.legislative_data_collector.utils.selenium.SeleniumUtils;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Slf4j
@Service
public class ColombiaController implements ScrapingController {
    private static final String SLASH = "/";
    public static final String DOWNLOAD_PATH =
            SeleniumUtils.DEFAULT_USER_HOME + SLASH + SeleniumUtils.DOWNLOAD_FOR_COLOMBIA;

    private final ColombiaPageCollector colombiaPageCollector;
    private final ColombiaDataParser colombiaDataParser;
    private final AffectedLawParser affectedLawParser;

    @Autowired
    public ColombiaController(ColombiaPageCollector colombiaPageCollector, ColombiaDataParser colombiaDataParser,
                              AffectedLawParser affectedLawParser
    ) {
        this.colombiaPageCollector = colombiaPageCollector;
        this.colombiaDataParser = colombiaDataParser;
        this.affectedLawParser = affectedLawParser;
    }

    @Override
    public void runScraping(List<String> args) {
        File downloadDirectory = new File(DOWNLOAD_PATH);
        try (WebDriverWrapper browser =
                     new WebDriverWrapper(SeleniumUtils.getChromeBrowserForColombia(), downloadDirectory)) {
            colombiaPageCollector.collectPageSources(browser.getWebDriver());
            colombiaDataParser.parseBillPages(browser);

            log.info("Collecting modification information for laws");
            affectedLawParser.handleAffectedLaws();
            log.info("Counting affected laws");
            affectedLawParser.setAffectedLawsCount();
            log.info("Processing affecting laws");
            affectedLawParser.processAffectingLaws();
            log.info("Processing bills for affected laws related variables - finished");
        } catch (DataCollectionException ex) {
            log.error("Error during scraping colombia data:", ex);
        }
    }
}
