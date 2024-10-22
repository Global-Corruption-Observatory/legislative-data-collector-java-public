package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.common.PageSourceLoader;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * Collects final votes for bills (the votes have a separate page - the URL for this page is stored in the record in a previous step). These pages can have different formats, so the class has different methods to parse them.
 */
@Slf4j
@Service
public class FinalVotesCollector {

    private final EntityManager entityManager;
    private final PageSourceLoader pageSourceLoader;
    private final PrimaryKeyGeneratingRepository recordRepository;

    @Autowired
    public FinalVotesCollector(
            EntityManager entityManager,
            PrimaryKeyGeneratingRepository recordRepository,
            PageSourceLoader pageSourceLoader) {
        this.entityManager = entityManager;
        this.recordRepository = recordRepository;
        this.pageSourceLoader = pageSourceLoader;
    }

    @Transactional
    public void collectForAllBills() {
        //filter for unprocessed records
        String qlString = "SELECT r FROM LegislativeDataRecord r"
                + " WHERE r.brazilCountrySpecificVariables.votesPageUrl IS NOT NULL"
                + " AND r.finalVoteFor IS NULL AND r.finalVoteAgainst IS NULL AND r.finalVoteAbst IS NULL";

        entityManager.createQuery(qlString, LegislativeDataRecord.class).getResultStream()
                .forEach(bill -> {
                    processBill(bill);
                    recordRepository.mergeInNewTransaction(bill);

                    log.info(
                            "Final votes collected for bill: {} ({}, {}, {})",
                            bill.getRecordId(),
                            bill.getFinalVoteFor(),
                            bill.getFinalVoteAgainst(),
                            bill.getFinalVoteAbst()
                    );
                });
    }

    public void processBill(LegislativeDataRecord bill) {
        String pageUrl = bill.getBrazilCountrySpecificVariables().getVotesPageUrl();

        Optional<PageSource> page =
                pageSourceLoader.loadFromDbOrFetchWithHttpGet(Country.BRAZIL, PageType.VOTES.name(), pageUrl);

        if (pageUrl.contains("www2.camara.leg.br/atividade-legislativa") || pageUrl.contains("www2.camara.gov.br/atividade-legislativa")) {
            page.ifPresent(pg -> parseType1(pg, bill));
        } else if (pageUrl.contains("www.camara.leg.br/evento-legislativo")) {
            page.ifPresent(pg -> parsePreType2(pg, bill));
        } else if (pageUrl.contains("www25.senado.leg.br")) {
            page.ifPresent(pg -> parseType3(pg, bill));
        } else if (pageUrl.contains("www.camara.leg.br/presenca-comissoes")) {
            page.ifPresent(pg -> parseType2(pg, bill));
        } else {
            log.error("Unknown page type for final votes: {}", pageUrl);
        }
    }

    /**
     * Example: <a href="https://www2.camara.gov.br/atividade-legislativa/plenario/chamadaExterna.html?link=http://www.camara.gov.br/internet/votacao/mostraVotacao.asp?ideVotacao=12036">link</a>
     *
     * @param source
     * @param record
     */
    public void parseType1(PageSource source, LegislativeDataRecord record) {
        //url also could be: http://www2.camara.gov.br/atividade-legislativa/plenario/chamadaExterna.html?link=http://www.camara.gov.br/internet/votacao/mostraVotacao.asp?ideVotacao=2624
        Element containerPage = Jsoup.parse(source.getRawSource()).body();
        String votesPageSrc = containerPage.select("iframe").first().attr("src");
        Optional<PageSource> votesPage =
                pageSourceLoader.loadFromDbOrFetchWithHttpGet(Country.BRAZIL, PageType.VOTES.name(), votesPageSrc);

        if (votesPage.isPresent()) {
            Element parsedVotesPage = Jsoup.parse(votesPage.get().getRawSource()).body();
            Element votesDiv = parsedVotesPage.selectXpath("//div[@id='listaVotacao']").first();

            if (votesDiv != null) {
                Element yesVotes = votesDiv.selectXpath("//th[text()='Sim:']/following-sibling::td").first();
                if (yesVotes != null) {
                    record.setFinalVoteFor(Integer.parseInt(yesVotes.text().strip()));
                }

                Element noVotes = votesDiv.selectXpath("//th[text()='Não:']/following-sibling::td").first();
                if (noVotes != null) {
                    record.setFinalVoteAgainst(Integer.parseInt(noVotes.text().strip()));
                }

                Element absVotes = votesDiv.selectXpath("//th[text()='Abstenção:']/following-sibling::td").first();
                if (absVotes != null) {
                    record.setFinalVoteAbst(Integer.parseInt(absVotes.text().strip()));
                }
            }
        }
    }

    /**
     * Parses the page that contains the link to the actual (type 2) vote page. Example: <a href="https://www.camara.leg.br/evento-legislativo/67239">link</a>
     *
     * @param source The stored page source
     * @param record The current record, to update with the final vote data
     */
    public void parsePreType2(PageSource source, LegislativeDataRecord record) {
        //follow Votação link to get to the actual vote page
        Element parsed = Jsoup.parse(source.getRawSource()).body();
        Element votesLink = parsed.selectXpath("//ul[@class='links-adicionais']//a[text()='Votações']").first();

        if (votesLink != null) {
            Optional<PageSource> votesPage = pageSourceLoader.loadFromDbOrFetchWithHttpGet(
                    Country.BRAZIL, PageType.VOTES.name(), votesLink.attr("href")
            );

            votesPage.ifPresent(pageSource -> parseType2(pageSource, record));
        }
    }

