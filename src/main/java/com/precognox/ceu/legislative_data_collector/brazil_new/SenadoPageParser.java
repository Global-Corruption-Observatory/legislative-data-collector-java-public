package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.AmendmentOriginator;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parses the bill pages from the Senado website, and extends existing records with new variables.
 *
 * Collects the following variables:
 * - date introduction if missing
 * - originator if missing
 * - bill type
 * - bill status if missing
 * - modified laws if missing?
 * - aff. laws first date
 * - stages
 * - committees if missing
 * - bill text url if missing
 */
@Slf4j
@Service
public class SenadoPageParser {

    private final PrimaryKeyGeneratingRepository recordRepository;
    private final PageSourceLoader pageSourceLoader;

    public static final List<String> MODIFICATION_PHRASES = List.of("dá nova redação", "modifica", "altera");

    @Autowired
    public SenadoPageParser(
            PrimaryKeyGeneratingRepository recordRepository,
            PageSourceLoader pageSourceLoader) {
        this.recordRepository = recordRepository;
        this.pageSourceLoader = pageSourceLoader;
    }

    @Transactional
    public void processAll() {
        recordRepository.streamAllWithSenadoPageUrl().forEach(this::processBill);
    }

    private void processBill(LegislativeDataRecord record) {
        //example: https://www25.senado.leg.br/web/atividade/materias/-/materia/154451 (contains bill type and stages)
        String url = record.getBrazilCountrySpecificVariables().getSenadoPageUrl();

        //check in db or download page
        Optional<PageSource> page = pageSourceLoader.loadFromDbOrFetchWithHttpGet(
                Country.BRAZIL, PageType.SENADO_BILL_DETAILS.name(), url
        );

        if (page.isPresent()) {
            parsePage(record, page.get());
            recordRepository.mergeInNewTransaction(record);
            log.info("Parsed Senado page for record: {}", record.getRecordId());
        } else {
            log.error("Failed to parse Senado page: {}", url);
        }
    }

    public void parsePage(LegislativeDataRecord record, PageSource page) {
        Element parsedPage = Jsoup.parse(page.getRawSource()).body();

        if (record.getBillTextUrl() == null) {
            Element billTextUrl = parsedPage.selectXpath("//a[text()[contains(.,'Texto inicial')]]").first();

            if (billTextUrl != null) {
                record.setBillTextUrl(billTextUrl.attr("href"));
            }
        }

        if (record.getOriginators().isEmpty() || record.getOriginType() == null) {
            String authorSelector = "//dt[text()[contains(.,'Autoria')]]/following-sibling::dd[1]";

            Optional.ofNullable(parsedPage.selectXpath(authorSelector).first())
                    .map(Element::text)
                    .map(this::parseOriginatorText)
                    .ifPresent(orig -> {
                        record.getOriginators().add(orig);
                        record.setOriginType(Utils.getOriginType(record.getOriginators()));
                    });
        }

        //date intro if missing
        if (record.getDateIntroduction() == null) {
            parsedPage.selectXpath("//div[@id='tramitacao']//div[@data-local='PLENARIO'][last()]//dt")
                    .stream()
                    .map(Element::text)
                    .filter(text -> text.matches("\\d{2}/\\d{2}/\\d{4}"))
                    .map(Utils::parseDate)
                    .map(LocalDate::from)
                    .findFirst()
                    .ifPresent(record::setDateIntroduction);
        }

        if (record.getCommitteeCount() == null) {
            List<Committee> comms =
                    parsedPage.selectXpath("//dt[text()='Providência legislativa:']/following-sibling::dd[1]/ul/li/ul/li")
                        .stream()
                        .map(Element::text)
                        .map(String::strip)
                        .map(commName -> new Committee(commName, null))
                        .toList();

            record.getCommittees().addAll(comms);
            record.setCommitteeCount(comms.size());
        }

        if (record.getOriginalLaw() == null) {
            Optional.ofNullable(parsedPage.selectXpath("//strong[text()='Ementa:']/following-sibling::span[1]").first())
                    .map(Element::text)
                    .map(this::isOriginalLaw)
                    .ifPresent(record::setOriginalLaw);
        }

        Optional<String> billTypeText =
                parsedPage.selectXpath("//dt[text()[contains(.,'Natureza')]]/following-sibling::dd[1]/span")
                        .stream()
                        .map(Element::text)
                        .map(String::strip)
                        .findFirst();

        billTypeText.ifPresent(record::setBillType);

        Optional<String> stagesPageLink = parsedPage.selectXpath("//a[text()[contains(.,'Tramitação bicameral')]]")
                .stream()
                .map(element -> element.attr("href"))
                .findFirst();

        stagesPageLink.ifPresent(record.getBrazilCountrySpecificVariables()::setStagesPageUrl);

        parseFinalVotesUrl(parsedPage)
                .ifPresent(link -> record.getBrazilCountrySpecificVariables().setVotesPageUrl(link));

        if (record.getBillStatus() == null) {
            //parse status from ‘Situação Atual’/‘Último estado’
            String selector = "//strong[text()[contains(.,'Situação Atual')]]";

            Optional<String> statusText = Optional.ofNullable(parsedPage.selectXpath(selector).first())
                    .map(Element::parent)
                    .map(Element::parent)
                    .map(div -> div.selectXpath("//dt[text()[contains(.,'Último estado')]]").first())
                    .map(Element::nextElementSibling)
                    .map(Element::text)
                    .map(String::toLowerCase);

            if (statusText.isPresent()) {
                Constants.BILL_STATUS_MAPPING.keySet()
                        .stream()
                        .filter(statusText.get()::contains)
                        .findFirst()
                        .map(Constants.BILL_STATUS_MAPPING::get)
                        .ifPresent(record::setBillStatus);
            }
        }

        //only parse amendments if they are missing for a record?
        //could be record id 125234:
        // - http://www.camara.gov.br/proposicoesWeb/fichadetramitacao?idProposicao=2192913
        // - https://legis.senado.gov.br/legis/resources/transparencia/portal-atividade?identificacao=PL 1077/2019
        List<Amendment> amendments = parseAmendments(parsedPage);
        amendments.forEach(am -> am.setDataRecord(record));
        record.getAmendments().addAll(amendments);
        record.setAmendmentCount(record.getAmendments().size());
    }

