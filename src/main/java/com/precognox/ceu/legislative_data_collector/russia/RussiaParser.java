package com.precognox.ceu.legislative_data_collector.russia;

import com.jayway.jsonpath.DocumentContext;
import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeStage;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.utils.JsonPathUtils;
import com.precognox.ceu.legislative_data_collector.utils.TextUtils;
import com.precognox.ceu.legislative_data_collector.utils.XmlUtils;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.precognox.ceu.legislative_data_collector.utils.DateUtils.toLocalDate;
import static com.precognox.ceu.legislative_data_collector.utils.ParserHelper.getStringValue;
import static com.precognox.ceu.legislative_data_collector.utils.TextUtils.distinctByKey;
import static com.precognox.ceu.legislative_data_collector.utils.TextUtils.findText;
import static com.precognox.ceu.legislative_data_collector.utils.TextUtils.findTexts;
import static com.precognox.ceu.legislative_data_collector.utils.XmlUtils.find;
import static com.precognox.ceu.legislative_data_collector.utils.XmlUtils.findAttribute;
import static com.precognox.ceu.legislative_data_collector.utils.XmlUtils.findElementText;
import static com.precognox.ceu.legislative_data_collector.utils.XmlUtils.findFirst;

@Slf4j
public class RussiaParser {

    public static final String DATE_FORMAT = "dd.MM.yyyy";
    public static final String КОМИТЕТ = "Комитет ";
    public static final String SZRF_BASE_URL = "https://www.szrf.ru/szrf/docslist.php?nb=100&div_id=1&numb=&tn=0&tx=&ora=0&st=";
    public static final String SOZD_DUMA_DOC_BASE_URL = "https://sozd.duma.gov.ru";
    public static final String SZRF_DOC_BASE_URL = "https://www.szrf.ru/szrf/";
    public static final String BILL_STATUS_PASS = "Закон опубликован".toLowerCase();
    public static final String BILL_STATUS_REJECT_1 = "Отклонить законопроект".toLowerCase();
    public static final String BILL_STATUS_REJECT_2 = "Снять закон с рассмотреният".toLowerCase();
    public static final String BILL_STATUS_REJECT_3 = "Снять законопроект с рассмотрения Государственной Думы в связи с отзывом субъектом права законодательной инициативы".toLowerCase();

