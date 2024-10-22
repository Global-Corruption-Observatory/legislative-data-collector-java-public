package com.precognox.ceu.legislative_data_collector.poland.parsers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precognox.ceu.legislative_data_collector.entities.Amendment;
import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.poland.json.CommitteeJson;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * This class is responsible for parsing committees in one legislative process. There are two HTML-formats for every leg.
 * process and there is an HTML-structure change between two formats: (<a> containing the name has appeared inside <font>)
 * "https://orka.sejm.gov.pl/SQL.nsf/poskomprocall?OpenAgent&3&2573" vs. "https://www.sejm.gov.pl/SQL2.nsf/poskomprocall?OpenAgent&3&2573".
 * It seems all the data needed is available from term3 to term9 in the second url-format as well, in first url-format
 * there is no data from term7 to term9. We would not use them for parsing at all. Committee names and roles come from
 * COMMITTEE_JSON-s.
 */
@Slf4j
@Service
public class PolandCommitteesTableParser {

    private static final String COMM_JSON_URL_TEMPLATE = "https://api.sejm.gov.pl/sejm/%s/committees";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PageSourceRepository pageSourceRepository;


    @Autowired
    public PolandCommitteesTableParser(PageSourceRepository pageSourceRepository) {
        this.pageSourceRepository = pageSourceRepository;
    }

    public void parseCommittees(PageSource source, LegislativeDataRecord dataRecord) {
        log.info("Processing committee table for {} started", dataRecord.getRecordId());

        Document committeePage = Jsoup.parse(source.getRawSource());
        String termInPolish = getTermInPolish(committeePage);

        Optional.ofNullable(committeePage.body().selectXpath("//body//center//table[2]").first())
                .ifPresent(commTable -> parseCommitteeTable(dataRecord, commTable, termInPolish));
        log.info("Processing committee table for {} finished", dataRecord.getRecordId());
    }

    private void parseCommitteeTable(LegislativeDataRecord dataRecord, Element commTable, String termInPolish) {
        Element commTableBody = commTable.getElementsByTag("tbody").first();
        List<Committee> committees = new ArrayList<>();
        List<Amendment> amendments = new ArrayList<>();

        if (commTableBody != null) {
            List<Element> committeeAndSubcommitteeRows = commTableBody.getElementsByTag("tr").stream()
                    .skip(1) // skipping table header
                    .filter(tr -> StringUtils.isNotBlank(tr.getElementsByTag("td").first().text().trim()))
                    .toList();
            dataRecord.setCommitteeHearingCount(committeeAndSubcommitteeRows.size());

            for (Element commRow : committeeAndSubcommitteeRows) {
                Element commMeetingCell = commRow.getElementsByTag("td").get(1);
                if (StringUtils.isNotBlank(commMeetingCell.text().trim())) {
                    String[] committeeNames = getCommitteeNames(commMeetingCell);
                    setCommittees(committeeNames, commRow, termInPolish, committees);
                    Element nobrParent = commRow.getElementsByTag("nobr").first().parent();
                    if (Objects.requireNonNull(nobrParent).hasAttr("href")) {
                        String amendmentUrl = nobrParent.attr("href");
                        AmendmentCommitteeNameParser.parseAmendmentCommitteeName(amendmentUrl, amendments,
                                dataRecord);
                    }
                    dataRecord.setCommitteeCount(committeeNames.length);
                }
            }
            if (!committees.isEmpty()) {
                // data record's "committeeDate" field is the date of the first meeting
                dataRecord.setCommitteeDate(committees.get(0).getDate());
            }
            dataRecord.setAmendments(amendments);
            dataRecord.setAmendmentCount(amendments.size());
            dataRecord.setCommittees(committees);
        }
    }

    @NotNull
    private String getTermInPolish(Document committeePage) {
        Element termAndTitleTable = committeePage.body().selectXpath("//body//center//table[1]").first();
        Element termTableBody = termAndTitleTable.getElementsByTag("tbody").first();
        Element termTableRow = termTableBody.getElementsByTag("tr").first();
        return termTableRow.getElementsByTag("td").first().text();
    }

    @NotNull
    private String[] getCommitteeNames(Element commMeetingCell) {
        Element span = commMeetingCell.getElementsByTag("span").first();
        Element committeeNameElement = span.getElementsByTag("font").first();
        String bulkCommitteeNames = committeeNameElement.getElementsByTag("a").first().text();
        return bulkCommitteeNames.split(" ");
    }

