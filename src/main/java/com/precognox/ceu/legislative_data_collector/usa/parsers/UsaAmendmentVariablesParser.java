package com.precognox.ceu.legislative_data_collector.usa.parsers;

import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.AmendmentOriginator;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.usa.Constants;
import com.precognox.ceu.legislative_data_collector.usa.PageDownloader;
import com.precognox.ceu.legislative_data_collector.usa.PageNotFoundException;
import com.precognox.ceu.legislative_data_collector.usa.PageTypes;
import com.precognox.ceu.legislative_data_collector.usa.UsaCommonFunctions;
import com.precognox.ceu.legislative_data_collector.utils.CaptchaException;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.JsoupUtils;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import com.precognox.ceu.legislative_data_collector.utils.XmlUtils;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.TriFunction;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.precognox.ceu.legislative_data_collector.usa.Constants.SITE_BASE_URL;

@Slf4j
@Service
public class UsaAmendmentVariablesParser {

    @Autowired
    private PageDownloader pageDownloader;

    public static final int AMENDMENTS_PAGE_SIZE = 100;
    public static final String HOUSE_VOTE_SITE_URL = "https://clerk.house.gov/Votes/";
    public static final String SENATE_VOTE_SITE_URL = "https://www.senate.gov/legislative/LIS/roll_call_votes/";
    private final PrimaryKeyGeneratingRepository legislativeRecordRepository;
    private final PageSourceRepository pageSourceRepository;
    private final JsoupUtils jsoupUtils;
    private WebDriver driver;
    private final UsaCommonFunctions commonFunctions;

    private Document amendmentsPage;
    private Integer MAX_PERIOD_NUM;

    private TriFunction<String, PageTypes, Function<String, String>, Optional<String>> pageLoader;

    public UsaAmendmentVariablesParser(PrimaryKeyGeneratingRepository legislativeRecordRepository,
                                       PageSourceRepository pageSourceRepository,
                                       JsoupUtils jsoupUtils,
                                       UsaCommonFunctions commonFunctions) {
        this.legislativeRecordRepository = legislativeRecordRepository;
        this.pageSourceRepository = pageSourceRepository;
        this.jsoupUtils = jsoupUtils;
        this.commonFunctions = commonFunctions;
    }

    @Transactional
    public void parseAllPages() {
        try {
            driver = WebDriverUtil.createChromeDriver();

            log.info("Querying unprocessed amendments...");
            MAX_PERIOD_NUM = commonFunctions.findMaximumPeriodNumber(driver);

            legislativeRecordRepository.streamUnprocessedAmendments(Country.USA)
                    .forEach(record -> {
                        log.info("Processing amendment data for bill page: " + record.getBillPageUrl());
                        legislativeRecordRepository.mergeInNewTransaction(parsePage(record));
                    });
        } finally {
            WebDriverUtil.quitChromeDriver(commonFunctions.driver);
        }
    }

    public LegislativeDataRecord parsePage(LegislativeDataRecord record) {
        PageSource source = pageSourceRepository.getByPageUrl(record.getBillPageUrl());

        Document billPage = Jsoup.parse(source.getRawSource());
        String currentPeriod = commonFunctions.getCurrentPeriod(source.getPageUrl());

        int amendmentCount = getAmendmentCount(billPage);
        record.setAmendmentCount(amendmentCount);

        if (amendmentCount > 0) {
            List<Amendment> amendments = collectAmendments(amendmentCount, record, billPage, currentPeriod);
            record.setAmendments(amendments);
        }

        return record;
    }

    private int getAmendmentCount(Document billPage) {
        int amendmentCount = 0;

        try {
            amendmentCount =
                    Optional.ofNullable(billPage.body().getElementsByClass("tabs_container").first())
                            .map(li -> li.getElementsByTag("li").get(4))
                            .map(counter -> counter.getElementsByClass("Counter").text())
                            .map(amendmentCountString -> amendmentCountString.replaceAll("[()]", ""))
                            .map(Integer::parseInt)
                            .orElse(0);

        } catch (NumberFormatException ex) {
            log.error("Amendment count not found: ", ex);
        }

        return amendmentCount;
    }