    /**
     * Parses one stored page source.
     *
     * @param pageSource
     * @param pageCache
     * @return
     */
    public synchronized static LegislativeDataRecord parseRowData(PageSource pageSource, Function<String, String> pageCache) {
        log.info("Parse row: " + pageSource.getPageUrl());

        Unirest.config().verifySsl(false);

        DocumentContext context = JsonPathUtils.parseJson(pageSource.getRawSource());
        Map<String, Object> rawMap = JsonPathUtils.findByJsonPath(context, "$");

        LegislativeDataRecord data = new LegislativeDataRecord();
        data.setCountry(Country.RUSSIA);

        Map<String, Object> listItem = (Map<String, Object>) rawMap.get("listItem");
        String billID = getStringValue(listItem, "Номер, наименование");
        String billTitle = billID;
        billID = billID.split("[^\\d-]")[0];
        data.setBillId(billID);
        billTitle = billTitle.replaceFirst("^[^\n]+\n", "").trim();
        data.setBillTitle(billTitle);

        String originType = getStringValue(listItem, "СПЗИ");
        data.setOriginType(toOriginType(originType));
        String originatorName = originType;
        List<String> originatorNames = findTexts(originatorName, "[,]? ([\\p{IsCyrillic}][.][\\p{IsCyrillic}][.][\\p{IsCyrillic}]+)");
        List<Originator> originators = originatorNames.stream().map(name -> new Originator(name)).collect(Collectors.toList());
        data.setOriginators(originators);

//        96700347-2  Тематический блок законопроектов
//Социальная политика
        String details = getStringValue(rawMap, "details");
        Document detailsPage = XmlUtils.parseXml(details);
        String billType = findElementText(detailsPage, "//tr[td[node()='Тематический блок законопроектов']]/td[2]").trim();
        data.setBillType(billType);

        String billStatus = getStringValue(listItem, "Последнее событие");
        data.setBillStatus(toBillStatus(billStatus));

        String oz_name = findElementText(detailsPage, "//span[@id='oz_name']").trim();

        if (oz_name.contains("О внесении изменени") || oz_name.contains("О внесении дополнени")) {
            List<String> modified_law_texts = findTexts(oz_name, "[\"]([^\"]+)[\"]");
            data.getModifiedLaws().addAll(modified_law_texts);
            data.setModifiedLawsCount(modified_law_texts.size());
        }
        data.setDateIntroduction(null);
        List<LegislativeStage> stages = new ArrayList<>();
        int stageNumber = 1;
        Elements stageElements = find(detailsPage, "//div[@id='bh_hron']//div[@class='bh_etap_date']/span[contains(@class, 'mob_not')]//ancestor::div[contains(@class, 'root-stage bh_item')]");
        for (Element element : stageElements) {
            LegislativeStage stage = new LegislativeStage();
            stage.setStageNumber(stageNumber);
            stage.setDate(toLocalDate(findElementText(element, "//div[@class='bh_etap_date']/span[contains(@class, 'mob_not')]"), DATE_FORMAT));
            if (data.getDateIntroduction() == null) {
                data.setDateIntroduction(stage.getDate());
            }
            String name = findElementText(element, "//div[@class='ttl']/a");
            stage.setName(name);

            stages.add(stage);
            stageNumber++;
        }
        data.setStages(stages);
        data.setStagesCount(stages.size());

        data.setDatePassing(data.getStages().stream()
                .filter(stage -> stage.getName().equalsIgnoreCase("Рассмотрение закона Президентом Российской Федерации"))
                .map(stage -> stage.getDate())
                .findAny().orElse(null));

        Optional<LegislativeStage> lawPublishing = data.getStages().stream().filter(stage -> stage.getName().equalsIgnoreCase("Опубликование закона")).findAny();
        if (lawPublishing.isPresent()) {
            data.setDateEnteringIntoForce(lawPublishing.get().getDate());
        }

        List<Committee> committees = new ArrayList<>();
        String committee1 = findElementText(detailsPage, "//tr[.//span[contains(text()[1], 'Профильный комитет')]]/td[2]");
        String committee2 = findElementText(detailsPage, "//tr[.//span[contains(text()[1], 'Ответственный комитет')]]/td[2]");
        String committee3 = findElementText(detailsPage, "//tr[.//span[contains(text()[1], 'Комитеты-соисполнители')]]/td[2]");

        committees.addAll(toCommitteeList(committee1, "Specialist committee"));
        committees.addAll(toCommitteeList(committee2, "Resposible committee"));
        committees.addAll(toCommitteeList(committee3, "Co-executive committees"));
        data.setCommittees(committees);

        if (data.getStagesCount() > 1) {
            data.setCommitteeDate(data.getStages().get(1).getDate());
        }
        Long committeeCount = committees.stream().filter(distinctByKey(Committee::getName)).count();
        data.setCommitteeCount(committeeCount.intValue());
//        committee_hearing_count
        Elements committeeHearingElements = find(detailsPage, "//span[contains(text()[1], 'Принятие ответственным комитетом решения о представлении законопроекта в Совет Государственной Думы')] " +
                "| //span[contains(text()[1], 'Принятие профильным комитетом решения о представлении законопроекта в Совет Государственной Думы')]");
        data.setCommitteeHearingCount(committeeHearingElements.size());

        data.setBillTextUrl(findAttribute(detailsPage, "//a[.//div[contains(text()[1], 'Текст внесенного законопроекта')]]", "href"));
        if (StringUtils.isNotBlank(data.getBillTextUrl()) && data.getBillText() == null) {
            String billTextUrl = data.getBillTextUrl().startsWith(SOZD_DUMA_DOC_BASE_URL) ? data.getBillTextUrl() : SOZD_DUMA_DOC_BASE_URL + data.getBillTextUrl();
            data.setBillTextUrl(billTextUrl);
            data.setBillText(downloadFileAndConvertToText(pageCache, data.getBillTextUrl()));
        }

        if (StringUtils.isNotBlank(data.getBillTextUrl())) {
            String billTextUrl = data.getBillTextUrl().startsWith(SOZD_DUMA_DOC_BASE_URL) ? data.getBillTextUrl() : SOZD_DUMA_DOC_BASE_URL + data.getBillTextUrl();
            data.setBillTextUrl(billTextUrl);
        }

        if (StringUtils.isNotBlank(data.getBillText())) {
            data.setBillText(data.getBillText().replaceFirst("^[\n]*[\\d]+\n", "").trim());
        }

        //        Law_size, Law_text, Law_text_url
        if (StringUtils.isBlank(data.getLawText())) {
            String lawTextUrl = findAttribute(detailsPage, "//a[contains(@href, 'http://publication.pravo.gov.ru/Document/View/')]", "href");
            if (StringUtils.isNotBlank(lawTextUrl)) {
                lawTextUrl = lawTextUrl.replace("http://publication.pravo.gov.ru/Document/View/", "http://actual.pravo.gov.ru/text.html#pnum=");
                data.setLawTextUrl(lawTextUrl);

                String lawText = downloadFileAndConvertToText(pageCache, lawTextUrl);
                data.setLawText(lawText);
            } else {
                String szrfArticleNumber = findElementText(detailsPage, "//span[@data-original-title=\"Номер статьи в СЗ РФ\"]");
                String szrfPublicationYear = findElementText(detailsPage, "//span[@data-original-title=\"Год опубликования в СЗ РФ\"]");
                if (StringUtils.isNotBlank(szrfArticleNumber)) {
                    setupLawData(data, szrfArticleNumber, szrfPublicationYear);
                }
            }
        }
        if (StringUtils.isNotBlank(data.getLawText()) && data.getLawText().contains("var title")) {
            String lawText = data.getLawText().replaceFirst("(?ms)^.*var title.*monospace;}", "").trim();
            data.setLawText(lawText);
        }

//        Amendment_count, Amendment_id, Amendment_text
        Elements amendmentElements = find(detailsPage, "//div[@id=\"bh_histras\"]//a[.//div[@class='doc_wrap'][contains(text()[1], 'Таблица поправок, рекомендуемых к принятию')]]");
        List<Amendment> baseAmendmentList = new ArrayList<>();
        for (Element element : amendmentElements) {
            String amendmentUrl = element.attr("href");

            String textSourceUrl = amendmentUrl.startsWith("http") ? amendmentUrl : SOZD_DUMA_DOC_BASE_URL + amendmentUrl;

            Amendment amendment = findOrCreate(textSourceUrl, data);

            if (StringUtils.isBlank(amendment.getAmendmentText()) && StringUtils.isNotBlank(amendment.getTextSourceUrl())) {
                amendment.setAmendmentText(downloadFileAndConvertToText(pageCache, amendment.getTextSourceUrl()));
            }

            String committeeName = findText(element.text(), "[(]([^)]+)[)]");
            amendment.setCommitteeName(committeeName);

            baseAmendmentList.add(amendment);
        }
        data.setAmendmentCount(baseAmendmentList.size());

        Optional<Element> votingSection = findFirst(detailsPage, "//div[@id=\"bh_votes\"]");
        if (votingSection.isPresent()) {
            Elements votingRows = find(votingSection.get(), "//tr[.//div[contains(text()[1], 'принятые поправки')]]");
            int baseAmendmentListId = baseAmendmentList.size() - 1;
            Collections.reverse(votingRows);
            for (Element votingRow : votingRows) {
                Amendment amendment = baseAmendmentListId >= 0 ? baseAmendmentList.get(baseAmendmentListId) : new Amendment();
                baseAmendmentListId--;

                Element parent = votingRow.parent().parent().parent();
                String id = parent.attr("id");
                String voteDate = findElementText(parent.parent(), "//span[@data-target=\"#" + id + "\"]");
                LocalDate amendmentDate = toLocalDate(voteDate, "dd.MM.yyyy");

                amendment.setDate(amendmentDate);
                Optional<Element> resultColumn = findFirst(votingRow, "//td[2]");
                if (resultColumn.isPresent()) {
                    String resultColumnText = resultColumn.get().text();
                    if (resultColumnText.contains("Принят")) {
                        amendment.setOutcome(Amendment.Outcome.APPROVED);
                    } else if (resultColumnText.contains("Отклонен")) {
                        amendment.setOutcome(Amendment.Outcome.REJECTED);
                    }
                    amendment.setVotesInFavor(TextUtils.toInteger(findText(resultColumnText, "За: ([\\d]+)\\b"), 0));
                    amendment.setVotesAgainst(TextUtils.toInteger(findText(resultColumnText, "Против: ([\\d]+)\\b"), 0));
                    amendment.setVotesAbstention(TextUtils.toInteger(findText(resultColumnText, "Воздержалось: ([\\d]+)\\b"), 0));

                }
            }

            data.getAmendments().addAll(baseAmendmentList);
            data.getAmendments().forEach(amendment -> amendment.setDataRecord(data));

            Optional<Element> finalVotingRows = findFirst(votingSection.get(), "//tr[.//div[contains(text()[1], '(3 чтение)')]]");
            if (finalVotingRows.isPresent()) {
                String resultColumnText = finalVotingRows.get().text();
                data.setFinalVoteFor(TextUtils.toInteger(findText(resultColumnText, "За: ([\\d]+)\\b"), 0));
                data.setFinalVoteAgainst(TextUtils.toInteger(findText(resultColumnText, "Против: ([\\d]+)\\b"), 0));
                data.setFinalVoteAbst(TextUtils.toInteger(findText(resultColumnText, "Воздержалось: ([\\d]+)\\b"), 0));
            }
        }

        String plenaryText = findElementText(detailsPage, "//div[@id=\"bh_tab_content\"]");
        int plenaryTextLength = TextUtils.getLengthWithoutWhitespace(plenaryText);
        if (plenaryTextLength > 0) {
            data.setPlenarySize(plenaryTextLength);
        }

//        Original_law
        Boolean originalLaw;
        if (StringUtils.isNotBlank(data.getLawText())) {
            originalLaw = data.getLawText().contains("О внесении изменений в");
        } else {
            originalLaw = null;
        }
        data.setOriginalLaw(originalLaw);

        return data;
    }

