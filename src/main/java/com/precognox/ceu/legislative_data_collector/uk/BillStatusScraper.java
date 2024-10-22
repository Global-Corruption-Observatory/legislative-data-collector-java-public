package com.precognox.ceu.legislative_data_collector.uk;

import com.precognox.ceu.legislative_data_collector.common.ChromeBrowserPool;
import com.precognox.ceu.legislative_data_collector.common.Constants;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.repositories.LegislativeDataRepository;
import com.precognox.ceu.legislative_data_collector.utils.selenium.SeleniumUtils;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.precognox.ceu.legislative_data_collector.uk.CommonConstants.WEBSITE_BASE_URL;

/**
 * Collects only the bill_status variable from the website, since it was missing from the API responses.
 */
@Slf4j
@Service
public class BillStatusScraper {

    @Autowired
    private ChromeBrowserPool browserPool;

    @Autowired
    private LegislativeDataRepository legislativeRecordRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    public void collectBillStatusFromSite() {
        try {
            ChromeDriver browser;
//            ChromeDriver browser = browserPool.borrowObject();
            int maxPage = 100;

            for (int currentPage = 1; currentPage < maxPage; currentPage++) {
                String url = WEBSITE_BASE_URL + "/?Session=0&page=%s";
                String formatted = url.formatted(currentPage);

                TimeUnit.SECONDS.sleep(5);

                ChromeOptions chromeOptions = new ChromeOptions();
                chromeOptions.setBinary(Constants.CHROME_LOCATION);
                browser = new ChromeDriver(chromeOptions);

                try {
                    browser.get(formatted);
                    SeleniumUtils.checkCaptcha(browser);
                    List<WebElement> cards = browser.findElements(By.cssSelector("div.card-bill"));

                    cards.forEach(card -> {
                        WebElement firstLink = card.findElement(By.tagName("a"));

                        if (firstLink != null) {
                            String billLink = firstLink.getAttribute("href");

                            Optional<String> billId = Pattern.compile("/bills/(\\d+)")
                                    .matcher(billLink)
                                    .results()
                                    .map(match -> match.group(1))
                                    .findFirst();

                            billId.ifPresent(bId -> {
                                transactionTemplate.execute(status -> {
                                    Optional<LegislativeDataRecord> storedBill =
                                            legislativeRecordRepository.findByCountryAndBillId(Country.UK, bId);

                                    storedBill.ifPresent(bill -> {
                                        String cardText = card.getText();

                                        if (cardText.contains("Fell Awaiting:")) {
                                            bill.setBillStatus(LegislativeDataRecord.BillStatus.REJECT);
                                        } else if (cardText.contains("Next Stage:")) {
                                            bill.setBillStatus(LegislativeDataRecord.BillStatus.ONGOING);
                                        } else if (cardText.contains("Granted")) {
                                            bill.setBillStatus(LegislativeDataRecord.BillStatus.PASS);
                                        } else {
                                            String error = "Unknown bill status label: " + cardText;
                                            bill.getErrors().add(error);
                                            log.warn(error);
                                        }
                                    });

                                    return null;
                                });
                            });
                        }
                    });
                } finally {
                    browser.close();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
