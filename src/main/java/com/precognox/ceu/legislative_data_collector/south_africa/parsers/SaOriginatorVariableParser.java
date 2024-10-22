package com.precognox.ceu.legislative_data_collector.south_africa.parsers;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.repositories.PrimaryKeyGeneratingRepository;
import com.precognox.ceu.legislative_data_collector.south_africa.SaPageType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@AllArgsConstructor
public class SaOriginatorVariableParser {
    private final PageSourceRepository pageSourceRepository;
    private final PrimaryKeyGeneratingRepository recordRepository;
    private static final Pattern ORIGINATOR_NAME_PATTERN = Pattern.compile(
            "\\(The English text is the offıcial text of the Bill\\)\\s+\\((.*?)\\)");

    @Transactional
    public void parseAllPages() {
        log.info("Querying unprocessed pages...");

        recordRepository.streamUnprocessedOriginators(Country.SOUTH_AFRICA)
                .forEach(record -> {
                    log.info("Processing originator data for bill page: " + record.getBillPageUrl());
                    recordRepository.mergeInNewTransaction(parsePage(record));
                });
    }

    public LegislativeDataRecord parsePage(LegislativeDataRecord record) {
        PageSource source = pageSourceRepository.getByPageUrl(record.getBillPageUrl());

        Optional<Originator> originator = parseOriginatorData(source);
        if (originator.isPresent()) {
            record.setOriginators(List.of(originator.get()));
            record.setOriginType(parseOriginType(originator.get()));
        }
        return record;
    }

    public Optional<Originator> parseOriginatorData(PageSource source) {
        Element page = Jsoup.parse(source.getRawSource()).body();
        Originator originator = new Originator();

        Optional<Element> originatorNameElement = Optional.ofNullable(page.select("div.NA").first())
                .flatMap(divNA -> {
                    Elements introducedDiv = divNA.select("div:contains(introduced)");
                    if (!introducedDiv.isEmpty()) {
                        return Optional.ofNullable(introducedDiv.last());
                    } else {
                        return Optional.ofNullable(page.select("div.NCOP").first());
                    }
                })
                .flatMap(naElement -> getAncestorByContainingTag(naElement, "h5"));


//        Parse affiliation
        if (originatorNameElement.isPresent()) {
            String originatorName = originatorNameElement.get().text();
            if (originatorName.equalsIgnoreCase("national assembly")) {
                Optional<String> originatorNameBillText = getOriginatorNameFromBillText(source.getPageUrl());
                if (originatorNameBillText.isPresent()) {
                    originatorName = originatorNameBillText.get();
                }
            }
            originator.setName(originatorName);

            if (originatorName.toLowerCase().contains("committee") || originatorName.toLowerCase()
                    .contains("minister") || originatorName.equalsIgnoreCase("state security")) {
                originator.setAffiliation(originatorName);
            } else {
                parseMpOriginatorAffiliation(originatorName).ifPresent(originator::setAffiliation);
            }
        }
        return originator.getName() == null && originator.getAffiliation() == null ?
                Optional.empty() : Optional.of(originator);
    }

    private Optional<String> getOriginatorNameFromBillText(String billTextUrl) {
        Optional<String> billText = recordRepository.getBillTextByBillPageUrl(billTextUrl);

        return billText.flatMap(s -> ORIGINATOR_NAME_PATTERN
                .matcher(s.replace("\n", " "))
                .results()
                .map(m -> m.group(1))
                .findFirst());
    }

    public Optional<Element> getAncestorByContainingTag(Element element, String tag) {
        while (element != null) {
            if (!element.select(tag).isEmpty()) {
                return Optional.ofNullable(element.getElementsByTag(tag).first());
            }

            element = element.parent();
        }
        return Optional.empty();
    }

    private Optional<String> parseMpOriginatorAffiliation(String originatorName) {
        Optional<String> pageSource = getMpOriginator(originatorName);

        return getMpOriginatorAffiliation(pageSource);
    }

    private Optional<String> getMpOriginator(String originatorName) {
        return pageSourceRepository.findOriginatorUrlWithFuzzyMatching(
                SaPageType.ORIGINATOR.name(), originatorName);
    }

    private Optional<String> getMpOriginatorAffiliation(Optional<String> pageSource) {
        if (pageSource.isPresent()) {
            Element page = Jsoup.parse(pageSource.get()).body();
            return Optional.ofNullable(page.select("h3:contains(Political party:)").first())
                    .map(Element::nextElementSibling)
                    .map(Element::text);
        }
        return Optional.empty();
    }

    public OriginType parseOriginType(Originator originator) {
        String originatorName = originator.getName();

        if (originatorName.toLowerCase().contains("minister") || originatorName.toLowerCase()
                .contains("deputy minister") || originatorName.equalsIgnoreCase("state security")) {
            return OriginType.GOVERNMENT;
        } else if (originatorName.toLowerCase().contains("committee")) {
            return OriginType.COMMITTEE;
        } else if (originatorName.equalsIgnoreCase("national assembly")) {
            return null;
        } else if (!originatorName.equals(originator.getAffiliation())) {
            return OriginType.INDIVIDUAL_MP;
        }
        return null;
    }
}