    private void setCommittees(String[] committeeNames, Element commRow, String termInPolish, List<Committee> committees) {
        for (String name : committeeNames) {
            Committee committee = new Committee();
            committee.setDate(setCommitteeDate(commRow));
            committee.setName(name);
            String committeeRole = getCommitteeNamesAndRoles(termInPolish).get(name);
            committee.setRole(committeeRole);
            committees.add(committee);
        }
    }

    private LocalDate setCommitteeDate(Element commRow) {
        return LocalDate.parse(commRow.getElementsByTag("nobr").first().text(),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"));
    }

    // in different terms different committee role belong to the same committee name (like ESK (term9 vs. term8):
    // "Komisja do Spraw Energii, Klimatu i Aktywów Państwowych" vs. "Komisja do Spraw Energii i Skarbu Państwa")
    private Map<String, String> getCommitteeNamesAndRoles(String termInPolish) {
        Optional<PageSource> optCommitteeSource =
                pageSourceRepository.findByPageUrl(String.format(COMM_JSON_URL_TEMPLATE, getTerm(termInPolish)));
        if (optCommitteeSource.isPresent()) {
            Map<String, String> commNamesAndRoles = new HashMap<>();
            CommitteeJson[] committeeJsonArray;
            try {
                committeeJsonArray = objectMapper.readValue(optCommitteeSource.get().getRawSource(), CommitteeJson[].class);
                for (CommitteeJson json : committeeJsonArray) {
                    commNamesAndRoles.put(json.getCommitteeName(), json.getCommitteeRole());
                }
            } catch (JsonProcessingException e) {
                log.error("JSON-processing error at URL: {}", optCommitteeSource.get().getPageUrl());
            }
            return commNamesAndRoles;
        }
        return Collections.emptyMap();
    }

    private String getTerm(String termInPolish) {
        return switch (termInPolish) {
            case "III kadencja" -> "term3";
            case "IV kadencja" -> "term4";
            case "V kadencja" -> "term5";
            case "VI kadencja" -> "term6";
            case "VII kadencja" -> "term7";
            case "VIII kadencja" -> "term8";
            case "IX kadencja" -> "term9";
            default -> throw new IllegalStateException("Unexpected value: " + termInPolish);
        };
    }

    // From term3 to term6 urls are like: https://orka.sejm.gov.pl/Biuletyn.nsf/wgskrnr/INF-9, from term7 to term url
    // format is like: https://www.sejm.gov.pl/Sejm9.nsf/biuletyn.xsp?skrnr=INF-193. The two formats have totally
    // different HTML-structures.
    private static class AmendmentCommitteeNameParser {

        private static void parseAmendmentCommitteeName(String url, List<Amendment> amendments,
                                                        LegislativeDataRecord dataRecord
        ) {
            HttpResponse<String> httpResponse = Unirest.get(url).asString();

            if (httpResponse.isSuccess()) {
                Document page = Jsoup.parse(httpResponse.getBody());
                String amendmentCommitteeName = "";

                if (url.startsWith("https://orka.sejm")) {
                    Element nameTag = page.body().selectXpath("//td[normalize-space()='Komisja:']").first();
                    if (nameTag != null) {
                        Element committeeNameCell = nameTag.nextElementSibling();
                        amendmentCommitteeName = committeeNameCell.text();
                    }
                } else { //"https://www.sejm.gov.pl/..."
                    Element div = page.body().getElementById("view:_id1:_id2:facetMain");
                    if (div != null) {
                        Element divChild = div.child(0);
                        if (!divChild.getElementsByTag("ul").isEmpty()) {
                            Element committeeNameCell = divChild.child(2);
                            List<String> names = committeeNameCell.children().eachText();
                            StringBuilder sb = new StringBuilder();
                            for (String name : names) {
                                sb.append(name);
                                sb.append(",");
                            }
                            amendmentCommitteeName = sb.substring(0, sb.length() - 1); // trim last ","
                        } else {
                            amendmentCommitteeName = "Brak tekstu w postaci elektronicznej";
                        }
                    }
                }
                Amendment amendment = new Amendment();
                amendment.setCommitteeName(amendmentCommitteeName);
                amendment.setDataRecord(dataRecord);
                amendments.add(amendment);
            } else {
                log.error("{} error response for page: {}", httpResponse.getStatus(), url);
            }
        }
    }
}