    public List<Amendment> collectAmendments(int amendmentCount, LegislativeDataRecord record, Document billPage,
                                             String currentPeriod) {
        List<Amendment> amendments = new ArrayList<>();

        String amendmentsUrl = SITE_BASE_URL + billPage.body()
                .getElementsByClass("tabs_container")
                .first()
                .getElementsByTag("a")
                .get(4)
                .attr("href") + "&page=";

        int maxPageNumber = (amendmentCount / 100) + 1;

        int currentPage = 1;
        int currentAmendmentCount = 0;

        while (currentPage <= maxPageNumber) {
            try {
                amendmentsPage =
                        commonFunctions.getPageFromDbOrDownload(PageTypes.AMENDMENT.name(),
                                amendmentsUrl + currentPage);

                for (int i = 0; i < AMENDMENTS_PAGE_SIZE; i++) {
                    Amendment amendment = new Amendment();
                    amendment.setDataRecord(record);

                    Optional<String> amendmentId = getAmendmentId(i);

                    if (amendmentId.isPresent()) {
                        amendment.setAmendmentId(amendmentId.get());

                        String amendmentTextPageUrl = getAmendmentTextPageUrl(i, amendmentId.get());
                        amendment.setTextSourceUrl(amendmentTextPageUrl);
                        getAmendmentText(amendmentTextPageUrl, amendmentId.get()).ifPresent(
                                amendment::setAmendmentText);
                        amendment.setPlenary(getAmendmentPlenary(amendment.getAmendmentId()));
                        amendment.setOriginators(getAmendmentOriginator(i));
                        amendment.setOutcome(getAmendmentOutcome(i, currentPeriod));
                        collectStageInfo(amendment, amendmentTextPageUrl);
                        getAmendmentVotes(amendment, i);

                        amendments.add(amendment);
                    }
                    currentAmendmentCount++;
                    if (currentAmendmentCount == amendmentCount) {
                        break;
                    }
                }

                currentPage++;
            } catch (PageNotFoundException ex) {
                log.error("Amendment page not found: ", ex);
            }
        }

        return amendments;
    }

    private Optional<String> getAmendmentId(int index) {
        return Optional.of(amendmentsPage.body())
                .map(resultList -> resultList.getElementsByClass("basic-search-results-lists").first())
                .map(result -> result.getElementsByClass("expanded").get(index))
                .map(a -> a.getElementsByTag("a").first())
                .map(Element::text);
    }

    private Optional<String> getAmendmentText(String origAmendmentTextPageUrl, String amendmentId) {
        Optional<Element> itemTable = Optional.empty();
        Document amendmentTextPage = getAmendmentTextPage(origAmendmentTextPageUrl, null);

        int textVersionNum = getAmendmentTextVersionNum(amendmentTextPage);

        if (textVersionNum == 1) {
            itemTable = handleSingleVersionText(amendmentTextPage);
        } else if (textVersionNum >= 2) {
            itemTable = handleMultipleVersionText(origAmendmentTextPageUrl, amendmentTextPage);
        }

        return itemTable.isPresent() ? getAmendmentTextFromTable(itemTable.get(), amendmentId) : Optional.empty();
    }

    private Optional<Element> handleSingleVersionText(Document amendmentTextPage) {
        return getItemTable(amendmentTextPage, null);
    }

