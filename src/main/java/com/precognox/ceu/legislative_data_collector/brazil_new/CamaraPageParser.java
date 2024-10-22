package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses the bill pages from the Camara website, and extends existing records with new variables. Uses the already downloaded pages from the page_source table.
 *
 * Parses the following variables:
 * - originators
 * - originType
 * - procedureTypeStandard
 * - procedureTypeNational
 * - billStatus
 * - modifiedLaws
 * - originalLaw
 * - committees
 * - billTextUrl
 * - votesPageUrl
 */
@Slf4j
@Service
public class CamaraPageParser {

    private final PageSourceLoader pageSourceLoader;
    private final PrimaryKeyGeneratingRepository recordRepository;

    @Autowired
    public CamaraPageParser(PrimaryKeyGeneratingRepository recordRepository, PageSourceLoader pageSourceLoader) {
        this.recordRepository = recordRepository;
        this.pageSourceLoader = pageSourceLoader;
    }

    @Transactional
    public void processAll() {
        recordRepository.streamAllWithCamaraPageUrl()
                .peek(record -> log.info("Processing record: {}", record.getBillPageUrl()))
                .forEach(this::processCamaraPage);
    }

    private void processCamaraPage(LegislativeDataRecord record) {
        Optional<PageSource> pageSource = pageSourceLoader.loadFromDbOrFetchWithHttpGet(
                Country.BRAZIL,
                PageType.CAMARA_BILL_DETAILS.name(),
                record.getBrazilCountrySpecificVariables().getCamaraPageUrl()
        );

        if (pageSource.isPresent()) {
            parseCamaraPage(record, pageSource.get());
            recordRepository.mergeInNewTransaction(record);
        } else {
            log.error("Camara page not found for record: {}", record.getId());
        }
    }

    public void parseCamaraPage(LegislativeDataRecord record, PageSource source) {
        Document parsed = Jsoup.parse(source.getRawSource());

        if (record.getOriginators().isEmpty()) {
            parseOriginator(parsed).ifPresent(record.getOriginators()::add);
            record.setOriginType(Utils.getOriginType(record.getOriginators()));
        }

        parseProcedureType(record, parsed);
        parseBillStatus(parsed).ifPresent(record::setBillStatus);
        parseModifiedLaws(record, parsed);
        parseCommittees(record, parsed);
        parseBillText(parsed).ifPresent(record::setBillTextUrl);
        parseCommitteeHearingCount(parsed).ifPresent(record::setCommitteeHearingCount);

        parseFinalVotesLink(parsed, source.getPageUrl())
                .ifPresent(link -> record.getBrazilCountrySpecificVariables().setVotesPageUrl(link));

        parseAmendmentCountsAndLinks(parsed, record);
    }

    private Optional<Originator> parseOriginator(Document parsed) {
        String authorXpath =
                "//strong[text()='Autor']/following-sibling::span[1] | //strong[text()='Autor']/following-sibling::a[1]";

        Elements authorElements = parsed.body().selectXpath(authorXpath);

        return authorElements.stream()
                .map(Element::text)
                .map(String::strip)
                .findFirst()
                .map(this::parseOriginatorString);
    }

    private Originator parseOriginatorString(String originatorString) {
        /*
        examples for originatorString:
         Major Olimpio - PDT/SP, Poder Executivo
         Senado Federal - Alessandro Vieira - PSDB/SE
         Senado Federal - Comissão da Reforma Política do Senado Federal
         Senado Federal (comissão Mista - Art. 142 E 143 do Regimento Comum)
        */
        String clean = originatorString.replace("Senado Federal - ", "").strip();

        String[] parts = clean.split(" - ");

        if (parts.length == 1) {
            return new Originator(parts[0]);
        }

        return new Originator(parts[0], parts[1]);
    }

    private void parseProcedureType(LegislativeDataRecord record, Document parsed) {
        //parse procedure type
        parsed.body()
                .selectXpath("//strong[text()='Regime de Tramitação']/..")
                .stream()
                .map(Element::text)
                .map(String::strip)
                .findFirst()
                .ifPresent(procedureTypeText -> {
                    //remove header...
                    String cleanProcType =
                            procedureTypeText.replace("Regime de Tramitação", "").strip();
                    record.setProcedureTypeNational(cleanProcType);

                    if (Stream.of("Prioridade", "Urgência").anyMatch(cleanProcType::contains)) {
                        record.setProcedureTypeStandard(LegislativeDataRecord.ProcedureType.EXCEPTIONAL);
                    } else {
                        record.setProcedureTypeStandard(LegislativeDataRecord.ProcedureType.REGULAR);
                    }
                });
    }

    private Optional<LegislativeDataRecord.BillStatus> parseBillStatus(Document parsedPage) {
        String statusSelector =
                "//strong[text()[contains(.,'Situação')]]/following-sibling::*[self::span or self::a][1]";

        return parsedPage.body()
                .selectXpath(statusSelector)
                .stream()
                .map(Element::text)
                .findFirst()
                .map(this::parseStatus);
    }

