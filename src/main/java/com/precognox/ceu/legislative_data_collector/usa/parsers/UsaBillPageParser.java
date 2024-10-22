package com.precognox.ceu.legislative_data_collector.usa.parsers;

import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.RelatedBill;
import com.precognox.ceu.legislative_data_collector.entities.usa.UsaCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.usa.Constants;
import com.precognox.ceu.legislative_data_collector.usa.PageNotFoundException;
import com.precognox.ceu.legislative_data_collector.usa.PageTypes;
import com.precognox.ceu.legislative_data_collector.usa.UsaCommonFunctions;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import com.precognox.ceu.legislative_data_collector.utils.selenium.WebDriverUtil;
import lombok.extern.slf4j.Slf4j;
import me.xdrop.fuzzywuzzy.FuzzySearch;
import one.util.streamex.StreamEx;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.Select;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.precognox.ceu.legislative_data_collector.usa.Constants.SITE_BASE_URL;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Slf4j
@Service
public class UsaBillPageParser {

    private final PrimaryKeyGeneratingRepository legislativeRecordRepository;
    private final PageSourceRepository pageSourceRepository;
    private final UsaCommonFunctions commonFunctions;

    private List<String> existingCommittees = null;

    public String currentPeriod;

    public UsaBillPageParser(PrimaryKeyGeneratingRepository legislativeRecordRepository,
                             PageSourceRepository pageSourceRepository,
                             UsaCommonFunctions commonFunctions) {
        this.legislativeRecordRepository = legislativeRecordRepository;
        this.pageSourceRepository = pageSourceRepository;
        this.commonFunctions = commonFunctions;
    }

    @Transactional
    public void parseAllPages() {
        try {
            log.info("Querying unprocessed pages...");
            commonFunctions.driver = WebDriverUtil.createChromeDriver();

            pageSourceRepository.findUnprocessedBillPages(Country.USA).forEach(page -> {
                legislativeRecordRepository.save(parsePage(page));

                log.info("Saved record for bill: {}", page.getCleanUrl());
            });
        } finally {
            WebDriverUtil.quitChromeDriver(commonFunctions.driver);
        }
    }

    public LegislativeDataRecord parsePage(PageSource source) {
        log.info("Processing bill: {}", source.getCleanUrl());
        currentPeriod = commonFunctions.getCurrentPeriod(source.getPageUrl());

        if (existingCommittees == null) {
            initCommitteeList();
        }

        LegislativeDataRecord record = new LegislativeDataRecord();
        Set<String> congressionalRecordUrls = new LinkedHashSet<>();

        record.setBillPageUrl(source.getCleanUrl());
        record.setCountry(Country.USA);

        Document billPage = Jsoup.parse(source.getRawSource());
        UsaCountrySpecificVariables countrySpecificVariables = new UsaCountrySpecificVariables();

        Optional<String> billId = getBillId(billPage);
        if (billId.isPresent()) {
            record.setBillId(billId.get());
            getBillTitle(billPage, billId.get()).ifPresent(record::setBillTitle);

            if (record.getBillTitle().toLowerCase().contains("reserved bill")) {
                return record;
            }
        }

        record.setDateIntroduction(getIntroductionDate(billPage));

        Optional<PageSource> billTextPageSource = findRelatedBillTextUrl(record.getBillPageUrl());

        if (billTextPageSource.isPresent()) {
            record.setBillTextUrl(billTextPageSource.get().getCleanUrl());
            record.setBillText(getBillText(Jsoup.parse(billTextPageSource.get().getRawSource())));
        } else {
            String billTextUrl = getBillTextUrl(billPage);

            if (billTextUrl != null) {
                record.setBillTextUrl(billTextUrl);
                record.setBillText(getBillText(billTextUrl, record.getBillPageUrl()));
            }
        }

        record.setOriginType(OriginType.INDIVIDUAL_MP);
        record.setOriginators(getOriginatorData(billPage));

        int coSponsorCount = getCoSponsorCount(billPage);
        countrySpecificVariables.setCosponsorCount(coSponsorCount);

        Set<String> originatorSupportNames = new LinkedHashSet<>();
        if (coSponsorCount > 0) {
            originatorSupportNames = getOriginatorSupportNames(billPage);
        }

        List<RelatedBill> relatedBills = getRelatedBills(billPage, countrySpecificVariables);

        Optional<String> actionsUrl = commonFunctions.getActionsUrl(billPage);
        Document actionsPage;

        if (actionsUrl.isPresent()) {
            try {
                actionsPage = commonFunctions.getPageFromDbOrDownload(PageTypes.ACTION.name(), actionsUrl.get());

                countrySpecificVariables.setAmendmentStagesCount(getAmendmentStagesCount(actionsPage));

                List<Committee> committees = StreamEx.of(getCommittees(actionsPage))
                        .filter(committee -> existingCommittees.contains(committee.getName()))
                        .distinct(Committee::getName).collect(Collectors.toList());

                record.setCommittees(committees);
                record.setCommitteeCount(committees.size());
                record.setStages(getActionStagesDetails(record, actionsPage));
                record.setProcedureTypeStandard(getProcedureTypeStandard(actionsPage));

                Set<String> modifiedLaws = getModifiedLaws(actionsPage, record, congressionalRecordUrls);
                record.setModifiedLawsCount(modifiedLaws.size());
                record.setModifiedLaws(modifiedLaws);
            } catch (PageNotFoundException e) {
                log.error(String.format("Action page not found: %s;", actionsUrl.get()), e);
            }
        }

        record.setUsaCountrySpecificVariables(countrySpecificVariables);
        record.setOriginatorSupportNames(originatorSupportNames);
        record.setRelatedBills(relatedBills);

        return record;
    }