    private Optional<Element> handleMultipleVersionText(String origAmendmentTextPageUrl, Document amendmentTextPage) {
        Optional<PageSource> savedAmendmentTextPage = findAmendmentTextPage(origAmendmentTextPageUrl);
        if (savedAmendmentTextPage.isPresent()) {
            amendmentTextPage = Jsoup.parse(savedAmendmentTextPage.get().getRawSource());
        } else {
            WebDriver driver = null;
            try {
                driver = WebDriverUtil.createChromeDriver();
                driver.get(origAmendmentTextPageUrl);

                amendmentTextPage = Jsoup.parse(driver.getPageSource());

                Optional<Element> urlSelect = Optional.ofNullable(
                        amendmentTextPage.body().getElementById("urlSelect"));

                if (urlSelect.isPresent()) {
                    Select versions = new Select(driver.findElement(By.id("urlSelect")));
                    versions.selectByIndex(1);
                    String amendmentTextPageUrl = driver.getCurrentUrl();
                    amendmentTextPage = getAmendmentTextPage(amendmentTextPageUrl, origAmendmentTextPageUrl);
                } else {
                    log.warn("Amendment text not found on page: {}", origAmendmentTextPageUrl);
                }
            } finally {
                if (driver != null) {
                    driver.quit();
                }
            }
        }
        return getItemTable(amendmentTextPage, origAmendmentTextPageUrl);
    }

    public Optional<PageSource> findAmendmentTextPage(String amendmentTextPageUrl) {
        return pageSourceRepository.findByUrlByMetadata(PageTypes.AMENDMENT_TEXT.name(), Country.USA,
                amendmentTextPageUrl);
    }

    private String getAmendmentTextPageUrl(int index, String amendmentId) {
        String amendmentIdNumber = amendmentId.replaceAll("[a-zA-Z.]*", "");

        return SITE_BASE_URL + amendmentsPage.body()
                .getElementsByClass("basic-search-results-lists")
                .first()
                .getElementsByClass("expanded")
                .get(index)
                .getElementsByTag("a")
                .attr("href")
                .replace(amendmentIdNumber, amendmentIdNumber + "/text");

    }

    private Document getAmendmentTextPage(String amendmentTextPageUrl, String origAmendmentTextPageUrl) {
        Document amendmentTextPage = null;

        try {
            amendmentTextPage =
                    commonFunctions.getPageFromDbOrDownload(PageTypes.AMENDMENT_TEXT.name(), amendmentTextPageUrl,
                            origAmendmentTextPageUrl);
        } catch (PageNotFoundException ex) {
            log.error("Amendment text page not found: {}", amendmentTextPageUrl);
        }

        return amendmentTextPage;
    }

    private int getAmendmentTextVersionNum(Document amendmentTextPage) {
        Optional<String> versionNumString = Optional.empty();

        try {
            if (amendmentTextPage != null) {
                //There can be 3 cases: 0 or 1 or many versions, indicated by the number in the parentheses
                versionNumString =
                        Optional.ofNullable(amendmentTextPage.body().getElementsByClass("tabs_links").first())
                                .map(links -> links.getElementsByClass("selected").first())
                                .map(link -> link.text().replaceAll("[a-zA-Z() ]*", ""));
            }
        } catch (NumberFormatException ex) {
            log.error("Amendment text version num not found: ", ex);
        }

        return versionNumString.map(Integer::parseInt).orElse(0);
    }

    private Optional<Element> getItemTable(Document amendmentTextPage, String origAmendmentTextPageUrl) {
        Optional<Element> itemTable;
        String amendmentTextPageUrl;

        if (amendmentTextPage == null) {
            return Optional.empty();
        }
        itemTable = Optional.ofNullable(amendmentTextPage.body().getElementsByClass("item_table").first());

        if (itemTable.isEmpty()) {
            //If item table is not attached to the page, there is a link which navigate to another page where
            // the table can be found
            amendmentTextPageUrl =
                    Optional.ofNullable(amendmentTextPage.body().getElementsByClass("main-wrapper").first())
                            .map(a -> a.getElementsByTag("a").attr("href")).orElse(null);

            if (amendmentTextPageUrl != null) {
                amendmentTextPage = getAmendmentTextPage(SITE_BASE_URL + amendmentTextPageUrl,
                        origAmendmentTextPageUrl);
                if (amendmentTextPage != null) {
                    itemTable = Optional.ofNullable(amendmentTextPage.body().getElementsByClass("item_table").first());
                } else {
                    itemTable = Optional.empty();
                }
            }
        }

        return itemTable;
    }

