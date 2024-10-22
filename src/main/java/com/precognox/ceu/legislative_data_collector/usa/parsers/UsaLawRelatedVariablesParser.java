package com.precognox.ceu.legislative_data_collector.usa.parsers;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.usa.LawType;
import com.precognox.ceu.legislative_data_collector.usa.PageNotFoundException;
import com.precognox.ceu.legislative_data_collector.usa.PageTypes;
import com.precognox.ceu.legislative_data_collector.usa.UsaCommonFunctions;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import com.precognox.ceu.legislative_data_collector.utils.XmlUtils;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.precognox.ceu.legislative_data_collector.usa.Constants.SITE_BASE_URL;

@Slf4j
@Service
@AllArgsConstructor
public class UsaLawRelatedVariablesParser {
    private final PrimaryKeyGeneratingRepository legislativeRecordRepository;
    private final PageSourceRepository pageSourceRepository;
    private final UsaCommonFunctions commonFunctions;

    @Transactional
    public void parseAllPages() {
        try {
            log.info("Querying unprocessed pages...");
            commonFunctions.driver = WebDriverUtil.createChromeDriver();

            legislativeRecordRepository.streamUnprocessedLaws(Country.USA)
                    .forEach(record -> {
                        log.info("Processing law data for bill page: " + record.getBillPageUrl());

                        legislativeRecordRepository.mergeInNewTransaction(parsePage(record));
                    });
        } finally {
            WebDriverUtil.quitChromeDriver(commonFunctions.driver);
        }
    }

    public LegislativeDataRecord parsePage(LegislativeDataRecord record) {
        PageSource source = pageSourceRepository.getByPageUrl(record.getBillPageUrl());
        Document billPage = Jsoup.parse(source.getRawSource());

        LegislativeDataRecord.BillStatus billStatus = getBillStatus(source.getPageUrl(), billPage);
        record.setBillStatus(billStatus);

        Optional<String> actionsUrl = commonFunctions.getActionsUrl(billPage);
        Document actionsPage = null;

        if (actionsUrl.isPresent()) {
            try {
                actionsPage = commonFunctions.getPageFromDbOrDownload(PageTypes.ACTION.name(), actionsUrl.get());
            } catch (PageNotFoundException ex) {
                log.error("Actions page not found: ", ex);
            }
        }

        //Law related variables are only collected if the bill was passed
        if (billStatus == LegislativeDataRecord.BillStatus.PASS) {

            String lawType = getLawType(billPage);
            record.setLawType(lawType);

            if (lawType != null) {
                record.setLawId(getLawId(billPage));
            }

            if (actionsPage != null) {
                record.setDatePassing(getDatePassing(actionsPage));
            }

            getLawText(billPage, record);

            getFinalVotes(actionsPage, record);
        }

        return record;
    }

    private LegislativeDataRecord.BillStatus getBillStatus(String billUrl, Document billPage) {
        LegislativeDataRecord.BillStatus billStatus = LegislativeDataRecord.BillStatus.REJECT;


        String periodRegex = "\\d{2,3}th-congress";
        Pattern periodPattern = Pattern.compile(periodRegex);
        Matcher matcher = periodPattern.matcher(billUrl);

        Optional<Integer> period = matcher.find() ?
                Optional.of(Integer.parseInt(matcher.group().replace("th-congress", ""))) :
                Optional.empty();

        Optional<String> tracker =
                Optional.ofNullable(billPage.body().getElementsByClass("standard01").first())
                        .map(selected -> selected.getElementsByClass("selected").first())
                        .map(child -> child.childNodes().get(0).toString().trim());

        if (period.isPresent() && tracker.isPresent()) {
            int currentPeriod = 118;

            switch (tracker.get()) {
                case "Introduced", "Passed House", "Failed House", "Passed Senate", "Failed Senate",
                     "Resolving Differences", "To President", "Vetoed by President",
                     "Passed over veto", "Failed to pass over veto":
                    if (period.get() == currentPeriod) {
                        billStatus = LegislativeDataRecord.BillStatus.ONGOING;
                    }
                    break;
                case "Became Law", "Became Private Law": {
                    billStatus = LegislativeDataRecord.BillStatus.PASS;
                    break;
                }
            }
        }

        return billStatus;
    }

    public String getLawType(Document billPage) {
        String lawType = null;

        Optional<String> latestAction = Optional.ofNullable(billPage.body().getElementsByTag("table").first())
                .map(td -> td.getElementsContainingText("Latest Action").next().last())
                .map(Element::text);

        if (latestAction.isPresent()) {
            if (latestAction.get().contains("Became Public Law")) {
                lawType = LawType.Public;
            } else if (latestAction.get().contains("Became Private Law")) {
                lawType = LawType.Private;
            } else {
                log.warn("Law type not found in latest action field: {}", latestAction);
                return null;
            }
        } else {
            log.warn("Latest action not found");
        }

        return lawType;
    }

    public String getLawId(Document billPage) {
        String latestAction = Optional.ofNullable(billPage.body().getElementsByTag("table").first())
                .map(td -> td.getElementsContainingText("Latest Action").next().last())
                .map(Element::text)
                .orElse("");

        Pattern pattern = Pattern.compile("(Law No: \\d{2,3}-\\d{1,3})");
        Matcher matcher = pattern.matcher(latestAction);

        return matcher.find() ? matcher.group().replace("Law No: ", "") : null;
    }