    private Optional<PageSource> findRelatedBillTextUrl(String billPageUrl) {
        return pageSourceRepository.findByUrlByMetadata(PageTypes.BILL_TEXT.name(), Country.USA, billPageUrl);
    }

    private void initCommitteeList() {
        log.info("Initializing committee list...");

        //get current committees
        List<String> committeeList =
                pageSourceRepository.findPagesByPageTypeAndCountry(PageTypes.COMMITTEE_LIST.name(), Country.USA)
                        .stream()
                        .filter(pageSource -> pageSource.getMetadata().equals("Period: " + currentPeriod)).findFirst()
                        .map(PageSource::getRawSource).map(commList -> Arrays.asList(commList.split("; ")))
                        .orElse(Collections.emptyList());

        if (!committeeList.isEmpty()) {
            existingCommittees = committeeList;
        } else {
            throw new RuntimeException();
        }
    }

    private Optional<String> getBillId(Document billPage) {
        Optional<Node> billIdAndTitleContainingNode = getBillIdAndTitleContainingNode(billPage);

        if (billIdAndTitleContainingNode.isPresent()) {
            String billDetailString = billIdAndTitleContainingNode.get().toString();

//          In the given string bill id can be found in the first part until the dash sign
            int dashSignPosition = billDetailString.indexOf(" -");

            return Optional.of(billDetailString.substring(0, dashSignPosition).trim());
        } else {
            log.warn("Unable to parse bill id");
            return Optional.empty();
        }
    }

    private Optional<String> getBillTitle(Document billPage, String billId) {
        Optional<Node> billIdAndTitleContainingNode = getBillIdAndTitleContainingNode(billPage);

        if (billIdAndTitleContainingNode.isPresent()) {
            return Optional.of(billIdAndTitleContainingNode.get().toString().trim().replace(billId + " - ", ""));
        } else {
            log.warn("Unable to parse bill title");
            return Optional.empty();
        }
    }

    private Optional<Node> getBillIdAndTitleContainingNode(Document billPage) {
        return Optional.ofNullable(billPage.body().getElementsByClass("legDetail").first())
                .map(child -> child.childNode(0));
    }

    private LocalDate getIntroductionDate(Document billPage) {
//      Introduction date can be derived from the sponsor info
        return Optional.ofNullable(billPage.body().getElementsByClass("overview").first())
                .map(td -> td.getElementsByTag("td").first())
                .map(Element::text)
                .map(dateString -> Constants.DATE_REGEX.matcher(dateString)
                        .results()
                        .findFirst()
                        .map(MatchResult::group))
                .map(date -> DateUtils.parseUsaDate(date.get()))
                .orElse(null);
    }