    private Optional<String> getAmendmentTextFromTable(Element itemTable, String amendmentId) {
        Optional<String> amendmentText = Optional.empty();

        Predicate<Element> textOfAmendment = a -> a.text().contains("TEXT OF AMENDMENTS");
        Predicate<Element> amendmentsSubmitted = a -> a.text().contains("AMENDMENTS SUBMITTED");

        Elements rows = itemTable.selectFirst("tbody").getElementsByTag("tr");

        if (rows.size() == 1) {
            Optional<String> amendmentTextLink = Optional.ofNullable(itemTable.getElementsByTag("a").first())
                    .map(a -> a.attr("href"));

            return amendmentTextLink.map(s -> getTextFromCongressionalRecord(s, amendmentId));
        } else {
            Optional<String> amendmentTextLink = itemTable.getElementsByTag("tr")
                    .stream()
                    .skip(1)
                    .map(row -> row.getElementsByTag("td").first())
                    .map(cell -> cell.getElementsByTag("a").first())
                    .filter(Objects::nonNull)
                    .filter(textOfAmendment.or(amendmentsSubmitted))
                    .map(a -> a.attr("href"))
                    .findFirst();

            if (amendmentTextLink.isPresent()) {
                try {
                    Document amendmentTextPage;
                    amendmentTextPage = commonFunctions.getPageFromDbOrDownload(
                            PageTypes.TEXT_OF_AMENDMENT.name(), SITE_BASE_URL + amendmentTextLink.get());

                    amendmentText =
                            Optional.ofNullable(amendmentTextPage.body().getElementsByClass("styled").first())
                                    .map(Element::text);
                } catch (PageNotFoundException e) {
                    log.error("Amendment text page not found");
                }
            }
        }

        return amendmentText;
    }

    private String getTextFromCongressionalRecord(String congressionalRecordLink, String amendmentId) {
        try {
            Document page = jsoupUtils.getPage(commonFunctions.driver, SITE_BASE_URL + congressionalRecordLink);

            if (page == null) {
                return null;
            }

            Element textBlock = page.body().selectFirst("pre.styled");

            if (textBlock != null) {
                String amendmentNumber = amendmentId.replace("S.Amdt.", "").replace("H.Amdt.", "");
                Matcher matcher = Pattern.compile("AMENDMENT NO. " + amendmentNumber, Pattern.CASE_INSENSITIVE)
                        .matcher(textBlock.text());

                if (matcher.find()) {
                    int amendmentTextStart = matcher.end();
                    String textFromAmendmentStart = textBlock.text().substring(amendmentTextStart);
                    int amendmentTextEnd = textFromAmendmentStart.indexOf("______");
                    String amendmentText = amendmentTextEnd != -1
                            ? textFromAmendmentStart.substring(0, amendmentTextEnd)
                            : textFromAmendmentStart;

                    return amendmentText.strip();
                }
            }
        } catch (CaptchaException e) {
            log.error("Banned from page", e);
        }

        return null;
    }

    private Amendment.Plenary getAmendmentPlenary(String amendmentId) {
        Amendment.Plenary plenary = null;

        //Plenary can be derived from the first char of the id
        String plenaryChar = amendmentId.substring(0, 1);
        if ("H".equals(plenaryChar)) {
            plenary = Amendment.Plenary.LOWER;
        } else if ("S".equals(plenaryChar)) {
            plenary = Amendment.Plenary.UPPER;
        }

        return plenary;
    }

