package com.precognox.ceu.legislative_data_collector.poland.constants;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PolishTranslations {

    private static final String AWS = "AWS - Klub Parlamentarny Akcji Wyborczej Solidarność";
    private static final String LPR = "LPR - Klub Parlamentarny Liga Polskich Rodzin";
    private static final String PIS = "PiS - Klub Parlamentarny Prawo i Sprawiedliwość";
    private static final String PSL = "PSL - Klub Parlamentarny Polskiego Stronnictwa Ludowego";
    private static final String PO = "PO - Klub Parlamentarny Platforma Obywatelska";
    private static final String SLD = "SLD - Klub Parlamentarny Sojuszu Lewicy Demokratycznej";
    private static final String SAMOOBRONA = "Samoobrona - Klub Parlamentarny Samoobrona Rzeczypospolitej Polskiej";
    private static final String UP = "UP - Koło Parlamentarne Unii Pracy";
    private static final String UW = "UW - Klub Parlamentarny Unii Wolności";

    public static final Map<String, Integer> MONTHS_TRANSLATIONS = new HashMap<>();

    static {
        MONTHS_TRANSLATIONS.put("stycznia", 1);
        MONTHS_TRANSLATIONS.put("lutego", 2);
        MONTHS_TRANSLATIONS.put("marca", 3);
        MONTHS_TRANSLATIONS.put("kwietnia", 4);
        MONTHS_TRANSLATIONS.put("maja", 5);
        MONTHS_TRANSLATIONS.put("czerwca", 6);
        MONTHS_TRANSLATIONS.put("lipca", 7);
        MONTHS_TRANSLATIONS.put("sierpnia", 8);
        MONTHS_TRANSLATIONS.put("września", 9);
        MONTHS_TRANSLATIONS.put("października", 10);
        MONTHS_TRANSLATIONS.put("listopada", 11);
        MONTHS_TRANSLATIONS.put("grudnia", 12);
    }

    public static final Map<String, String> PARTY_FULL_NAMES = new HashMap<>();

    static {
        PARTY_FULL_NAMES.put("Alternatyw", "Koło Parlamentarne Alternatywa");
        PARTY_FULL_NAMES.put("AWS", "Klub Parlamentarny Akcji Wyborczej Solidarność");
        PARTY_FULL_NAMES.put("BC", "Koło Poselskie Biało-Czerwoni");
        PARTY_FULL_NAMES.put("D_OJCZYSTY", "Koło Poselskie \"Dom Ojczysty\"");
        PARTY_FULL_NAMES.put("KL", "Koło Poselskie Konserwatywno-Ludowe");
        PARTY_FULL_NAMES.put("Konfederacja", "Koło Poselskie Konfederacja");
        PARTY_FULL_NAMES.put("KO", "Klub Parlamentarny Koalicja Obywatelska - Platforma Obywatelska, Nowoczesna, Inicjatywa Polska, Zieloni");
        PARTY_FULL_NAMES.put("KP", "Klub Parlamentarny Koalicja Polska - PSL, UED, Konserwatyści");
        PARTY_FULL_NAMES.put("KPSP", "Sprawiedliwa Polska");
        PARTY_FULL_NAMES.put("Kukiz15", "Koło Poselskie Kukiz'15 - Demokracja Bezpośrednia");
        PARTY_FULL_NAMES.put("LD", "Koło Parlamentarne Lewicy Demokratycznej");
        PARTY_FULL_NAMES.put("Lewica", "Koalicyjny Klub Parlamentarny Lewicy (Nowa Lewica, PPS, Razem)");
        PARTY_FULL_NAMES.put("LPR", "Klub Parlamentarny Liga Polskich Rodzin");
        PARTY_FULL_NAMES.put("niez.", "Posłowie niezrzeszeni");
        PARTY_FULL_NAMES.put("PiS", "Klub Parlamentarny Prawo i Sprawiedliwość");
        PARTY_FULL_NAMES.put("PJN", "Klub Parlamentarny Polska Jest Najważniejsza");
        PARTY_FULL_NAMES.put("PO", "Klub Parlamentarny Platforma Obywatelska");
        PARTY_FULL_NAMES.put("Polska2050", "Koło Parlamentarne Polska 2050");
        PARTY_FULL_NAMES.put("Polska_Plus", "Polska Plus");
        PARTY_FULL_NAMES.put("PO-KO", "Klub Parlamentarny Platforma Obywatelska - Koalicja Obywatelska");
        PARTY_FULL_NAMES.put("PP", "Koło Poselskie Przywrócić Prawo");
        PARTY_FULL_NAMES.put("Prawica", "Koło Poselskie Prawica Rzeczypospolitej");
        PARTY_FULL_NAMES.put("PS", "Polskie Sprawy");
        PARTY_FULL_NAMES.put("PSL", "Klub Parlamentarny Polskiego Stronnictwa Ludowego");
        PARTY_FULL_NAMES.put("PSL-KP", "Klub Parlamentarny Polskie Stronnictwo Ludowe - Koalicja Polska");
        PARTY_FULL_NAMES.put("PSL-UED", "Klub Parlamentarny Polskie Stronnictwo Ludowe - Unia Europejskich Demokratów");
        PARTY_FULL_NAMES.put("RKN", "Koło Poselskie Ruchu Katolicko-Narodowego");
        PARTY_FULL_NAMES.put("RLN", "Koło Poselskie Ruch Ludowo-Narodowy");
        PARTY_FULL_NAMES.put("ROP", "Koło Parlamentarne Ruchu Odbudowy Polski");
        PARTY_FULL_NAMES.put("RP", "Koło Poselskie Ruch Patriotyczny");
        PARTY_FULL_NAMES.put("Samoobrona", "Klub Parlamentarny Samoobrona Rzeczypospolitej Polskiej");
        PARTY_FULL_NAMES.put("SDPL", "Klub Parlamentarny Socjaldemokracji Polskiej");
        PARTY_FULL_NAMES.put("SG", "Koło Poselskie Stronnictwa Gospodarczego");
        PARTY_FULL_NAMES.put("SKL", "Klub Parlamentarny Stronnictwa Konserwatywno-Ludowego");
        PARTY_FULL_NAMES.put("SLD", "Klub Parlamentarny Sojuszu Lewicy Demokratycznej");
        PARTY_FULL_NAMES.put("TERAZ!", "Koło Poselskie TERAZ!");
        PARTY_FULL_NAMES.put("TR", "Twój Ruch");
        PARTY_FULL_NAMES.put("UP", "Koło Parlamentarne Unii Pracy");
        PARTY_FULL_NAMES.put("UPR", "Unia Polityki Realnej");
        PARTY_FULL_NAMES.put("UW", "Klub Parlamentarny Unii Wolności");
        PARTY_FULL_NAMES.put("WiS", "Wolni i Solidarni");
        PARTY_FULL_NAMES.put("ZP", "Klub Parlamentarny Zjednoczona Prawica");
    }

    public static Map<String, String> getPartyFullNames() {
        return PARTY_FULL_NAMES;
    }

    public static String getRomanTermNoFromTermNo(String termNumber) {
        return switch (termNumber) {
            case "3" -> "III";
            case "4" -> "IV";
            case "5" -> "V";
            case "6" -> "VI";
            case "7" -> "VII";
            case "8" -> "VIII";
            case "9" -> "IX";
            default -> throw new IllegalStateException("Unexpected value: " + termNumber);
        };
    }

    // Originally it was necessary to find the exact ruling party while bill/law was passed in order to find out if the
    // law's procedure type is standard or exceptional. After the first data shipment the "procedure_type" variable was
    // removed from the scope completely. Maybe that would be necessary in the future.
    public static Set<String> getRulingParties(LocalDate processStartDate) {
        if (processStartDate.isAfter(LocalDate.of(1997, 10, 31))
                && processStartDate.isBefore(LocalDate.of(2001, 10, 19))) {
            return Set.of(AWS, UW);
        } else if (processStartDate.isAfter(LocalDate.of(2001, 10, 19))
                && processStartDate.isBefore(LocalDate.of(2004, 5, 2))) {
            return Set.of(SLD, PSL, UP);
        } else if (processStartDate.isAfter(LocalDate.of(2004, 5, 2))
                && processStartDate.isBefore(LocalDate.of(2004, 5, 19))) {
            return Set.of(SLD, UP);
        } else if (processStartDate.isAfter(LocalDate.of(2004, 6, 11))
                && processStartDate.isBefore(LocalDate.of(2005, 10, 19))) {
            return Set.of(SLD);
        } else if (processStartDate.isAfter(LocalDate.of(2005, 10, 31))
                && processStartDate.isBefore(LocalDate.of(2007, 11, 5))) {
            return Set.of(PIS, SAMOOBRONA, LPR);
        } else if (processStartDate.isAfter(LocalDate.of(2007, 11, 16))
                && processStartDate.isBefore(LocalDate.of(2015, 11, 16))) {
            return Set.of(PO, PSL);
        } else if (processStartDate.isAfter(LocalDate.of(2015, 11, 16))
                && processStartDate.isBefore(LocalDate.of(2023, 11, 13))) {
            return Set.of(PIS);
        } else {
            return Set.of("No ruling parties found");
        }
    }
}