    public void getLawText(Document billPage, LegislativeDataRecord record) {
        Document lawTextPage;

        String lawTextUrl = SITE_BASE_URL + billPage.body()
                .getElementsByClass("tabs_container")
                .first()
                .getElementsByTag("a")
                .get(1)
                .attr("href") + "/text";

        try {
            lawTextPage = commonFunctions.getPageFromDbOrDownload(PageTypes.LAW_TEXT.name(), lawTextUrl);

            Optional<Elements> version = Optional.ofNullable(lawTextPage.body().getElementById("textSelector"))
                    .map(e -> e.getElementsByTag("select"));

            if (version.isPresent()) {
                String lawVersion = version.get().select("option[selected]").text();

                if (lawVersion.contains(LawType.Public) || lawVersion.contains(LawType.Private)) {
                    Optional<Element> billTextContainer =
                            Optional.ofNullable(lawTextPage.body().getElementById("billTextContainer"));

                    if (billTextContainer.isPresent()) {
                        Optional<String> lawText = Optional.of(billTextContainer.get().text());
                        lawText.ifPresent(s -> {
                            record.setLawText(lawText.get());
                            record.setLawTextUrl(lawTextUrl);
                        });
                    }
                }

            } else {
                log.warn("Law text doesn't exist");
            }
        } catch (PageNotFoundException ex) {
            log.error("Law text page not found: ", ex);
        }
    }

    public LocalDate getDatePassing(Document actionsPage) {
        String dateString = Optional.ofNullable(
                        actionsPage.body().getElementsByClass("expanded-actions").first())
                .map(actions -> actions.getElementsByTag("tr"))
                .stream()
                .map(tr -> tr.text().toLowerCase())
                .filter(text -> text.contains("became"))
                .findFirst()
                .orElse("");

        // format matched  MM, dd, yyyy
        Pattern pattern = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");
        Matcher matcher = pattern.matcher(dateString);

        return matcher.find() ? DateUtils.parseUsaDate(matcher.group()) : null;
    }

    private void getFinalVotes(Document actionsPage, LegislativeDataRecord record) {
        Optional<String> votePageUrl = getVotePageUrl(actionsPage);

        if (votePageUrl.isPresent()) {
            try {
                Document votesPage = commonFunctions.getPageFromDbOrDownload(PageTypes.VOTES.name(),
                        votePageUrl.get());

                Optional<Integer> voteFor = Optional.empty();
                Optional<Integer> voteAgainst = Optional.empty();
                Optional<Integer> voteAbst = Optional.empty();

                if (votePageUrl.get().contains("senate.gov")) {
                    String yeas = XmlUtils.findElementText(votesPage, "//*[@id='secondary_col2']/div[2]/div[2]/span");
                    voteFor = Optional.ofNullable(TextUtils.toInteger(yeas, null));

                    String nays = XmlUtils.findElementText(votesPage, "//*[@id='secondary_col2']/div[2]/div[5]");
                    voteAgainst = Optional.ofNullable(TextUtils.toInteger(nays, null));

                    String abstentions = XmlUtils.findElementText(votesPage, "//*[@id='secondary_col2']/div[2]/div[8]");
                    voteAbst = Optional.ofNullable(TextUtils.toInteger(abstentions, null));

                } else if (votePageUrl.get().contains("house.gov")) {
                    Optional<Element> votesElement = Optional.ofNullable(
                            votesPage.body().getElementsByClass("col-md-3 roll-call-second-col").first());

                    voteFor = votesElement.map(
                                    p -> p.getElementsByAttributeValueMatching("aria-label", "Aye|yea").first())
                            .map(Element::text).map(text -> text.replaceAll("Aye: |yea: ", "").trim())
                            .map(Integer::parseInt);

                    voteAgainst = votesElement.map(
                                    p -> p.getElementsByAttributeValueMatching("aria-label", "No|nay").first())
                            .map(Element::text).map(text -> text.replaceAll("No: |nay: ", "").trim())
                            .map(Integer::parseInt);

                    voteAbst = votesElement.map(
                                    p -> p.getElementsByAttributeValueContaining("aria-label", "not voting").first())
                            .map(Element::text).map(text -> text.replace("not voting:", "").trim())
                            .map(Integer::parseInt);
                }

                voteFor.ifPresent(record::setFinalVoteFor);
                voteAgainst.ifPresent(record::setFinalVoteAgainst);
                voteAbst.ifPresent(record::setFinalVoteAbst);
            } catch (Exception e) {
                log.warn("Final votes not found: ", e);
            }
        }
    }

    private Optional<String> getVotePageUrl(Document actionsPage) {
        if (actionsPage == null) {
            return Optional.empty();
        }

        Optional<String> votePageUrl = Optional.empty();
        Optional<Element> table = Optional.ofNullable(actionsPage.body().getElementById("main"))
                .map(tbody -> tbody.getElementsByTag("tbody").first());

        if (table.isPresent()) {
            votePageUrl = table.get().getElementsByTag("tr").stream()
                    .map(a -> a.getElementsContainingText("Record Vote")).filter(a -> !a.isEmpty())
                    .map(url -> url.attr("href")).findFirst();

            if (votePageUrl.isEmpty()) {
                votePageUrl = table.get().getElementsByTag("tr").stream()
                        .map(a -> a.getElementsContainingText("Roll no.")).filter(a -> !a.isEmpty())
                        .map(url -> url.attr("href")).findFirst();
            }

        }
        return votePageUrl;
    }
}
