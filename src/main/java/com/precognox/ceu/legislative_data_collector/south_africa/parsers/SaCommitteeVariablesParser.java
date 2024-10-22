package com.precognox.ceu.legislative_data_collector.south_africa.parsers;

import com.precognox.ceu.legislative_data_collector.entities.Committee;
import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.sa.PublicHearing;
import com.precognox.ceu.legislative_data_collector.entities.sa.SouthAfricaCountrySpecificVariables;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.south_africa.SaPageCollector;
import com.precognox.ceu.legislative_data_collector.south_africa.SaPageType;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.south_africa.SaPageCollector.SOUTH_AFRICA_MAIN_URL;
import static com.precognox.ceu.legislative_data_collector.utils.DateUtils.parseSouthAfricaDate;

@Slf4j
@Service
@AllArgsConstructor
public class SaCommitteeVariablesParser {

    private final PrimaryKeyGeneratingRepository recordRepository;
    private final PageSourceRepository pageSourceRepository;
    private final SaPageCollector saPageCollector;

    @Transactional
    public void parseAllPages() {
        log.info("Querying unprocessed pages...");

        recordRepository.streamUnprocessedCommittees(Country.SOUTH_AFRICA)
                .forEach(record -> {
                    log.info("Processing committee data for bill page: " + record.getBillPageUrl());
                    recordRepository.mergeInNewTransaction(parsePage(record));
                });
    }

    public LegislativeDataRecord parsePage(LegislativeDataRecord record) {
        PageSource source = pageSourceRepository.getByPageUrl(record.getBillPageUrl());
        Element page = Jsoup.parse(source.getRawSource()).body();

        SouthAfricaCountrySpecificVariables southAfricaCountrySpecificVariables =
                new SouthAfricaCountrySpecificVariables();

        List<Committee> committees = parseCommitteeData(page);
        record.setCommittees(committees);
        record.setCommitteeCount(committees.size());
        if (!committees.isEmpty()) {
            record.setCommitteeHearingCount(getCommitteeHearingCount(record.getCommittees()));
            southAfricaCountrySpecificVariables.setPublicHearingCount(getPublicHearings(committees));
        }

        southAfricaCountrySpecificVariables.setPublicHearings(parsePublicHearings(page));

        record.setSouthAfricaCountrySpecificVariables(southAfricaCountrySpecificVariables);
        return record;
    }

    public List<Committee> parseCommitteeData(Element page) {
        List<Committee> committees = new ArrayList<>();

        Optional<Elements> billLocations = getBillLocations(page);

        billLocations.stream()
                .map(location -> location.select("img[src*=committee-discussion.png]"))
                .flatMap(cd -> cd.stream()
                        .map(committeeData -> Optional.ofNullable(committeeData.parent())
                                .map(Element::nextElementSibling))
                        .flatMap(Optional::stream)
                        .map(this::processCommitteeData))
                .forEach(committees::add);

        return committees;
    }

    public Optional<Elements> getBillLocations(Element page) {
        return Optional.ofNullable(page.select("div.bill-events").first())
                .map(locations -> locations.select(".NA, .NCOP, .Joint"));
    }

    private Committee processCommitteeData(Element committeeData) {
        String committeeName = Optional.ofNullable(committeeData.getElementsByTag("h5").first())
                .map(Element::text)
                .orElse(null);

        LocalDate committeeDate = Optional.ofNullable(
                        committeeData.getElementsByClass("col-xs-4 col-md-2 text-muted")
                                .first())
                .map(Element::text)
                .map(DateUtils::parseSouthAfricaDate)
                .orElse(null);

        List<Element> committeeHearings = getCommitteeHearings(committeeData);
        int committeeHearingCount = 0;
        int numberOfPublicHearingsCommittee = 0;

        Optional<Boolean> publicHearingTextElement;

        for (Element hearingCount : committeeHearings) {
            publicHearingTextElement = Optional.ofNullable(hearingCount.getElementsByTag("span").first())
                    .map(Element::text)
                    .map(s -> s.contains("public participation"));

            if (publicHearingTextElement.isEmpty()) {
                committeeHearingCount++;
            } else {
                numberOfPublicHearingsCommittee++;
                String hearingUrl = SOUTH_AFRICA_MAIN_URL + hearingCount.getElementsByTag("a")
                        .first().attr("href");

                saPageCollector.downloadPage(hearingUrl, SaPageType.PUBLIC_HEARING);
            }
        }

        return new Committee(committeeName, null, committeeDate, committeeHearingCount,
                             numberOfPublicHearingsCommittee);
    }

    private List<Element> getCommitteeHearings(Element committeeData) {
        return new ArrayList<>(committeeData.getElementsByClass("row"));
    }

    public Integer getCommitteeHearingCount(List<Committee> committees) {
        int committeeHearingCount = 0;

        for (Committee committee : committees) {
            committeeHearingCount += committee.getCommitteeHearingCount();
        }

        return committeeHearingCount;
    }

    public Integer getPublicHearings(List<Committee> committees) {
        int publicHearings = 0;

        for (Committee committee : committees) {
            publicHearings += committee.getNumberOfPublicHearingsCommittee();
        }

        return publicHearings;
    }

    public List<PublicHearing> parsePublicHearings(Element page) {
        return getBillLocations(page)
                .map(billLocations -> billLocations.stream()
                        .map(billLocation -> Optional.ofNullable(
                                        billLocation.select("img[src*=committee-discussion.png]").first())
                                .map(Element::parent)
                                .map(Element::nextElementSibling)
                                .map(this::getCommitteeHearings)
                                .orElseGet(ArrayList::new)
                                .stream()
                                .filter(hearing -> hearing.text().contains("public participation"))
                                .map(this::getPublicHearingData).toList())
                        .flatMap(Collection::stream)
                        .toList())
                .orElseGet(Collections::emptyList);
    }

    private PublicHearing getPublicHearingData(Element hearing) {

        String hearingTitle = hearing.getElementsByTag("a").text();
        LocalDate hearingDate = parseSouthAfricaDate(
                hearing.getElementsByClass("col-xs-4 col-md-2 text-muted").text());

        String hearingUrl = SOUTH_AFRICA_MAIN_URL + hearing.getElementsByTag("a").attr("href");
        Integer hearingSubmissionCount = parseSubmissionCount(hearingUrl);

        return new PublicHearing(hearingTitle, hearingDate, hearingSubmissionCount);
    }

    private Integer parseSubmissionCount(String hearingUrl) {
        Element page = getSubmissionCountPage(hearingUrl);
        return getSubmissionCount(page);
    }

    private static Integer getSubmissionCount(Element page) {
        return Optional.ofNullable(page.select("h4:contains(Documents)").first())
                .map(Element::parent)
                .map(Element::nextElementSibling)
                .map(li -> li.getElementsByTag("li").size())
                .orElse(null);
    }

    private Document getSubmissionCountPage(String hearingUrl) {
        return Jsoup.parse(pageSourceRepository.getByPageUrl(hearingUrl).getRawSource());
    }
}