    private String getBillText(String billTextUrl, String billPageUrl) {
        try {
            return getBillText(
                    commonFunctions.getPageFromDbOrDownload(PageTypes.BILL_TEXT.name(), billTextUrl, billPageUrl));
        } catch (PageNotFoundException ex) {
            log.error("Bill text page not found ", ex);
        }
        return null;
    }

    private String getBillText(Document billTextPage) {
        return Optional.ofNullable(billTextPage.body().getElementById("billTextContainer"))
                .map(Element::text)
                .orElse(null);
    }

    private String getBillTextUrl(Document billPage) {
        Elements tabs_container = billPage.body().getElementsByClass("tabs_container");

        if (tabs_container.isEmpty()) {
            return null;
        }

        String billTextUrl = SITE_BASE_URL + tabs_container.get(0).getElementsByTag("a").get(1).attr("href");

        try {
            int textNum =
                    Optional.ofNullable(billPage.body().getElementsByClass("tabs_links").first())
                            .map(tabLink -> tabLink.getElementsContainingText("Text").last())
                            .map(text -> text.text().replaceAll("[a-zA-Z() ]*", ""))
                            .map(Integer::parseInt).orElse(0);

            if (textNum > 1) {
                commonFunctions.driver.get(billTextUrl);

                try {
                    Select textVersion = new Select(commonFunctions.driver.findElement(By.id("textVersion")));

                    textVersion.selectByIndex(textVersion.getOptions().size() - 1);

                    billTextUrl = commonFunctions.driver.getCurrentUrl();
                } catch (NoSuchElementException ex) {
                    log.error("Text versions not found");
                }
            }

        } catch (NumberFormatException ex) {
            log.error("Bill text url not found: ");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return billTextUrl + "&format=txt";
    }

    private List<Originator> getOriginatorData(Document billPage) {
        List<Originator> originators = new ArrayList<>();
        Originator originator = new Originator();

        Optional<String> originatorDetails =
                Optional.ofNullable(billPage.body().getElementsByClass("overview").first())
                        .map(td -> td.getElementsByTag("td").first().text());

        String originatorName;
        String originatorAffiliation;

        //Getting the position of the first opening square bracket in order to determine the
        //sponsor's name and affiliation
        //From the beginning pos until the square bracket's position we get the sponsor's name,
        //the following char represents the affiliation
        if (originatorDetails.isPresent()) {
            int openingSquareBracketPos = originatorDetails.get().indexOf('[');

            originatorName = originatorDetails.get().substring(0, openingSquareBracketPos - 1);
            originatorAffiliation = originatorDetails.get()
                    .substring(openingSquareBracketPos + 1, openingSquareBracketPos + 2);

            originator.setName(originatorName);

            switch (originatorAffiliation) {
                case "D" -> originator.setAffiliation("Democrats");
                case "R" -> originator.setAffiliation("Republicans");
                case "I" -> originator.setAffiliation("Independent");
            }
        }
        originators.add(originator);
        return originators;
    }

    public int getCoSponsorCount(Document billPage) {
        int coSponsorCount = 0;

        try {
            coSponsorCount =
                    Optional.ofNullable(billPage.body().getElementsByClass("tabs_container").first())
                            .map(li -> li.getElementsByTag("li").get(5))
                            .map(counter -> counter.getElementsByClass("Counter"))
                            .map(Elements::text)
                            .map(coSponsorCountString -> Integer.parseInt(coSponsorCountString.replaceAll("[()]", "")))
                            .orElse(0);

        } catch (NumberFormatException ex) {
            log.error("Cosponsor count not found: ", ex);
        }

        return coSponsorCount;
    }

    public Set<String> getOriginatorSupportNames(Document billPage) {
        Set<String> originatorSupportNames = new LinkedHashSet<>();

        String coSponsorCountUrl =
                SITE_BASE_URL + billPage.body().getElementsByClass("tabs_container").first()
                        .getElementsByTag("li").get(5).getElementsByTag("a").attr("href");

        String originatorSupportNameSquareBracketContentRegex = "\\[[A-Za-z\\d -]*][*]*";

        try {
            Document coSponsorPage =
                    commonFunctions.getPageFromDbOrDownload(PageTypes.COSPONSORS.name(), coSponsorCountUrl);

            originatorSupportNames =
                    Optional.ofNullable(coSponsorPage.body().getElementsByClass("item_table").first())
                            .map(tr -> tr.getElementsByTag("tr").stream().skip(1)
                                    .map(td -> td.getElementsByTag("td").first())
                                    .map(name -> name.text()
                                            .replaceAll(originatorSupportNameSquareBracketContentRegex, ""))
                                    .collect(Collectors.toSet())).orElse(Collections.emptySet());
        } catch (PageNotFoundException ex) {
            log.error("Cosponsor page not found: ", ex);
        }
        return originatorSupportNames;
    }

    private List<RelatedBill> getRelatedBills(Document billPage,
                                              UsaCountrySpecificVariables countrySpecificVariables) {
        List<RelatedBill> relatedBills = new ArrayList<>();

        Optional<Element> relatedBillsElement = Optional.ofNullable(
                        billPage.body().getElementsByClass("tabs_container").first())
                .map(li -> li.getElementsByTag("li").get(7));

        Integer relatedBillsCount = relatedBillsElement
                .map(counter -> counter.getElementsByClass("counter").text())
                .map(countString -> countString.replaceAll("[()]", ""))
                .map(Integer::parseInt)
                .orElse(0);

        if (relatedBillsCount > 0) {
            Optional<String> relatedBillsUrl =
                    Optional.ofNullable(relatedBillsElement.get().getElementsByTag("a").first())
                            .map(a -> a.attr("href"));

            if (relatedBillsUrl.isPresent()) {
                try {
                    Document relatedBillsPage = commonFunctions.getPageFromDbOrDownload(PageTypes.RELATED_BILL.name(),
                            SITE_BASE_URL + relatedBillsUrl.get());

                    Optional<Elements> relatedBillsTableRows =
                            Optional.ofNullable(
                                            relatedBillsPage.body().getElementsByClass("item_table relatedBills").first())
                                    .map((tr -> tr.getElementsByTag("tr")));

                    if (relatedBillsTableRows.isPresent()) {
                        relatedBills = relatedBillsTableRows.get().stream().skip(2)
                                .filter(tr -> isEmpty(tr.attr("class")))
                                .map(this::getRelatedBillDetailsFromTableRow)
                                .filter(relatedBill -> !relatedBill.getRelatedBillId().contains("Res.")).toList();
                    }
                } catch (PageNotFoundException ex) {
                    log.error("Related bills page not found ", ex);
                }
            }
        }

        countrySpecificVariables.setRelatedBillsCount(relatedBills.size());

        return relatedBills;
    }

    private RelatedBill getRelatedBillDetailsFromTableRow(Element row) {
        RelatedBill relatedBill = new RelatedBill();

        Elements cells = row.getElementsByTag("td");

        if (cells.size() > 2) {
            relatedBill.setRelatedBillId(cells.first().text());
            relatedBill.setRelatedBillTitle(cells.get(1).text());
            relatedBill.setRelatedBillRelationship(cells.get(2).text());
        }

        return relatedBill;
    }

    public int getAmendmentStagesCount(Document actionsPage) {
        int amendmentStagesCount = 0;

        Optional<String> allActionsCount =
                Optional.ofNullable(actionsPage.body().getElementById("facetbox_action-category"))
                        .map(actionUl -> actionUl.getElementsContainingText("All Actions").last())
                        .map(Element::text)
                        .map(actionCount -> actionCount.replaceAll("[a-zA-z ]", ""));

        Optional<String> allActionsExceptAmendments =
                Optional.ofNullable(actionsPage.body().getElementById("facetbox_action-category"))
                        .map(actionUl -> actionUl.getElementsContainingText("All Actions Except Amendments").last())
                        .map(Element::text).map(actionCount -> actionCount.replaceAll("[a-zA-z ]", ""));

        if (allActionsCount.isPresent() && allActionsExceptAmendments.isPresent()) {
            try {
                amendmentStagesCount =
                        Integer.parseInt(allActionsCount.get()) - Integer.parseInt(allActionsExceptAmendments.get());
            } catch (NumberFormatException ex) {
                log.error("Action numbers not found");
            }
        }

        return amendmentStagesCount;
    }

    private List<Committee> getCommittees(Document actionsPage) {
        List<Committee> committees = new ArrayList<>();

        Optional<Element> committeesFilterElement;

        committeesFilterElement = Optional.ofNullable(actionsPage.body().getElementById("facetbox_house-committees"));
        committeesFilterElement.ifPresent(element -> committees.addAll(findCommitteeNames(element)));

        committeesFilterElement = Optional.ofNullable(actionsPage.body().getElementById("facetbox_senate-committees"));
        committeesFilterElement.ifPresent(element -> committees.addAll(findCommitteeNames(element)));

        return committees;
    }

    private Set<Committee> findCommitteeNames(Element committeesElement) {
        Set<Committee> committees = new HashSet<>();

        committeesElement.getElementsByTag("li")
                .stream()
                .skip(1)
                .forEach(li -> {
                    String committeeLink = li.getElementsByTag("a").attr("href");
                    String committeeName = li.getElementsByTag("a").text().replaceAll(" \\[[\\d ]*]", "");

                    committees.add(getCommitteeDetailsFromTable(committeeName, SITE_BASE_URL + committeeLink));
                });

        return committees;
    }

    private Committee getCommitteeDetailsFromTable(String committeeName, String committeeLink) {
        Committee committee = new Committee();

        try {
            committee.setName(committeeName);

            Document committeePage = commonFunctions.getPageFromDbOrDownload(PageTypes.COMMITTEE.name(), committeeLink);

            Optional<LocalDate> committeeDate =
                    Optional.ofNullable(committeePage.body().getElementsByClass("expanded-actions item_table").first())
                            .map(tr -> tr.getElementsByTag("tr").last())
                            .map(td -> td.getElementsByTag("td").first())
                            .map(Element::text)
                            .map(dateString -> Constants.DATE_REGEX.matcher(dateString)
                                    .results()
                                    .findFirst()
                                    .map(MatchResult::group))
                            .map(date -> DateUtils.parseUsaDate(date.get()));

            committeeDate.ifPresent(committee::setDate);

        } catch (PageNotFoundException ex) {
            log.error("Committee page not found ", ex);
        }

        return committee;
    }

    private List<LegislativeStage> getActionStagesDetails(LegislativeDataRecord record, Document actionsPage) {
        List<LegislativeStage> legislativeStages = new ArrayList<>();
        int stagesCount = 0;

//           Action table contains actions in time ordered, the latest ones can be found at the beginning
        Optional<Elements> actionPageTableRows = getActionPageTableRows(actionsPage);

        if (actionPageTableRows.isPresent()) {
            legislativeStages.addAll(getHouseStages(actionPageTableRows.get(), stagesCount));
            stagesCount = legislativeStages.size();

            legislativeStages.addAll(getSenateStages(actionPageTableRows.get(), stagesCount));
            stagesCount = legislativeStages.size();

//            Ordering legislative stages by time
            for (int i = 0; i < legislativeStages.size(); i++) {
                for (LegislativeStage legStage : legislativeStages) {

                    if (!legislativeStages.get(i).getDate().isBefore(legStage.getDate()) && legislativeStages.get(i)
                            .getStageNumber() < legStage.getStageNumber()) {

                        int tmpStageNumber = legislativeStages.get(i).getStageNumber();
                        legislativeStages.get(i).setStageNumber(legStage.getStageNumber());
                        legStage.setStageNumber(tmpStageNumber);
                    }
                }
            }

            getSignedByPresidentStage(actionPageTableRows.get(), stagesCount).ifPresent(legislativeStages::add);
            stagesCount = legislativeStages.size();
            getBecameLawStage(actionPageTableRows.get(), stagesCount).ifPresent(legislativeStages::add);
            stagesCount = legislativeStages.size();

            record.setStagesCount(stagesCount);
        }
        return legislativeStages;
    }

    private Optional<Elements> getActionPageTableRows(Document actionsPage) {
        return Optional.ofNullable(actionsPage.body().getElementsByClass("expanded-actions").first())
                .map(tr -> tr.getElementsByTag("tr"));
    }

    private List<LegislativeStage> getHouseStages(Elements actionPageTableRows, int stagesCount) {
        List<LegislativeStage> houseStages = new ArrayList<>();

//            In the action table it can happen that, Chamber column is missing, in this case this info can be derived from the AllActions column
        Predicate<Element> filterChamberColumn = tr -> tr.getElementsByTag("td").get(1).text().equals("House");
        Predicate<Element> filterAllActionsColumn = tr -> tr.getElementsByTag("td").get(1).text()
                .contains("Action By: House of Representatives");

        List<Element> houseRelatedRows = actionPageTableRows.stream().skip(1)
                .filter(filterChamberColumn.or(filterAllActionsColumn)).toList();

//          Have at least one row, collect the first action
        if (!houseRelatedRows.isEmpty()) {
            stagesCount++;
            houseStages.add(
                    getLegislativeStage(houseRelatedRows.get(houseRelatedRows.size() - 1), "House - ", stagesCount));
        }
//          Have more than one row, collect the last action
        if (houseRelatedRows.size() > 1) {
            stagesCount++;
            houseStages.add(getLegislativeStage(houseRelatedRows.get(0), "House - ", stagesCount));
        }

        return houseStages;
    }

    private List<LegislativeStage> getSenateStages(Elements actionPageTableRows, int stagesCount) {
        List<LegislativeStage> senateStages = new ArrayList<>();

//            In the action table it can happen that, Chamber column is missing, in this case this info can be derived from the AllActions column
        Predicate<Element> filterChamberColumn = tr -> tr.getElementsByTag("td").get(1).text().equals("Senate");
        Predicate<Element> filterAllActionsColumn = tr -> tr.getElementsByTag("td").get(1).text()
                .contains("Action By: Senate");

        List<Element> senateRelatedRows = actionPageTableRows.stream().skip(1)
                .filter(filterChamberColumn.or(filterAllActionsColumn)).toList();

//            Have at least one row, collect the first action
        if (!senateRelatedRows.isEmpty()) {
            stagesCount++;
            senateStages.add(
                    getLegislativeStage(senateRelatedRows.get(senateRelatedRows.size() - 1), "Senate - ", stagesCount));
        }
//            Have more than one row, collect the last action
        if (senateRelatedRows.size() > 1) {
            stagesCount++;
            senateStages.add(getLegislativeStage(senateRelatedRows.get(0), "Senate - ", stagesCount));
        }

        return senateStages;
    }

    private Optional<LegislativeStage> getSignedByPresidentStage(Elements actionPageTableRows, int stagesCount) {
        Optional<LegislativeStage> signedByPresidentStage = Optional.empty();
        Predicate<Element> filterSignedByPresident = tr -> tr.getElementsByTag("td").last().text()
                .contains("Signed by President.");

        Optional<Element> signedByPresidentRow = actionPageTableRows.stream().skip(1).filter(filterSignedByPresident)
                .findFirst();

        if (signedByPresidentRow.isPresent()) {
            stagesCount++;
            signedByPresidentStage = Optional.of(getLegislativeStage(signedByPresidentRow.get(), "", stagesCount));
        }

        return signedByPresidentStage;
    }

    private Optional<LegislativeStage> getBecameLawStage(Elements actionPageTableRows, int stagesCount) {
        Optional<LegislativeStage> becameLawStage = Optional.empty();
        Predicate<Element> filterBecameLaw = tr -> tr.getElementsByTag("td").last().text().contains(" Law No:");

        Optional<Element> becameLawRow = actionPageTableRows.stream().skip(1).filter(filterBecameLaw).findFirst();

        if (becameLawRow.isPresent()) {
            stagesCount++;
            becameLawStage = Optional.of(getLegislativeStage(becameLawRow.get(), "", stagesCount));
        }

        return becameLawStage;
    }

    private LegislativeStage getLegislativeStage(Element actionTableRow, String chamber, int stagesCount) {
        LegislativeStage legislativeStage = new LegislativeStage();
        String dateString =
                Optional.ofNullable(actionTableRow.getElementsByTag("td").first())
                        .map(Element::text)
                        .orElse("");
        Matcher matcher = Constants.DATE_REGEX.matcher(dateString);

        if (matcher.find()) {
            legislativeStage.setDate(DateUtils.parseUsaDate(matcher.group()));
            legislativeStage.setName(
                    chamber + Optional.ofNullable(actionTableRow.getElementsByTag("td").last()).map(Element::text)
                            .orElse(""));
            legislativeStage.setStageNumber(stagesCount);
        }

        return legislativeStage;
    }

    private LegislativeDataRecord.ProcedureType getProcedureTypeStandard(Document actionsPage) {
        LegislativeDataRecord.ProcedureType procedureType = LegislativeDataRecord.ProcedureType.REGULAR;

        Optional<String> type =
                Optional.ofNullable(actionsPage.body().getElementsByClass("expanded-actions").first())
                        .map(expandedActions -> expandedActions.getElementsByTag("tr"))
                        .map(rows -> rows.stream().map(text -> text.text().toLowerCase())
                                .filter(text -> text.contains("suspend")).findFirst())
                        .orElse(Optional.empty());

        if (type.isPresent()) {
            procedureType = LegislativeDataRecord.ProcedureType.EXCEPTIONAL;
        }

        return procedureType;
    }

    private Set<String> getModifiedLaws(Document actionsPage, LegislativeDataRecord record,
                                        Set<String> congressionalRecordUrls) {
        //Both modified law ids and plenary size can be collected from the same Congressional record page
        Set<String> modifiedLawUrls = getModifiedLawUrls(actionsPage, record, congressionalRecordUrls);
        Set<String> modifiedLawIds = new TreeSet<>();

        for (String url : modifiedLawUrls) {
            try {
                Document modifiedLawPage = commonFunctions.getPageFromDbOrDownload(PageTypes.MODIFIED_LAW.name(), url);

                Optional<String> txtBox =
                        Optional.ofNullable(modifiedLawPage.body().getElementsByClass("styled").first())
                                .map(text -> text.text().toLowerCase());

                if (txtBox.isPresent()) {
                    String pageText = txtBox.get();

                    if (record.getPlenarySize() == null) {
                        record.setPlenarySize(TextUtils.getLengthWithoutWhitespace(pageText));
                    }

                    String cleanText =
                            pageText.replace("\n", "")
                                    .replaceAll("\s+", " ")
                                    .replaceAll("- ", "-");

                    modifiedLawIds =
                            Constants.MODIFIED_LAW_PATTERN.matcher(cleanText)
                                    .results().map(MatchResult::group)
                                    .map(String::trim)
                                    .collect(Collectors.toSet());

                }
            } catch (PageNotFoundException ex) {
                log.error("Modified law page not found ", ex);
            }
        }

        return modifiedLawIds;
    }

    private Set<String> getModifiedLawUrls(Document actionsPage, LegislativeDataRecord record,
                                           Set<String> congressionalRecordUrls) {
        Set<String> modifiedLawUrls = new LinkedHashSet<>();
        Optional<Elements> actionPageTableRows =
                Optional.ofNullable(actionsPage.body().getElementsByClass("expanded-actions").first())
                        .map(tr -> tr.getElementsByTag("tr"));

        Set<Element> aTagElements = new HashSet<>();

        actionPageTableRows
                .ifPresent(elements -> elements
                        .stream().skip(1)
                        .map(td -> td.getElementsByTag("td").last())
                        .filter(Objects::nonNull)
                        .map(td -> td.getElementsContainingText("CR").first())
                        .filter(Objects::nonNull)
                        .map(cr -> cr.getElementsByTag("a"))
                        .forEach(aTagElements::addAll));

        aTagElements.forEach(a -> {
            String link = a.attr("href");
            if (link.contains("section/page/")) {
                congressionalRecordUrls.add(SITE_BASE_URL + link);
            }
        });

        Document congressionalRecordPage;

        for (String url : congressionalRecordUrls) {
            try {
                congressionalRecordPage =
                        commonFunctions.getPageFromDbOrDownload(PageTypes.CONGRESSIONAL_RECORD.name(), url);

                Optional<List<String>> modifiedLawUrl =
                        Optional.ofNullable(congressionalRecordPage.body().getElementsByClass("item_table").first())
                                .map(tr -> tr.getElementsByTag("tr").stream().skip(1)
                                        .map(td -> td.getElementsByTag("td").first()).filter(Objects::nonNull)
                                        .map(a -> a.getElementsByTag("a").first()).filter(Objects::nonNull)
                                        .filter(a -> FuzzySearch.ratio(a.text().toLowerCase(),
                                                record.getBillTitle().toLowerCase()) >= 50)
                                        .map(link -> SITE_BASE_URL + link.attr("href")).collect(Collectors.toList()));

                modifiedLawUrl.ifPresent(modifiedLawUrls::addAll);
            } catch (PageNotFoundException ex) {
                log.error("Congressional record page not found");
            }
        }

        return modifiedLawUrls;
    }
}