    private List<AmendmentOriginator> getAmendmentOriginator(int index) {
        List<AmendmentOriginator> amendmentOriginator = new ArrayList<>();
        AmendmentOriginator originator = new AmendmentOriginator();
        String originatorName;
        String originatorAff;

        Optional<String> originatorDetails =
                Optional.of(amendmentsPage.body().getElementsByClass("expanded").get(index))
                        .map(a -> a.getElementsContainingText("Sponsor").next().last())
                        .map(Element::text);

        if (originatorDetails.isPresent()) {
            //Getting the position of the first opening square bracket in order to determine the
            //sponsor's name and affiliation
            //From the beginning position till the square bracket's pos we get the sponsor's name,
            //the following char represents the affiliation
            int openingSquareBracketPos = originatorDetails.get().indexOf('[');

            if (openingSquareBracketPos > -1) {
                originatorName = originatorDetails.get().substring(0, openingSquareBracketPos - 1);
                originatorAff =
                        originatorDetails.get().substring(openingSquareBracketPos + 1, openingSquareBracketPos + 2);

                if (originatorAff.equals("D")) {
                    originator.setAffiliation("Democrats");
                } else if (originatorAff.equals("R")) {
                    originator.setAffiliation("Republicans");
                } else {
                    originator.setAffiliation(originatorAff);
                }

                originator.setName(originatorName);
                amendmentOriginator.add(originator);
            }
        }

        return amendmentOriginator;
    }

    private Amendment.Outcome getAmendmentOutcome(int amendmentIndexOnPage, String currentPeriod) {
        Amendment.Outcome outcome = null;

        Elements latestActionElements = amendmentsPage.body()
                .select("li.expanded")
                .get(amendmentIndexOnPage)
                .getElementsContainingText("Latest Action");

        if (latestActionElements.size() > 1) {
            String latestAction = latestActionElements.get(1).text().toLowerCase();

            if (latestAction.contains("not agreed") || latestAction.contains("failed by")) {
                outcome = Amendment.Outcome.REJECTED;
            } else if (latestAction.contains("agreed to")) {
                outcome = Amendment.Outcome.APPROVED;
            } else if (latestAction.contains("withdrawn")) {
                outcome = Amendment.Outcome.WITHDRAWN;
            } else {
                outcome = Amendment.Outcome.REJECTED;
            }

        } else if (!currentPeriod.equals(MAX_PERIOD_NUM.toString())) {
//            Every law which doesn't belong to the current period is rejected
            outcome = Amendment.Outcome.REJECTED;
        }

        return outcome;
    }

    @SneakyThrows
    private void collectStageInfo(Amendment amendment, String amendmentTextPageUrl) {
        Document amendmentPage = jsoupUtils.getPage(commonFunctions.driver, amendmentTextPageUrl);

        if (amendmentPage == null) {
            return;
        }

        Optional<LocalDate> latestActionDate = Optional.ofNullable(amendmentPage.body().select("div.overview").first())
                .map(overview -> overview.getElementsContainingOwnText("Latest Action:"))
                .map(Elements::first)
                .map(Element::nextElementSibling)
                .map(Element::text)
                .map(dateString -> Constants.DATE_REGEX.matcher(dateString)
                        .results()
                        .findFirst()
                        .map(MatchResult::group))
                .map(date -> DateUtils.parseUsaDate(date.get()));

        if (latestActionDate.isPresent()) {
            Optional<LegislativeStage> closestEarlierStage = amendment.getDataRecord().getStages()
                    .stream()
                    .filter(stage -> stage.getDate().isEqual(latestActionDate.get()) || stage.getDate()
                            .isBefore(latestActionDate.get()))
                    .max(Comparator.comparing(LegislativeStage::getDate));

            closestEarlierStage.ifPresent(stg -> {
                amendment.setStageName(stg.getName());
                amendment.setStageNumber(stg.getStageNumber());
            });
        }
    }