    private static Amendment findOrCreate(String textSourceUrl, LegislativeDataRecord data) {
        return data.getAmendments().stream()
                .filter(origAmendment -> origAmendment.getTextSourceUrl().equals(textSourceUrl))
                .findAny()
                .orElseGet(() -> {
                    Amendment amendment = new Amendment();
                    amendment.setDataRecord(data);
                    amendment.setTextSourceUrl(textSourceUrl);
                    return amendment;
                });
    }

    private static void setupLawData(LegislativeDataRecord data, String szrfArticleNumber, String szrfPublicationYear) {
        String szrfUrl = SZRF_BASE_URL + szrfArticleNumber.replaceAll("[^\\d]", "");
        String szrfDetails = Unirest.get(szrfUrl).asString().getBody();
        Document szrfDetailsPage = XmlUtils.parseXml(szrfDetails);
        Elements szrfElements = find(szrfDetailsPage, "//table[@class='txt2']//table");
        for (Element element : szrfElements) {
            //e.g.:  //table[@class='txt2']//table//td[@class='docname']/b[contains(text(), ' г.')]
            String docname = findElementText(element, "//td[@class='docname']/b[contains(text(), ' г.')]");
            if (docname.contains(szrfPublicationYear + " г.")) {
                Elements link = find(element, "//a");
                String lawText = link.text();
                lawText += link.parents().first().ownText();
                String docUrl = (SZRF_DOC_BASE_URL + link.attr("href")).replace("/doc.php", "/text.php");
                String docDetails = Unirest.get(docUrl).asString().getBody();
                lawText += XmlUtils.xmlToText(docDetails);
                data.setLawText(lawText);
                data.setLawTextUrl(docUrl);
            }
        }
    }