    /**
     * Parses the actual vote page (type 2). Example: <a href="https://www.camara.leg.br/presenca-comissoes/votacao-portal?reuniao=67239&itemVotacao=11377">link</a>. This page has a dropdown to select the relevant session, and the votes are displayed after selecting the session. This method find the relevant item in the dropdown, then sends the page to the next method.
     *
     * @param votesPage The stored page source
     * @param record The current record, to update with the final vote data
     */
    private void parseType2(PageSource votesPage, LegislativeDataRecord record) {
        //select relevant bill from the dropdown (parse option value from select tag, based on the bill id)
        //then send that value as a query param, eg.: https://www.camara.leg.br/presenca-comissoes/votacao-portal?reuniao=67239&itemVotacao=11377
        Element parsedVotesPage = Jsoup.parse(votesPage.getRawSource()).body();
        String selector = "//select[@id='dropDownReunioes']//option[text()[contains(.,'%s')]]"
                .formatted(record.getBillId());

        Elements optionsForBill = parsedVotesPage.selectXpath(selector);

        if (!optionsForBill.isEmpty()) {
            List<String> finalVoteLabels = List.of(
                    "projeto de lei (simbólica)",
                    "projeto de lei (nominal)",
                    "apreciação do pl"
            );

            Optional<Element> finalVoteItem = optionsForBill.stream()
                    .filter(option ->
                            finalVoteLabels.stream().anyMatch(i -> option.text().toLowerCase().contains(i)))
                    .findFirst();

            if (finalVoteItem.isPresent()) {
                int itemNum = Integer.parseInt(finalVoteItem.get().attr("value"));
                String url =
                        "https://www.camara.leg.br/presenca-comissoes/votacao-portal?reuniao=67239&itemVotacao=%s".formatted(itemNum);

                Optional<PageSource> page =
                        pageSourceLoader.loadFromDbOrFetchWithHttpGet(Country.BRAZIL, "", url);

                page.ifPresent(pageSource -> parseType2Votes(pageSource, record));
            }
        }
    }

    /**
     * Collects the votes themselves from the page, after the relevant session (dropdown) is selected in the previous method.
     *
     * @param page  The stored page source
     * @param record The current record, to update with the final vote data
     */
    private void parseType2Votes(PageSource page, LegislativeDataRecord record) {
        //example: https://www.camara.leg.br/presenca-comissoes/votacao-portal?reuniao=67239
        Element parsed = Jsoup.parse(page.getRawSource()).body();
        Element votesList = parsed.selectFirst("ul.painelVotos");

        if (votesList != null) {
            //format 1
            Element yes = votesList.selectXpath("//li[@class='sim']//span[@class='qtd']").first();

            if (yes != null && StringUtils.isNotBlank(yes.text())) {
                record.setFinalVoteFor(Integer.parseInt(yes.text()));
            }

            Element no = votesList.selectXpath("//li[@class='nao']//span[@class='qtd']").first();

            if (no != null && StringUtils.isNotBlank(no.text())) {
                record.setFinalVoteAgainst(Integer.parseInt(no.text()));
            }

            Element abs = votesList.selectXpath("//li[@class='abstencao']//span[@class='qtd']").first();

            if (abs != null && StringUtils.isNotBlank(abs.text())) {
                record.setFinalVoteAbst(Integer.parseInt(abs.text()));
            }
        } else {
            //format 2
            Element votesDiv = parsed.selectFirst("div.containerVotos");

            if (votesDiv != null) {
                //count span class=voto nao for no, span class=voto sim for yes
                record.setFinalVoteFor(votesDiv.select("span.voto.sim").size());
                record.setFinalVoteAgainst(votesDiv.select("span.voto.nao").size());
            } else {
                log.error("Final votes could not be parsed from page: {}", page.getPageUrl());
            }
        }
    }

    /**
     * Example: <a href="https://www25.senado.leg.br/web/atividade/materias/-/materia/154451/votacoes#votacao_6818">link</a>
     * @param source
     * @param record
     */
    public void parseType3(PageSource source, LegislativeDataRecord record) {
        Element parsed = Jsoup.parse(source.getRawSource()).body();

        //check heading for the relevant bill id, or the anchor in the url
        String[] urlParts = source.getPageUrl().split("#");

        if (urlParts.length > 1) {
            String fragment = urlParts[1];
            Element relevantVoteLink = parsed.selectXpath("//a[@id='%s']".formatted(fragment)).first();
            Elements cells = relevantVoteLink.parent()
                    .parent()
                    .selectXpath("//table")
                    .first()
                    .selectXpath("//tbody//td");

            //get votes by index
            record.setFinalVoteFor(Integer.parseInt(cells.get(0).text()));
            record.setFinalVoteAgainst(Integer.parseInt(cells.get(1).text()));
            record.setFinalVoteAbst(Integer.parseInt(cells.get(2).text()));
        } else {
            log.error("Final votes page does not have the expected format: {}", source.getPageUrl());
        }
    }

}