    private void getAmendmentVotes(Amendment amendment, int indexOnPage) {
        Element currentAmendment = amendmentsPage.body().select("li.expanded").get(indexOnPage);
        Element houseVotesLink = currentAmendment
                .getElementsByAttributeValueContaining("href", HOUSE_VOTE_SITE_URL).last();
        Element senateVotesLink =
                currentAmendment.getElementsByAttributeValueContaining("href", SENATE_VOTE_SITE_URL).last();

        if (senateVotesLink != null) {
            parseSenateVotesPage(amendment, senateVotesLink);
        }

        if (houseVotesLink != null) {
            parseHouseVotesPage(amendment, houseVotesLink);
        }
    }

    private void parseHouseVotesPage(Amendment amendment, Element houseVotesLink) {
        try {
            String houseVotesUrl = houseVotesLink.attr("href");
            if (houseVotesUrl == null) {
                log.info("Missing houseVotesLink");
                return;
            }
            Optional<String> senateVotesPageSource = pageLoader.apply(
                    houseVotesUrl,
                    PageTypes.VOTES,
                    this::downloadPage);

            if (senateVotesPageSource.isEmpty()) {
                log.info("Missing houseVotesLink");
                return;
            }
            Document parsedPage = Jsoup.parse(senateVotesPageSource.get());
            if (parsedPage != null) {
                Element ayeLabel = parsedPage.body().getElementsContainingOwnText("Aye:").first();
                Element noLabel = parsedPage.body().getElementsContainingOwnText("No:").first();
                Element notVotingLabel = parsedPage.body().getElementsContainingOwnText("Not Voting:").first();

                extractVotes(ayeLabel).ifPresent(amendment::setVotesInFavor);
                extractVotes(noLabel).ifPresent(amendment::setVotesAgainst);
                extractVotes(notVotingLabel).ifPresent(amendment::setVotesAbstention);
            }
        } catch (Exception e) {
            log.error("Failed to parseHouseVotesPage", e);
        }
    }

    private Optional<Integer> extractVotes(Element label) {
        return Optional.ofNullable(label)
                .flatMap(l -> l.siblingNodes().stream()
                        .filter(node -> node instanceof TextNode && StringUtils.isNotEmpty(node.toString().strip()))
                        .findFirst())
                .map(voteText -> voteText.toString().strip())
                .map(Integer::parseInt);
    }

    private void parseSenateVotesPage(Amendment amendment, Element senateVotesLink) {
        try {
            String senateVotesUrl = senateVotesLink.attr("href");

            Optional<String> senateVotesPageSource = pageLoader.apply(
                    senateVotesUrl,
                    PageTypes.ACTION,
                    this::downloadPage);

            if (senateVotesPageSource.isEmpty()) {
                log.error("Missing page {};", senateVotesUrl);
                return;
            }

            Document document = XmlUtils.parseXml(senateVotesPageSource.get());

            String yeas = XmlUtils.findElementText(document, "//*[@id='secondary_col2']/div[2]/div[2]/span");
            Optional<Integer> voteFor = Optional.ofNullable(TextUtils.toInteger(yeas, null));
            voteFor.ifPresent(amendment::setVotesInFavor);

            String nays = XmlUtils.findElementText(document, "//*[@id='secondary_col2']/div[2]/div[5]");
            Optional<Integer> voteAgainst = Optional.ofNullable(TextUtils.toInteger(nays, null));
            voteAgainst.ifPresent(amendment::setVotesAgainst);

            String abstentions = XmlUtils.findElementText(document, "//*[@id='secondary_col2']/div[2]/div[8]");
            Optional<Integer> voteAbst = Optional.ofNullable(TextUtils.toInteger(abstentions, null));
            voteAbst.ifPresent(amendment::setVotesAbstention);
        } catch (Exception e) {
            log.error("Failed to save bill", e);
        }
    }

    private String downloadPage(String pageUrl) {
        try {
            Document parsedPage = jsoupUtils.getPage(commonFunctions.driver, pageUrl);

            if (parsedPage == null) {
                return null;
            }

            return parsedPage.html();
        } catch (CaptchaException e) {
            log.error("Failed to download: {}", pageUrl, e);
            return null;
        }
    }
}
