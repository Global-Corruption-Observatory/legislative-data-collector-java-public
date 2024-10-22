package com.precognox.ceu.legislative_data_collector.colombia.recordbuilding;

import com.precognox.ceu.legislative_data_collector.colombia.constants.PageType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.entities.PageSource;
import com.precognox.ceu.legislative_data_collector.entities.colombia.ColombiaOriginatorVariables;
import com.precognox.ceu.legislative_data_collector.entities.colombia.OriginTypeColombia;
import com.precognox.ceu.legislative_data_collector.repositories.PageSourceRepository;
import com.precognox.ceu.legislative_data_collector.utils.CachedSources;
import com.precognox.ceu.legislative_data_collector.utils.ReadDatabaseService;
import com.precognox.ceu.legislative_data_collector.utils.selenium.SeleniumUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import javax.persistence.NonUniqueResultException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.precognox.ceu.legislative_data_collector.colombia.constants.ColombianTranslations.GENDER_TRANSLATIONS;

/**
 * This class collects the originator information from the bill page and handles the originator page collection.
 */
@Slf4j
public class OriginatorInformationCollector {
    private static final String BASE_PAGEURL = "https://congresovisible.uniandes.edu.co";
    private static final CachedSources<PageSource> NOT_YET_SAVED_PAGES = new CachedSources<>();

    private final ReadDatabaseService readService;
    private final Elements originatorsContainers;
    private final OriginTypeColombia type;

    public OriginatorInformationCollector(ReadDatabaseService readService, BillPageParser pageParser,
                                          OriginTypeColombia type) {
        this.readService = readService;
        this.type = type;
        this.originatorsContainers = pageParser.getOriginatorContainers();
    }

    public static void savePagesToDatabase(PageSourceRepository repository) {
        NOT_YET_SAVED_PAGES.saveToDatabase(repository);
    }