    private static List<Committee> toCommitteeList(String committeeText, String role) {
        List<Committee> committees = new ArrayList<>();

        String[] committeeArray = committeeText.split("[,]?[\\s]+[Кк]омитет ");
        for (String name : committeeArray) {
            if (StringUtils.isNotBlank(name)) {
                name = name.contains("Комитет ") || name.contains("комитет ") ? name : КОМИТЕТ + name;
                committees.add(new Committee(name, role));
            }
        }

        return committees;
    }

    private static LegislativeDataRecord.BillStatus toBillStatus(String billStatusText) {
        LegislativeDataRecord.BillStatus billStatus;
        billStatusText = billStatusText.toLowerCase();
        if (billStatusText.contains(BILL_STATUS_PASS)) {
            billStatus = LegislativeDataRecord.BillStatus.PASS;
        } else if (billStatusText.contains(BILL_STATUS_REJECT_1) ||
                billStatusText.contains(BILL_STATUS_REJECT_2) ||
                billStatusText.contains(BILL_STATUS_REJECT_3)
        ) {
            billStatus = LegislativeDataRecord.BillStatus.REJECT;
        } else {
            billStatus = LegislativeDataRecord.BillStatus.ONGOING;
        }

        return billStatus;
    }

    public static OriginType toOriginType(String originTypeText) {
        OriginType originType;
        if (originTypeText.equalsIgnoreCase("Президент Российской Федерации")) {
            originType = OriginType.PRESIDENT;
        } else if (originTypeText.toLowerCase().contains("Депутат ГД".toLowerCase())) {
            originType = OriginType.INDIVIDUAL_MP;
        } else if (originTypeText.toLowerCase().contains("Депутаты ГД".toLowerCase())) {
            originType = OriginType.GROUP_MP;
        } else if (originTypeText.toLowerCase().contains("Правительство Российской Федерации".toLowerCase())) {
            originType = OriginType.GOVERNMENT;
        } else {
            originType = OriginType.OTHER;
        }
        return originType;
    }

    private static String downloadFileAndConvertToText(Function<String, String> pageCache, String url) {
        return pageCache.apply(url);
    }
}
