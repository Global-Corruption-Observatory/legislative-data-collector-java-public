package com.precognox.ceu.legislative_data_collector.hungary;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;
import com.precognox.ceu.legislative_data_collector.utils.DateUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord.BillStatus.*;
import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;

public class IromanyokAdataiTableParser {

    private Element iromAdatDiv;
    private LegislativeDataRecord result;

    public IromanyokAdataiTableParser(LegislativeDataRecord result, Element iromAdatDiv) {
        this.result = result;
        this.iromAdatDiv = iromAdatDiv;
    }

    public void parseVariables() {
        Optional.ofNullable(iromAdatDiv.selectFirst("table"))
                .ifPresent(iromanyokAdataiTable -> {
                    result.setOriginalLaw(parseBillNature(iromanyokAdataiTable));
                    parseDateIntroduction(iromanyokAdataiTable, result);
                    parseLawType(iromanyokAdataiTable, result);
                    result.setLawId(parseLawId(iromanyokAdataiTable));
                    result.setBillStatus(parseBillStatus(iromanyokAdataiTable));
                    result.setDateEnteringIntoForce(parseDateEnteringForce(iromanyokAdataiTable));
                    parseProcedureType(iromanyokAdataiTable, result);
                    result.setBillTextUrl(parseBillTextUrl(iromanyokAdataiTable));
                    parseOriginators(iromanyokAdataiTable, result);
                });
    }

    private void parseOriginators(Element iromanyokAdataiTable, LegislativeDataRecord result) {
        getValueByLabel(iromanyokAdataiTable, "Benyújtó(k)").ifPresent(originator -> {
            if (originator.contains("kormány") || originator.contains("bizottság")) {
                result.setOriginType(OriginType.GOVERNMENT);
                result.setOriginators(List.of(new Originator(originator)));
            } else {
                result.setOriginType(OriginType.INDIVIDUAL_MP);
                List<Originator> originators = parseOriginatorList(originator);

                result.setOriginators(originators);
            }
        });
    }

    private List<Originator> parseOriginatorList(String originator) {
        String result = originator
                .replaceAll("\\d", "")
                .replaceAll(",", "\n")
                .replaceAll(":", "")
                .replaceAll("\\.{2,}", "");

        return Arrays.stream(result.split("\n"))
                .map(String::trim)
                .map(this::parseOriginator)
                .toList();
    }

    private Originator parseOriginator(String origString) {
        if (origString.contains("(")) {
            int affNameStart = origString.indexOf("(");
            int affNameEnd = origString.contains(")") ? origString.indexOf(")") : origString.length() - 1;

            String name = origString.substring(0, affNameStart).trim();
            String affiliation = origString.substring(affNameStart + 1, affNameEnd);

            if ("független".equals(affiliation)) {
                affiliation = "independent";
            }

            return new Originator(name, affiliation);
        } else {
            return new Originator(origString);
        }
    }

    @Nullable
    private Boolean parseBillNature(Element iromanyokAdataiTable) {
        return findCellByText(iromanyokAdataiTable, "Jelleg")
                .map(Element::nextElementSibling)
                .map(Element::text)
                .map(this::isOriginalLaw)
                .orElse(null);
    }

    @Nullable
    private Boolean isOriginalLaw(String billNatureTerm) {
        return switch (billNatureTerm) {
            case "új" -> true;
            case "módosító" -> false;
            default -> null;
        };
    }

    private void parseDateIntroduction(Element iromanyokAdataiTable, LegislativeDataRecord result) {
        getValueByLabel(iromanyokAdataiTable, "Benyújtva")
                .map(DateUtils::parseHungaryDate)
                .ifPresent(dateIntro -> {
                    result.setDateIntroduction(dateIntro);
                    result.setBillId(dateIntro.getYear() + "/" + result.getBillId());
                });
    }

    private void parseLawType(Element iromanyokAdataiTable, LegislativeDataRecord result) {
        getValueByLabel(iromanyokAdataiTable, "Típus")
                .ifPresent(lawType -> {
                    result.setBillType(lawType);
                    result.setTypeOfLawEng(Translations.LAW_TYPE_TRANSLATIONS.get(lawType.toLowerCase()));
                });
    }

    private String parseLawId(Element iromanyokAdataiTable) {
        return getValueByLabel(iromanyokAdataiTable, "Kihirdetés száma").orElse(null);
    }

    private LegislativeDataRecord.BillStatus parseBillStatus(Element iromanyokAdataiTable) {
        return getValueByLabel(iromanyokAdataiTable, "Állapot")
                .map(this::parseStatusLabel)
                .orElse(null);
    }

    private LegislativeDataRecord.BillStatus parseStatusLabel(String status) {
        return switch (status.toLowerCase()) {
            case "kihirdetve" -> PASS;
            case "országgyűlés nem tárgyalja",
                    "elutasítva",
                    "az ogy nem tárgyalja",
                    "tárgyalása lezárva",
                    "visszavonva",
                    "visszautasítva" -> REJECT;
            default -> ONGOING;
        };
    }

    private void parseProcedureType(Element iromanyokAdataiTable, LegislativeDataRecord result) {
        getValueByLabel(iromanyokAdataiTable, "Tárgyalási mód").ifPresent(term -> {
            result.setProcedureTypeNational(term);
            result.setProcedureTypeEng(Translations.PROCEDURE_TYPE_TRANSLATIONS.get(term.toLowerCase()));
            result.setProcedureTypeStandard(parseProcedureType(term));
        });
    }

    @Nullable
    private LegislativeDataRecord.ProcedureType parseProcedureType(String term) {
        if (containsIgnoreCase(term, "normál")) {
            return LegislativeDataRecord.ProcedureType.REGULAR;
        } else if (containsIgnoreCase(term, "sürgős") || containsIgnoreCase(term, "kivételes")) {
            return LegislativeDataRecord.ProcedureType.EXCEPTIONAL;
        }

        return null;
    }

    private String parseBillTextUrl(Element iromanyokAdataiTable) {
        return findCellByText(iromanyokAdataiTable, "Irományszöveg")
                .map(Element::nextElementSibling)
                .map(this::extractLinks)
                .filter(links -> !links.isEmpty())
                .map(links -> links.stream().filter(l -> l.endsWith(".pdf")).findFirst()
                        .orElseGet(() -> links.get(0)))
                .orElse(null);
    }

    @NotNull
    private List<String> extractLinks(Element cell) {
        return cell.getElementsByTag("a")
                .stream()
                .map(aTag -> aTag.attr("href"))
                .toList();
    }

    private LocalDate parseDateEnteringForce(Element iromanyokAdataiTable) {
        return getValueByLabel(iromanyokAdataiTable, "Kihirdetés dátuma")
                .map(DateUtils::parseHungaryDate)
                .orElse(null);
    }

    private Optional<String> getValueByLabel(Element iromanyokAdataiTable, String label) {
        return findCellByText(iromanyokAdataiTable, label)
                .map(Element::nextElementSibling)
                .map(Element::text);
    }

    private Optional<Element> findCellByText(Element table, String text) {
        if (table.tagName().equals("table")) {
            return Optional.ofNullable(table.selectXpath("//*[text()=\"%s\"]".formatted(text)).first());
        }

        return Optional.empty();
    }

}