    private Boolean isOriginalLaw(String summaryText) {
        return MODIFICATION_PHRASES.stream().noneMatch(summaryText.toLowerCase()::contains);
    }

    private Originator parseOriginatorText(String text) {
        Matcher matcher = Pattern.compile("(.+?) \\((.+?)\\)").matcher(text);

        if (matcher.find()) {
            return new Originator(matcher.group(1).replace("Senador ", ""), matcher.group(2));
        }

        return new Originator(text);
    }

    private Optional<String> parseFinalVotesUrl(Element parsedPage) {
        //final votes example: https://www25.senado.leg.br/web/atividade/materias/-/materia/154451
        //find link that starts with Votação nominal do Projeto de Lei under section Votações Nominais
        return parsedPage.selectXpath("//div[@id='votacoes']//a[text()[contains(.,'Votação nominal do Projeto de Lei')]]")
                .stream()
                .map(element -> element.attr("href"))
                .findFirst();
    }

    private static final Map<String, Amendment.Outcome> OUTCOME_MAP = Map.of(
            "Rejeitada", Amendment.Outcome.REJECTED,
            "Aprovada", Amendment.Outcome.APPROVED
    );

    private List<Amendment> parseAmendments(Element parsedPage) {
        //amendments example: https://www25.senado.leg.br/web/atividade/materias/-/materia/133045
        Element amendmentsDiv = parsedPage.selectXpath("//div[@id='emendas']").first();

        return Optional.ofNullable(amendmentsDiv)
                .stream()
                .flatMap(div -> div.select("div.accordion-group").stream())
                .flatMap(this::parseAmendmentGroup)
                .toList();
    }

    private Stream<Amendment> parseAmendmentGroup(Element div) {
        Stream<Amendment> amendments = div.selectXpath("//tbody//tr").stream().map(this::mapToAmendment);

        Optional<String> commName = Optional.ofNullable(div.selectXpath("//a[@class='accordion-toggle']").first())
                .map(Element::text)
                .map(String::strip)
                .map(this::getCommitteeNameFromHeaderText);

        if (commName.isPresent()) {
            amendments = amendments.peek(am -> am.setCommitteeName(commName.get()));
        }

        return amendments;
    }

    private String getCommitteeNameFromHeaderText(String headerText) {
        return Pattern.compile("\\D+")
                .matcher(headerText)
                .results()
                .map(MatchResult::group)
                .map(String::strip)
                .findFirst()
                .orElse(null);
    }

    private Amendment mapToAmendment(Element row) {
        Elements cells = row.select("td");
        String idStr = cells.get(0).text().strip();
        String textLink = cells.get(0).selectFirst("a").attr("href");
        String authorStr = cells.get(1).text().strip();
        String dateStr = cells.get(2).text().strip();
        String outcomeStr = cells.get(4).text().strip();

        List<AmendmentOriginator> orig = Pattern.compile("(.+?) \\((.+?)\\)").matcher(authorStr)
                .results()
                .map(matchResult -> new AmendmentOriginator(
                        matchResult.group(1).replace("Senador ", ""),
                        matchResult.group(2)
                ))
                .toList();

        Amendment amendment = new Amendment();
        amendment.setAmendmentId(idStr);
        amendment.setTextSourceUrl(textLink);
        amendment.setDate(Utils.parseDate(dateStr));
        amendment.setOriginators(orig);

        OUTCOME_MAP.keySet()
                .stream()
                .filter(outcomeStr::contains)
                .findFirst()
                .ifPresent(outcome -> amendment.setOutcome(OUTCOME_MAP.get(outcome)));

        return amendment;
    }
}