    private void parseModifiedLaws(LegislativeDataRecord record, Document parsed) {
        StringBuilder summaryText = new StringBuilder();

        //collect text from the Summary and Additional information sections
        String summaryXpath = "//strong[text()='Ementa']/following-sibling::span[1]"
                + " | //strong[text()='Dados Complementares:']/following-sibling::span[1]"
                + " | //strong[text()='Nova Ementa da Redação']/following-sibling::span[1]";

        parsed.body()
                .selectXpath(summaryXpath)
                .stream()
                .map(Element::text)
                .forEach(summaryText::append);

        if (!summaryText.isEmpty()) {
            record.setModifiedLaws(parseModifiedLaws(summaryText.toString()));
            record.setModifiedLawsCount(record.getModifiedLaws().size());

            if (record.getModifiedLawsCount() > 0) {
                record.setOriginalLaw(false);
            } else {
                List<String> modificationPhrases =
                        List.of("altera", "modifica", "dá nova redação", "acrescenta");

                boolean originalLaw = modificationPhrases
                        .stream()
                        .noneMatch(s -> summaryText.toString().toLowerCase().contains(s));

                record.setOriginalLaw(originalLaw);
            }
        } else {
            record.setOriginalLaw(true);
        }
    }

    private void parseCommittees(LegislativeDataRecord record, Document parsed) {
        record.setCommittees(parseCommittees(parsed));

        //add more committees - keeps the earliest committee by committee date if there are duplicates
        StreamEx.of(getTramitacaoCommittees(parsed))
                .distinct(Committee::getName)
                .forEach(record.getCommittees()::add);

        record.setCommitteeCount(record.getCommittees().size());
    }

    private List<Committee> parseCommittees(Document parsedPage) {
        Elements committeesTable = parsedPage.body().selectXpath("//div[@id='pareceresValidos']/table");

        if (!committeesTable.isEmpty()) {
            Elements cells = committeesTable.get(0).selectXpath("//td");

            List<Committee> comms = cells.stream()
                    .map(Element::text)
                    .filter(text -> text.contains("Comissão de"))
                    .map(String::strip)
                    .distinct()
                    .map(committeeName -> new Committee(committeeName, null))
                    .toList();

            return new ArrayList<>(comms); //wrap immutable list in new list to allow modification later
        }

        return new ArrayList<>();
    }

