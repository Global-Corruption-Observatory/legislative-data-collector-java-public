package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.precognox.ceu.legislative_data_collector.brazil_new.Utils.DATE_REGEX;
import static com.precognox.ceu.legislative_data_collector.brazil_new.Utils.toAbsolute;

/**
 * Parses the stored bill pages from the LexML website. Creates the records in the bill_main_table, but collects only a subset of the variables. The rest of the variables are collected in the next steps.
 *
 * Parses the following variables:
 *  - billId
 *  - billPageUrl
 *  - alternative bill IDs
 *  - billTitle
 *  - dateIntroduction
 *  - datePassing
 *  - lawId
 *  - lawTextUrl
 *  - camaraPageUrl
 *  - senadoPageUrl
 */
@Slf4j
@Service
public class LexMlPageParser {

    private final PageSourceRepository pageSourceRepository;
    private final PrimaryKeyGeneratingRepository legislativeDataRecordRepository;

    @Autowired
    public LexMlPageParser(
            PageSourceRepository pageSourceRepository,
            PrimaryKeyGeneratingRepository legislativeDataRecordRepository) {
        this.pageSourceRepository = pageSourceRepository;
        this.legislativeDataRecordRepository = legislativeDataRecordRepository;
    }

    @Transactional
    public void processAll() {
        //process stored bill details pages
        pageSourceRepository.streamUnprocessedPages(Country.BRAZIL, PageType.LEXML_BILL_DETAILS.name())
                .peek(page -> log.info("Parsing legislative data record from URL: {}", page.getPageUrl()))
                .map(this::parsePage)
                .forEach(legislativeDataRecordRepository::save);

        //speed up processing as much as possible?
    }

    public LegislativeDataRecord parsePage(PageSource source) {
        LegislativeDataRecord result = new LegislativeDataRecord(Country.BRAZIL);
        result.setBillPageUrl(source.getPageUrl());

        Document parsed = Jsoup.parse(source.getRawSource());
        Elements panels = parsed.body().select("div.panel-body");

        Element firstPanel = panels.get(0);

        //get bill IDs from the Títuló section
        Pattern billIdRegex = Pattern.compile(" \\d+/\\d{4}");

        Optional<String> tituloText = firstPanel
                .selectXpath("//strong[text()='Título']/parent::div/following-sibling::div[1]")
                .stream()
                .map(Element::text)
                .findFirst();

        if (tituloText.isPresent()) {
            List<String> billIds = billIdRegex
                    .matcher(tituloText.get())
                    .results()
                    .map(MatchResult::group)
                    .map(String::strip)
                    .distinct()
                    .toList();

            Pattern billTitleRegex = Pattern.compile("(PL|Projeto de Lei)(.+?)? \\d+/\\d{4}");

            billTitleRegex.matcher(tituloText.get())
                    .results()
                    .map(MatchResult::group)
                    .findFirst()
                    .ifPresent(result::setBillTitle);

            String apelidoTextXpath = "//strong[text()='Apelido']/parent::div/following-sibling::div[1]";
            Elements apelidoTextElements = firstPanel.selectXpath(apelidoTextXpath);
            Optional<String> apelidoText = apelidoTextElements.stream()
                    .map(Element::text)
                    .findFirst();

            if (apelidoText.isPresent()) {
                //law id is LEI-number-date under Apelido section, example: LEI-10769-2003-11-19
                Optional<String> lawId = Pattern.compile("LEI-\\d+-\\d{4}-\\d{2}-\\d{2}")
                        .matcher(apelidoText.get())
                        .results()
                        .map(MatchResult::group)
                        .findFirst();

                lawId.ifPresent(id -> {
                    result.setLawId(id);

                    //parse date passing from law id
                    Pattern.compile("\\d{4}-\\d{2}-\\d{2}")
                            .matcher(id)
                            .results()
                            .map(MatchResult::group)
                            .map(LocalDate::parse)
                            .map(LocalDate::from)
                            .findFirst()
                            .ifPresent(result::setDatePassing);
                });

                if (billIds.isEmpty()) {
                    //check Apelido section if bill id is not found
                    billIds = apelidoText
                            .map(billIdRegex::matcher)
                            .map(Matcher::results)
                            .get()
                            .map(MatchResult::group)
                            .distinct()
                            .toList();
                }
            }

            if (!billIds.isEmpty()) {
                result.setBillId(billIds.get(0));
                result.getBrazilCountrySpecificVariables().setAlternativeBillIds(billIds.subList(1, billIds.size()));
            }
        }

        //parse date intro
        Elements dataTextElements = firstPanel.selectXpath(
                "//strong[text()='Data']/parent::div/following-sibling::div[1]");

        dataTextElements.stream()
                .map(Element::text)
                .filter(text -> text.matches(DATE_REGEX))
                .map(Utils::parseDate)
                .map(LocalDate::from)
                .findFirst()
                .ifPresent(result::setDateIntroduction);

        //parse law text url
        Elements lawTextLinks = firstPanel.selectXpath(
                "//strong[text()='Norma Gerada']/parent::div/following-sibling::div[1]/a");

        //parse law ID from law text link if not already set
        if (result.getLawId() == null) {
            lawTextLinks.stream()
                    .findFirst()
                    .map(Element::text)
                    .flatMap(this::transformLawId)
                    .ifPresent(result::setLawId);
        }

        List<String> links = lawTextLinks.stream()
                .map(element -> element.attr("href"))
                .distinct()
                .toList();

        if (!links.isEmpty()) {
            //should be one unique link
            String first = toAbsolute(links.get(0));
            result.setLawTextUrl(first);

            if (links.size() > 1) {
                log.warn("{} law text links found for bill: {}", links.size(), result.getBillId());
            }
        }

        //store links for later processing
        Element camaraLink = firstPanel.selectFirst("a[href*=http://www.camara.gov.br/]");
        if (camaraLink != null) {
            result.getBrazilCountrySpecificVariables().setCamaraPageUrl(camaraLink.attr("href"));
        }

        Element senadoLink = firstPanel.selectFirst("a[href*=https://legis.senado.gov.br/]");
        if (senadoLink != null) {
            result.getBrazilCountrySpecificVariables().setSenadoPageUrl(senadoLink.attr("href"));
        }

        return result;
    }

    private Optional<String> transformLawId(String lawRef) {
        //example: Lei nº 10769 de 19/11/2003 -> LEI-10769-2003-11-19
        return Pattern.compile("Lei nº (\\d+) de (\\d{2})/(\\d{2})/(\\d{4})")
                .matcher(lawRef.replace(".", ""))
                .results()
                .findFirst()
                .map(match -> {
                    return MessageFormat.format(
                            "LEI-{0}-{1}-{2}-{3}",
                            match.group(1),
                            match.group(4),
                            match.group(3),
                            match.group(2)
                    );
                });
    }

}