    public List<Originator> getOriginatorsFromBillPage() {

        return originatorsContainers.stream()
                .filter(originatorContainer -> Objects.nonNull(originatorContainer.selectFirst(".name p")))
                .map(this::getOriginatorFromElement)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<Originator> getOriginatorFromElement(Element originatorElement) {
        String[] originatorName = originatorElement.selectFirst(".name p").text().split("([.:])");

        if (originatorName.length == 1) {
            if (type != null && type.equals(OriginTypeColombia.PARLIAMENTARY)) {
                Element link = originatorElement.parent();
                if (Objects.nonNull(link) && link.tagName().equals("a")) {
                    return Optional.of(getOriginatorWithWebpage(link, originatorName[0]));
                }
                return Optional.empty();
            }
            // Does not have dedicated originator page, name is the only collectable information
            return Optional.of(new Originator(originatorName[0]));
        } else {
            return Optional.of(createOriginatorFromNameText(originatorName));
        }
    }

    private Originator getOriginatorWithWebpage(Element link, String name) {
        String url = BASE_PAGEURL + link.attr("href") + "/";
        Optional<Document> originatorPage = getOriginatorPage(url);

        if (originatorPage.isPresent()) {
            Element affiliationElement = originatorPage.get().selectFirst(".job p");
            Originator originator;
            if (Objects.isNull(affiliationElement)) {
                originator = new Originator(name);
            } else {
                String affiliation = affiliationElement.text().trim();
                originator = new Originator(name, affiliation);
            }
            originator.setColombiaVariables(getOriginatorExtraVariables(originatorPage.get()));
            return originator;
        }
        return new Originator(name);
    }

    private Optional<Document> getOriginatorPage(String url) {
        Optional<PageSource> optOriginatorPage = tryGettingOriginatorPage(url);
        if (optOriginatorPage.isPresent()) {
            return Optional.of(Jsoup.parse(optOriginatorPage.get().getRawSource()));
        } else {
            ChromeDriver browser = SeleniumUtils.getChromeBrowserForColombia();
            log.info("Missing originator page {} from database. Collecting it.", url);
            try {
                browser.get(url);
                PageSource source = SeleniumUtils.downloadColombianPageSource(browser, PageType.ORIGINATOR.label);
                NOT_YET_SAVED_PAGES.add(source);
                log.info("Page collected");
                return Optional.of(Jsoup.parse(source.getRawSource()));
            } catch (RuntimeException ex) {
                log.error("Unable to collect originator page {}, because {}. Skipping it.", url, ex.getMessage());
                return Optional.empty();
            } finally {
                browser.quit();
            }
        }
    }

    private Optional<PageSource> tryGettingOriginatorPage(String url) {
        Optional<PageSource> databasePage = tryGettingOriginatorPageFromDb(url);
        if (databasePage.isPresent()) {
            return databasePage;
        } else {
            return getPageFromNotYetSaved(url);
        }
    }

    private Optional<PageSource> tryGettingOriginatorPageFromDb(String url) {
        try {
            return readService.findByPageTypeAndPageUrl(PageType.ORIGINATOR.label, url);
        } catch (IncorrectResultSizeDataAccessException | NonUniqueResultException ex) {
            log.warn("Originator page url exist multiple times in the database! {}", url);
            return Optional.ofNullable(readService.findAllByPageUrl(url).get(0));
        }
    }

    private Optional<PageSource> getPageFromNotYetSaved(String url) {
        synchronized (NOT_YET_SAVED_PAGES.getLock()) {
            return NOT_YET_SAVED_PAGES.stream()
                    .filter(source -> source.getPageUrl().equals(url))
                    .findAny();
        }
    }

    private ColombiaOriginatorVariables getOriginatorExtraVariables(Document page) {
        ColombiaOriginatorVariables variables = new ColombiaOriginatorVariables();

        Map<String, String> profileCards = page.select(".littleProfileCard .vertiCal-text").stream()
                .filter(Objects::nonNull)
                .map(element -> {
                    Element typeElement = element.selectFirst("small");
                    Element valueElement = element.selectFirst("p");
                    if (Objects.isNull(typeElement) || Objects.isNull(valueElement)) {
                        return null;
                    }
                    String type = typeElement.text().toLowerCase().trim();
                    String value = valueElement.text();
                    return new AbstractMap.SimpleEntry<>(type, value);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        AbstractMap.SimpleEntry::getKey,
                        AbstractMap.SimpleEntry::getValue,
                        (value1, value2) -> value1)
                );

        String education = profileCards.getOrDefault("grado de estudio", "");
        if (!education.isBlank()) {
            variables.setEducation(education);
        }

        try {
            Integer age = Integer.parseInt(profileCards.getOrDefault("edad", ""));
            variables.setAge(age);
        } catch (NumberFormatException ex) {
            log.debug("No age found for originator");
        }


        String genderString = profileCards.getOrDefault("género", "");
        if (!genderString.isBlank()) {
            ColombiaOriginatorVariables.Gender gender = GENDER_TRANSLATIONS.get(genderString.toLowerCase().trim());
            variables.setGender(gender.toString());
        }

        String constituency = profileCards.getOrDefault("circunscripción", "");
        if (!constituency.isBlank()) {
            variables.setConstituency(constituency);
        }

        int investigationsCount = page.select(".verticalTab").stream()
                .filter(tabElement -> {
                    Element titleElement = tabElement.selectFirst(".title");
                    if (Objects.isNull(titleElement)) {
                        return false;
                    }
                    return titleElement.text().equalsIgnoreCase("investigaciones");
                })
                .mapToInt(tabElement -> tabElement.select(".littleProfileCard").size())
                .sum();
        variables.setInvestigationsCount(investigationsCount);
        variables.setInvestigationsDummy(investigationsCount != 0);

        return variables;
    }

    private Originator createOriginatorFromNameText(String[] nameText) {
        String supposedName = nameText[nameText.length - 1];
        String supposedPosition = nameText[0];
        if (isAffiliationString(supposedName)) {
            return new Originator(supposedPosition, createUniformPositionText(supposedName));
        }
        // Government originator, originatorName[] = String[position,...,name]
        return new Originator(supposedName, createUniformPositionText(supposedPosition));
    }

    private boolean isAffiliationString(String text) {
        List<String> regexStrings = List.of(
                "Contralora*",
                "Directora*",
                "Ministr[ao]",
                "President[ae]",
                "Procuradora*",
                "Viceministr[ao]",
                "Vicepresident[ae]",
                "Subdirectora*"
        );
        return regexStrings.stream()
                .map(Pattern::compile)
                .map(regex -> regex.matcher(text))
                .anyMatch(Matcher::find);
    }

    private String createUniformPositionText(String positionRawText) {
        String removedTemporarySigns = positionRawText
                .replaceAll("[(][eE][)]", "")
                .replaceAll("\\s+", " ")
                .trim();
        String removedGenderDifferentiation = removedTemporarySigns
                .replaceAll("Contralora*", "Contralor/a")
                .replaceAll("Directora*", "Director/a")
                .replaceAll("Ministr[ao]", "Ministro/a")
                .replaceAll("President[ae]", "Presidente/a")
                .replaceAll("Procuradora*", "Procurador/a")
                .replaceAll("Viceministr[ao]", "Viceministro/a")
                .replaceAll("Vicepresident[ae]", "Vicepresidente/a")
                .replaceAll("Subdirectora*", "Subdirector/a");
        if (removedGenderDifferentiation.contains("Ministro/a")) {
            return removedGenderDifferentiation
                    .replaceAll("Nacional", "")
                    .trim();
        } else {
            return removedGenderDifferentiation.trim();
        }
    }
}