    @NotNull
    private Stream<Committee> getTramitacaoCommittees(Document parsed) {
        return parsed.body()
                .selectXpath("//div[@id='tramitacoes']/table/tbody/tr")
                .stream()
                .map(this::processTableRow)
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    /**
     * If the row contains a mention of a committee, return a new Committee object. Otherwise, return an empty Optional.
     *
     * @param row One table row.
     * @return Committee object or empty Optional.
     */
    private Optional<Committee> processTableRow(Element row) {
        String date = row.select("td").get(0).text().strip();

        return Optional.ofNullable(row.selectXpath("//td[2]").first())
                .filter(this::isCommitteeCell)
                .map(td -> td.selectXpath("//p//strong"))
                .map(Elements::text)
                .map(String::strip)
                .map(commName -> new Committee(commName, null, Utils.parseDate(date)));
    }

    private boolean isCommitteeCell(Element cell) {
        //check for Comissão text in header first
        Element header = cell.selectXpath("//p//strong").first();

        if (header != null && header.text().contains("Comissão")) {
            return true;
        }

        //check for Recebimento text under the header
        Element descriptionText = cell.selectXpath("//ul").first();

        return descriptionText != null && descriptionText.text().contains("Recebimento");
    }

    @NotNull
    private Optional<String> parseBillText(Document parsed) {
        return parsed.body()
                .selectXpath("//a[text()[contains(.,'Inteiro teor')]]")
                .stream()
                .findFirst()
                .map(element -> element.attr("href"))
                .map(url -> url.replace(" ", "%20"));
    }

    private LegislativeDataRecord.BillStatus parseStatus(String statusText) {
        String statusLower = statusText.toLowerCase();

        return Constants.BILL_STATUS_MAPPING.keySet()
                .stream()
                .filter(statusLower::contains)
                .findFirst()
                .map(Constants.BILL_STATUS_MAPPING::get)
                .orElse(null);
    }

    private Set<String> parseModifiedLaws(String summaryText) {
        //example for text: Altera o art. 149 do Decreto-Lei nº 2.848, de 7 de dezembro de 1940 (Código Penal), para estabelecer penas ao crime nele tipificado e indicar as hipóteses em que se configura condição análoga à de escravo.
        //transform 'Decreto-Lei nº 2.848, de 7 de dezembro de 1940' to LEI-2848-1940-12-07
        //other example: Medida Provisória nº 2.229-43, de 6 de setembro de 2001
        String clean = summaryText.replace(".", ""); //remove period from law number to simplify regex
        Pattern pattern = Pattern.compile("Lei nº (\\d+), de (\\d+) de (\\w+) de (\\d+)");
        Matcher matcher = pattern.matcher(clean);

        return matcher.results().map(this::transformLawId).collect(Collectors.toSet());
    }

    private @NotNull String transformLawId(MatchResult match) {
        String lawNumber = match.group(1);
        String day = match.group(2);
        String month = match.group(3);
        String year = match.group(4);

        return String.format(
                "LEI-%s-%s-%02d-%02d",
                lawNumber,
                year,
                Constants.MONTH_TRANSLATIONS.get(month),
                Integer.parseInt(day)
        );
    }

    private Optional<Integer> parseCommitteeHearingCount(Document page) {
        //example page: https://www.camara.leg.br/proposicoesWeb/fichadetramitacao?idProposicao=348783, find "mais sessões e reuniões" link, then get total count from page header
        Element hearingsLink = page.body().selectXpath("//a[text()[contains(.,'mais sessões e reuniões')]]").first();

        if (hearingsLink != null) {
            String hearingsPageUrl = toAbsolute(hearingsLink.attr("href"));

            Optional<PageSource> hearingsPage = pageSourceLoader.loadFromDbOrFetchWithHttpGet(
                    Country.BRAZIL,
                    PageType.CAMARA_HEARINGS.name(),
                    hearingsPageUrl
            );

            if (hearingsPage.isEmpty()) {
                log.warn("Committee hearings page not found: {}", hearingsPageUrl);
            } else {
                Document hearingsDoc = Jsoup.parse(hearingsPage.get().getRawSource());

                String headerText = hearingsDoc.body()
                        .select("p.referenciaPaginacao")
                        .stream()
                        .map(Element::text)
                        .map(String::strip)
                        .findFirst()
                        .orElse("");

                //has one session
                if (headerText.contains("Sessão e Reunião de 1 a 1 de 1 encontrado")) {
                    return Optional.of(1);
                }

                //has multiple sessions
                Pattern pattern = Pattern.compile("Sessões e Reuniões de \\d+ a \\d+ de (\\d+) encontrados");

                return pattern.matcher(headerText)
                        .results()
                        .map(matchResult -> matchResult.group(1))
                        .map(Integer::parseInt)
                        .findFirst();
            }
        }

        return Optional.empty();
    }

    private String toAbsolute(String hearingsPageLink) {
        if (!hearingsPageLink.startsWith("http")) {
            return "https://www.camara.leg.br/proposicoesWeb/" + hearingsPageLink;
        }

        return hearingsPageLink;
    }

    private Optional<String> parseFinalVotesLink(Document page, String pageUrl) {
        //example: https://www.camara.leg.br/proposicoesWeb/fichadetramitacao?idProposicao=2323391
        //other example (no votacao link - must click through to anoter page): https://www.camara.leg.br/proposicoesWeb/fichadetramitacao?idProposicao=557678 - final votes link should be stored the same way?

        //first option - last Votação link
        //or use "(//div[@id='tramitacoes']//a[text()='Votação'])[last()]" if it doesn't work
        Elements voteLinks =
                page.body().selectXpath("//div[@id='tramitacoes']//a[text()[contains(.,'Votação')]][last()]");

        //should be just one result
        if (voteLinks.size() == 1) {
            return Optional.of(voteLinks.get(0).attr("href"));
        } else {
            //second option - last Sessão Deliberativa link
            voteLinks = page.body()
                    .selectXpath("//div[@id='tramitacoes']//a[text()[contains(.,'Sessão Deliberativa')]][last()]");

            if (!voteLinks.isEmpty()) {
                //take the last link
                return Optional.of(voteLinks.last().attr("href"));
            }
        }

        return Optional.empty();
    }

    private void parseAmendmentCountsAndLinks(Document page, LegislativeDataRecord record) {
        //parse count and links to amendment pages
        String selector = "//div[@id='documentosEanexos']//a[text()[contains(.,'Emendas ao Projeto')]]"
                + " | //div[@id='documentosEanexos']//a[text()[contains(.,'Emendas ao Substitutivo')]]";

        List<Element> amendmentLinks = page.body()
                .selectXpath(selector)
                .stream()
                .toList();

        List<String> urls = amendmentLinks.stream()
                .map(element -> element.attr("href"))
                .toList();

        record.getBrazilCountrySpecificVariables().setAmendmentPageLinks(urls);

        Pattern numberPattern = Pattern.compile("\\( (\\d+) \\)");

        int amendmentCount = amendmentLinks
                .stream()
                .map(Element::parent)
                .filter(Objects::nonNull)
                .map(Element::text)
                .flatMap(text -> numberPattern.matcher(text).results())
                .map(matchResult -> matchResult.group(1))
                .mapToInt(Integer::parseInt)
                .sum();

        record.setAmendmentCount(amendmentCount);
    }

}
