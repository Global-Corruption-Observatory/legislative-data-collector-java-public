package com.precognox.ceu.legislative_data_collector.russia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precognox.ceu.legislative_data_collector.common.ChromeBrowserFactory;
import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.utils.DocUtils;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
import org.apache.tika.exception.TikaException;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.xml.sax.SAXException;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;

@Slf4j
@Service
public class RussiaFixer {

    private final PrimaryKeyGeneratingRepository recordRepository;
    private final PageSourceRepository pageSourceRepository;
    private final EntityManager entityManager;
    private final ChromeBrowserFactory browserFactory;
    private final TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper;

    private ChromeDriver browser;

    private static final boolean DRY_RUN = false;
    private static final Locale RUS_LOCALE = new Locale("rus");
    private static final Pattern DATE_REGEX = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}");
    private static final DateTimeFormatter DATE_PARSER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String BILL_PAGE_URL_TEMPLATE = "https://sozd.duma.gov.ru/bill/{0}";

    private static final Pattern VOTES_FOR_REGEX = Pattern.compile("За: (\\d+)");
    private static final Pattern VOTES_AGAINST_REGEX = Pattern.compile("Против: (\\d+)");
    private static final Pattern VOTES_ABSTAIN_REGEX = Pattern.compile("Воздержалось: (\\d+)");

    private static final String AMENDMENTS_DOC_FILE_LABEL = "Таблица поправок, рекомендуемых к принятию";
    private static final String AMENDMENT_VOTES_LABEL = "принятые поправки";
    private static final Pattern AMENDMENT_COMMITTEE_REGEX = Pattern.compile("\\(Комитет .+?\\)");

    public static final String COMMITTEE_STAGE_LABEL
            = "Прохождение законопроекта у Председателя Государственной Думы";
    public static final String PRESIDENT_CONSIDERATION_LABEL
            = "Рассмотрение закона Президентом Российской Федерации";

    private static final int LAW_TEXT_WAIT_TIMEOUT = 15;

    @Autowired
    @SneakyThrows
    public RussiaFixer(
            PrimaryKeyGeneratingRepository recordRepository,
            PageSourceRepository pageSourceRepository,
            EntityManager entityManager,
            ChromeBrowserFactory browserFactory,
            TransactionTemplate transactionTemplate) {
        this.recordRepository = recordRepository;
        this.pageSourceRepository = pageSourceRepository;
        this.entityManager = entityManager;
        this.browserFactory = browserFactory;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public void reprocessAllRecords() {
        recordRepository.streamAll(Country.RUSSIA).forEach(record -> {
            fixRecord(record);

            if (!DRY_RUN) {
                recordRepository.mergeInNewTransaction(record);
            }

            log.info("Updated record {}", record.getRecordId());
        });
    }

    public void fixRecord(LegislativeDataRecord record) {
        String billUrl = MessageFormat.format(BILL_PAGE_URL_TEMPLATE, record.getBillId());
        record.setBillPageUrl(billUrl);

        //load source from db if exists
        Optional<PageSource> cleanSource = pageSourceRepository.findByPageTypeAndPageUrl("BILL", billUrl);

        if (cleanSource.isEmpty()) {
            log.warn("Getting old stored source (JSON) for bill: {}", billUrl);

            Optional<JsonNode> storedJson =
                    pageSourceRepository.findByPageTypeAndPageUrl("bill", billUrl)
                            .map(PageSource::getRawSource)
                            .map(this::parseJson);

            if (storedJson.isPresent()) {
                String storedSource = storedJson.get().findValue("details").textValue();
                String metadataJson = storedJson.get().findValue("listItem").toString();

                PageSource clean = new PageSource();
                clean.setCountry(Country.RUSSIA);
                clean.setPageType("BILL");
                clean.setPageUrl(billUrl);
                clean.setSize(storedSource.length());
                clean.setRawSource(storedSource);
                clean.setMetadata(metadataJson);

                cleanSource = Optional.of(clean);
                transactionTemplate.execute(status -> pageSourceRepository.save(clean));
            }
        } else {
            log.debug("Clean source found for bill: {}", billUrl);
        }

        cleanSource.ifPresentOrElse(clean -> processPageSource(record, clean), () -> {
            throw new IllegalStateException("No page source available: " + billUrl);
        });
    }

    @SneakyThrows
    private JsonNode parseJson(String json) {
        return objectMapper.readTree(json);
    }

    public void processPageSource(LegislativeDataRecord record, PageSource source) {
        Document parsedBillPage = Jsoup.parse(source.getRawSource());

        parseOriginType(parsedBillPage).ifPresent(record::setOriginType);
        parseBillTitle(parsedBillPage).ifPresent(record::setBillTitle);
        record.setOriginalLaw(parseOriginalLaw(record.getBillTitle()));
        parseStatus(parsedBillPage).ifPresent(record::setBillStatus);
        parseDatePassing(record, parsedBillPage).ifPresent(record::setDatePassing);
        parseCommitteeDate(parsedBillPage).ifPresent(record::setCommitteeDate);
        parseCommittees(record, parsedBillPage);
        record.setCommitteeHearingCount(parseCommitteeHearings(parsedBillPage));
        parsePlenarySize(parsedBillPage).ifPresent(record::setPlenarySize);
        fixAmendments(record, parsedBillPage);
        fixStages(record, parsedBillPage);
        fixVotes(record, parsedBillPage);

        record.setDateProcessed(LocalDateTime.now());
    }

    private Optional<OriginType> parseOriginType(Document parsedPage) {
        Element origTypeCell = parsedPage.body()
                .selectFirst("td:contains(Субъект права законодательной инициативы)");

        return Optional.ofNullable(origTypeCell)
                .map(Element::nextElementSibling)
                .map(Element::text)
                .map(String::trim)
                .map(this::toOriginType);
    }

    private OriginType toOriginType(String originTypeText) {
        String normalized = originTypeText.toLowerCase();

        String indMpLabel = "Депутат ГД".toLowerCase();
        String groupMpLabel1 = "Депутаты ГД".toLowerCase();
        String groupMpLabel2 = "Депутаты Государственной Думы".toLowerCase();
        String govLabel = "Правительство Российской Федерации".toLowerCase();

        if (normalized.equalsIgnoreCase("Президент Российской Федерации")) {
            return OriginType.PRESIDENT;
        } else if (normalized.contains(govLabel)) {
            return OriginType.GOVERNMENT;
        } else if (normalized.contains(indMpLabel)) {
            return OriginType.INDIVIDUAL_MP;
        } else if (normalized.contains(groupMpLabel1) || normalized.contains(groupMpLabel2)) {
            return OriginType.GROUP_MP;
        }

        return OriginType.OTHER;
    }

    private Optional<String> parseBillTitle(Document parsedPage) {
        return Optional.ofNullable(parsedPage.body().selectFirst("div.bill_data_top"))
                .map(topDiv -> topDiv.selectFirst("span#oz_name"))
                .map(Element::text);
    }

    private Optional<LegislativeDataRecord.BillStatus> parseStatus(Document parsedPage) {
        return Optional.ofNullable(parsedPage.selectFirst("div#oz_stages"))
                .map(stagesDiv -> stagesDiv.select("div.child_etaps").last())
                .map(Element::text)
                .map(this::parseStatus);
    }

    private LegislativeDataRecord.BillStatus parseStatus(String text) {
        String passLabel = "Закон опубликован";

        List<String> rejectionLabels = List.of(
                "Отклонить законопроект",
                "Снять закон с рассмотрения",
                "Снять законопроект с рассмотрения Государственной Думы в связи с отзывом субъектом права законодательной инициативы",
                "Снять закон с рассмотрения Государственной Думы",
                "Верунть законопроект",
                "вернуть законопроект субъекту права законодательной инициативы для выполнения требований Конституции Российской Федерации и Регламента Государственной Думы",
                "вернуть законопроект субъекту права законодательной инициативы для выполнения требований Конституции Российской Федерации и Регламента Государственной Думы"
        );

        if (text.toLowerCase().contains(passLabel.toLowerCase())) {
            return LegislativeDataRecord.BillStatus.PASS;
        }

        if (rejectionLabels.stream().anyMatch(label -> text.toLowerCase().contains(label.toLowerCase()))) {
            return LegislativeDataRecord.BillStatus.REJECT;
        }

        return LegislativeDataRecord.BillStatus.ONGOING;
    }

    private boolean parseOriginalLaw(String billTitle) {
        List<String> modificationLabels = List.of(
                "О внесении изменений в Закон Российской Федерации",
                "О внесении изменений в Федеральный закон",
                "О внесении изменений и дополнений в Закон",
                "О внесении изменений и дополнений в федеральный закон",
                "О внесении изменений в статью",
                "О внесении дополнений в Федеральный закон",
                "О внесении дополнений в Закон",
                "О внесении изменений и дополнений в статью Федерального закона",
                "О внесении изменений и дополнений в статью Закона",
                "О внесении дополнений в статью Федерального закона",
                "О внесении дополнений в статью Закона"
        );

        return modificationLabels.stream().noneMatch(
                l -> billTitle.toLowerCase(RUS_LOCALE).contains(l.toLowerCase(RUS_LOCALE)));
    }

    private Optional<LocalDate> parseDatePassing(LegislativeDataRecord record, Document parsedPage) {
        if (LegislativeDataRecord.BillStatus.PASS.equals(record.getBillStatus())) {
            return getDatesByStageLabel(parsedPage, PRESIDENT_CONSIDERATION_LABEL).findFirst();
        }

        return Optional.empty();
    }

    private Stream<LocalDate> getDatesByStageLabel(Document parsedPage, String stageLabel) {
        String labelSelector = "span:contains(" + stageLabel + ")";

        return Optional.ofNullable(parsedPage.body().selectFirst(labelSelector))
                .map(span -> span.parents().get(2))
                .map(stageDiv -> stageDiv.select("div.bh_etap_date"))
                .stream()
                .flatMap(Elements::stream)
                .map(Element::text)
                .map(DATE_REGEX::matcher)
                .flatMap(Matcher::results)
                .map(MatchResult::group)
                .map(date -> LocalDate.parse(date, DATE_PARSER));
    }

    private Optional<LocalDate> parseCommitteeDate(Document parsedPage) {
        return getDatesByStageLabel(parsedPage, COMMITTEE_STAGE_LABEL).skip(1).findFirst();
    }

    private void parseCommittees(LegislativeDataRecord record, Document parsedPage) {
        Map<String, String> labelsAndRoles = Map.of(
                "Профильный комитет", "PROFILE COMMITTEE",
                "Ответственный комитет", "RESPONSIBLE COMMITTEE",
                "Комитеты-соисполнители", "CO-EXECUTIVE COMMITTEE",
                "профильный комитет", "SPECIALIST COMMITTEE"
        );

        String separator = ", Комитет";

        Optional<Element> billPassportDiv = Optional.ofNullable(parsedPage.selectFirst("div#opc_hild"));

        if (billPassportDiv.isPresent()) {
            List<Committee> committees = billPassportDiv.get().select("div.opch_l")
                    .stream()
                    .filter(commLabelDiv -> labelsAndRoles.containsKey(commLabelDiv.text().trim()))
                    .flatMap(commLabelDiv -> {
                        String role = labelsAndRoles.get(commLabelDiv.text().trim());

                        if ("td".equals(commLabelDiv.parent().nextElementSibling().tagName())) {
                            String commNames = commLabelDiv.parent().nextElementSibling().text().trim();

                            if (commNames.contains(separator)) {
                                String[] names = commNames.split(separator);

                                return Arrays.stream(names)
                                        .map(String::trim)
                                        .map(name -> name.startsWith("Комитет") ? name : "Комитет " + name)
                                        .map(name -> new Committee(name, role));
                            } else {
                                return Stream.of(new Committee(commNames, role));
                            }
                        }

                        return Stream.empty();
                    }).distinct().toList();

            long uniqueCount = committees.stream().map(Committee::getName).distinct().count();
            record.setCommitteeCount(Math.toIntExact(uniqueCount));
            record.setCommittees(committees);
        }
    }

    private int parseCommitteeHearings(Document parsedPage) {
        Stream<String> committeeStageLabels = Stream.of(
                "Принятие ответственным комитетом решения о представлении законопроекта в Совет Государственной Думы",
                "Принятие профильным комитетом решения о представлении законопроекта в Совет Государственной Думы"
        );

        Element stagesDiv = parsedPage.body().selectFirst("div#oz_stages");

        if (stagesDiv != null) {
            long count = committeeStageLabels.map(l -> stagesDiv.select("span:contains(%s)".formatted(l)))
                    .flatMap(Elements::stream)
                    .count();

            return Math.toIntExact(count);
        }

        return 0;
    }

    private void fixCommittees(LegislativeDataRecord record) {
        String separator = ",Комитет";

        List<Committee> wrongCommittees = record.getCommittees().stream()
                .filter(comm -> comm.getName().contains(separator))
                .toList();

        if (!wrongCommittees.isEmpty()) {
            List<Committee> newCommittees = new ArrayList<>();

            for (Committee probl : wrongCommittees) {
                String[] names = probl.getName().split(separator);

                for (int i = 0; i < names.length; i++) {
                    newCommittees.add(new Committee(i > 0 ? "Комитет " + names[i] : names[i], probl.getRole()));
                }
            }

            record.getCommittees().removeAll(wrongCommittees);
            record.getCommittees().addAll(newCommittees);

            long count = newCommittees.stream().map(Committee::getName).distinct().count();
            record.setCommitteeCount(Math.toIntExact(count));
        }
    }

    private Optional<Integer> parsePlenarySize(Document parsedPage) {
        return Optional.ofNullable(parsedPage.body().selectFirst("div#bh_trans"))
                .map(Element::text)
                .map(TextUtils::getLengthWithoutWhitespace);
    }

    @Transactional
    public void fixAllBillTexts() {
        String qlString =
                "select r from LegislativeDataRecord r where r.billTextUrl is not null and r.billText is null";

        entityManager.createQuery(qlString, LegislativeDataRecord.class).getResultStream()
                .forEach(record -> {
                    fixBillText(record);

                    if (record.getBillText() != null) {
                        recordRepository.mergeInNewTransaction(record);

                        log.info(
                                "Updated record {} with text size {}",
                                record.getRecordId(), record.getBillSize()
                        );
                    }
                });
    }

    private void fixBillText(LegislativeDataRecord record) {
        HttpResponse<byte[]> resp = Unirest.get(record.getBillTextUrl()).asBytes();

        if (resp.isSuccess()) {
            if (isDocFile(resp)) {
                try {
                    DocUtils.getTextFromDoc(resp.getBody()).ifPresent(billText -> {
                        record.setBillText(billText);
                        record.setBillSize(TextUtils.getLengthWithoutWhitespace(billText));
                    });
                } catch (TikaException | IOException | SAXException e) {
                    log.error("Exception for " + record.getBillTextUrl(), e);
                }
            } else {
                log.error("File type is not expected for URL {}", record.getBillTextUrl());
            }
        } else {
            log.error("HTTP call failed to {} with response {}", record.getBillTextUrl(), resp.getStatus());
        }
    }

    private static final List<String> PROCESSED_BILL_TEXT_TYPES = List.of(".rtf", ".doc", ".docx");

    private boolean isDocFile(HttpResponse<byte[]> resp) {
        String contentDisp = resp.getHeaders().getFirst(CONTENT_DISPOSITION).toLowerCase();

        return contentDisp != null && PROCESSED_BILL_TEXT_TYPES.stream().anyMatch(contentDisp::endsWith);
    }

    @SneakyThrows
    @Transactional
    public void fixAllLawTexts() {
        browser = browserFactory.create();

        String qlString = "select r from LegislativeDataRecord r"
                + " where r.lawText is null"
                + " and r.lawTextUrl like '%.html%'";

        Stream<LegislativeDataRecord> records =
                entityManager.createQuery(qlString, LegislativeDataRecord.class).getResultStream();

        try {
            fixLawTexts(records);
        } finally {
            browser.close();
        }
    }

    private void fixLawTexts(Stream<LegislativeDataRecord> records) {
        records.forEach(record -> {
            fixLawText(record);

            if (record.getLawText() != null) {
                recordRepository.mergeInNewTransaction(record);

                log.info(
                        "Updated record {} with text size {}",
                        record.getRecordId(), record.getLawSize()
                );
            }
        });
    }

    private void fixLawText(LegislativeDataRecord record) {
        browser.get(record.getLawTextUrl());

        try {
            browser.navigate().refresh();
            browser.switchTo().frame(0);

            new WebDriverWait(browser, Duration.ofSeconds(LAW_TEXT_WAIT_TIMEOUT)).until(
                    br -> !br.findElement(By.tagName("body")).getText().isBlank()
            );

            String lawText = browser.findElement(By.tagName("body")).getText();

            record.setLawText(lawText);
            record.setLawSize(TextUtils.getLengthWithoutWhitespace(lawText));
        } catch (WebDriverException e) {
            log.error("", e);
            log.error("Can not get law text from page: {}", record.getLawTextUrl());
        }
    }

    private void fixStages(LegislativeDataRecord record, Document parsedPage) {
        //iterate on all stage divs, process subelements one by one
        Stream<Element> stageDivs = Optional.ofNullable(parsedPage.body().selectFirst("div#oz_stages"))
                .map(stagesDiv -> stagesDiv.select("div.root-stage"))
                .stream()
                .flatMap(Elements::stream);

        //wrap list to allow sorting
        List<LegislativeStage> stages = new ArrayList<>(
                stageDivs.map(stageDiv -> parseStage(parsedPage, stageDiv)).toList()
        );

        stages.sort(Comparator.comparing(
                legislativeStage -> Optional.ofNullable(legislativeStage.getDate()).orElse(LocalDate.MAX)
        ));

        for (int i = 0; i < stages.size(); i++) {
            stages.get(i).setStageNumber(i + 1);
        }

        record.setStages(stages);
        record.setStagesCount(stages.size());
    }

    @NotNull
    private static LegislativeStage parseStage(Document parsedPage, Element stageDiv) {
        //example: showTranscript('tznum6')
        Pattern transcriptNumRegex = Pattern.compile("tznum(\\d+)");

        LegislativeStage stage = new LegislativeStage();

        Optional.ofNullable(stageDiv.selectFirst("div.ttl"))
                .map(Element::text)
                .map(s -> s.replace("\\n", "").trim())
                .ifPresent(stage::setName);

        stageDiv.select("div.bh_etap_date")
                .stream()
                .flatMap(div -> div.select("span.mob_not").stream())
                .map(Element::text)
                .map(dateStr -> LocalDate.parse(dateStr, DATE_PARSER))
                .findFirst()
                .ifPresent(stage::setDate);

        Optional<String> transcriptNum = Optional.ofNullable(stageDiv.selectFirst("a.videoduma_el"))
                .map(link -> link.attr("onclick"))
                .filter(onclick -> onclick.contains("showTranscript"))
                .map(transcriptNumRegex::matcher)
                .stream()
                .flatMap(Matcher::results)
                .map(matchResult -> matchResult.group(1))
                .findFirst();

        //example div id with transcript text = transcr13
        Optional<Element> transcriptDiv = Optional.ofNullable(parsedPage.selectFirst("div#bh_trans"));

        if (transcriptDiv.isPresent()) {
            Optional<Integer> debateSize = transcriptNum
                    .map(num -> transcriptDiv.get().selectFirst("div#transcr%s".formatted(num)))
                    .map(Element::text)
                    .map(TextUtils::getLengthWithoutWhitespace);

            debateSize.ifPresent(stage::setDebateSize);
        }

        return stage;
    }

    private void fixVotes(LegislativeDataRecord record, Document parsedPage) {
        if (LegislativeDataRecord.BillStatus.PASS.equals(record.getBillStatus())) {
            Optional<Element> votesDiv = Optional.ofNullable(parsedPage.body().selectFirst("div#bh_votes"));
            Optional<String> firstTableText = votesDiv.map(div -> div.selectFirst("table")).map(Element::text);

            firstTableText.ifPresent(text -> {
                getFirstGroupFromRegexMatch(text, VOTES_FOR_REGEX).ifPresent(record::setFinalVoteFor);
                getFirstGroupFromRegexMatch(text, VOTES_AGAINST_REGEX).ifPresent(record::setFinalVoteAgainst);
                getFirstGroupFromRegexMatch(text, VOTES_ABSTAIN_REGEX).ifPresent(record::setFinalVoteAbst);
            });
        }
    }

    private void fixAmendments(LegislativeDataRecord record, Document parsedPage) {
        if (!record.getAmendments().isEmpty()) {
            log.info("Skipping overwriting existing amendments for record {}", record.getRecordId());
        }

        Elements amLinks = parsedPage.body().select("a:contains(%s)".formatted(AMENDMENTS_DOC_FILE_LABEL));

        List<Amendment> amendments = StreamEx.of(amLinks)
                .distinct(e -> e.attr("href"))
                .map(this::parseAmendment)
                .peek(am -> am.setDataRecord(record))
                .toList();

        if (!amendments.isEmpty()) {
            Optional<Element> votesDiv = Optional.ofNullable(parsedPage.body().selectFirst("div#bh_votes"));

            List<Element> amendmentVotingRows = votesDiv
                    .map(div -> div.select("tr:contains(%s)".formatted(AMENDMENT_VOTES_LABEL)))
                    .stream()
                    .flatMap(Elements::stream)
                    .toList();

            if (!amendmentVotingRows.isEmpty()) {
                String trText = amendmentVotingRows.get(0).text();

                Amendment.Outcome outcome;

                if (trText.contains("Принят")) {
                    outcome = Amendment.Outcome.APPROVED;
                } else if (trText.contains("Отклонен")) {
                    outcome = Amendment.Outcome.REJECTED;
                } else {
                    outcome = null;
                }

                amendments.forEach(a -> a.setOutcome(outcome));

                getFirstGroupFromRegexMatch(trText, VOTES_FOR_REGEX).ifPresent(
                        forVotes -> amendments.forEach(a -> a.setVotesInFavor(forVotes)));
                getFirstGroupFromRegexMatch(trText, VOTES_AGAINST_REGEX).ifPresent(
                        votesAgainst -> amendments.forEach(a -> a.setVotesAgainst(votesAgainst)));
                getFirstGroupFromRegexMatch(trText, VOTES_ABSTAIN_REGEX).ifPresent(
                        absVotes -> amendments.forEach(a -> a.setVotesAbstention(absVotes)));
            }

            record.setAmendments(amendments);
            record.setAmendmentCount(amendments.size());
        }
    }

    private Amendment parseAmendment(Element amLink) {
        String linkText = amLink.text();

        Amendment amendment = new Amendment();
        amendment.setTextSourceUrl(toAbsoluteUrl(amLink.attr("href")));

        Optional<LocalDate> amDate = amLink.parents().stream()
                .filter(p -> p.tagName().equals("div") && p.classNames().contains("child_etaps"))
                .map(Element::text)
                .map(DATE_REGEX::matcher)
                .flatMap(Matcher::results)
                .map(MatchResult::group)
                .map(dateStr -> LocalDate.parse(dateStr, DATE_PARSER))
                .findFirst();

        amDate.ifPresent(amendment::setDate);

        //committee example:
        //Таблица поправок, рекомендуемых к принятию (раздел 7) (Комитет Государственной Думы по бюджету и налогам)
        AMENDMENT_COMMITTEE_REGEX.matcher(linkText)
                .results()
                .map(MatchResult::group)
                .map(commName -> commName.replace("(", "").replace(")", ""))
                .findFirst()
                .ifPresent(amendment::setCommitteeName);

        return amendment;
    }

    private Optional<Integer> getFirstGroupFromRegexMatch(String text, Pattern regex) {
        return regex.matcher(text)
                .results()
                .map(res -> res.group(1))
                .map(Integer::parseInt)
                .findFirst();
    }

    private String toAbsoluteUrl(String url) {
        return url.startsWith("http") ? url : "https://sozd.duma.gov.ru" + url;
    }

    @Transactional
    public void collectAmendmentTexts() {
        String qlString = "select a from Amendment a " +
                "where a.amendmentText is null " +
                "and a.textSourceUrl is not null";

        entityManager.createQuery(qlString, Amendment.class).getResultStream().forEach(this::collectText);
    }

    private void collectText(Amendment amendment) {
        Unirest.get(amendment.getTextSourceUrl()).asBytes()
                .ifSuccess(resp -> storeAmendmentText(amendment, resp))
                .ifFailure(resp -> log.error("Failed to get amendment text from {}", amendment.getTextSourceUrl()));
    }

    private void storeAmendmentText(Amendment amendment, HttpResponse<byte[]> resp) {
        try {
            if (isDocFile(resp)) {
                DocUtils.getTextFromDoc(resp.getBody()).ifPresent(amendment::setAmendmentText);
                transactionTemplate.execute(status -> entityManager.merge(amendment));
                log.info("Stored amendment text with size {}", amendment.getAmendmentText().length());
            } else {
                log.error("Not a word file: {}", amendment.getTextSourceUrl());
            }
        } catch (TikaException | IOException | SAXException e) {
            log.error("Failed to get amendment text from {}", amendment.getTextSourceUrl());
            log.error(e.toString());
        }
    }

    @Transactional
    public void removeDuplicateRecords() {
        String urlsQuery = "select r.billPageUrl from LegislativeDataRecord r " +
                "group by r.billPageUrl " +
                "having count(r) > 1 ";

        List<String> urlList = entityManager.createQuery(urlsQuery, String.class).getResultList();

        urlList.forEach(url -> {
            String q = "select r.id from LegislativeDataRecord r where r.billPageUrl = :url order by r.id";

            List<Long> idList = entityManager.createQuery(q, Long.class)
                    .setParameter("url", url)
                    .getResultList();

            idList.remove(0);

            transactionTemplate.execute(status -> {
                recordRepository.deleteAllById(idList);
                log.info("Removed {} records for URL: {}", idList.size(), url);

                return status;
            });
        });
    }

}
