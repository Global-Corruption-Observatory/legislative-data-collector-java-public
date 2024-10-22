package com.precognox.ceu.legislative_data_collector.sweden;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Integer.parseInt;

@Slf4j
public class Utils {

    private static final Map<String, Integer> MONTH_TRANSLATIONS = new HashMap<>();

    static {
        MONTH_TRANSLATIONS.put("januari", 1);
        MONTH_TRANSLATIONS.put("februari", 2);
        MONTH_TRANSLATIONS.put("mars", 3);
        MONTH_TRANSLATIONS.put("april", 4);
        MONTH_TRANSLATIONS.put("maj", 5);
        MONTH_TRANSLATIONS.put("juni", 6);
        MONTH_TRANSLATIONS.put("juli", 7);
        MONTH_TRANSLATIONS.put("augusti", 8);
        MONTH_TRANSLATIONS.put("september", 9);
        MONTH_TRANSLATIONS.put("oktober", 10);
        MONTH_TRANSLATIONS.put("november", 11);
        MONTH_TRANSLATIONS.put("december", 12);
    }

    /**
     * Expected date format: 21 juni 2023
     *
     * @param dateExpr The date as a string
     *
     * @return Parsed {@link LocalDate}
     */
    public static LocalDate parseDateExpr(String dateExpr) {
        if (dateExpr == null) {
            return null;
        }

        String[] parts = dateExpr.trim().replace("\n", " ").split(" ");

        try {
            int day = parseInt(parts[0]);
            String monthStr = parts[1].toLowerCase();

            if (MONTH_TRANSLATIONS.containsKey(monthStr)) {
                Integer month = MONTH_TRANSLATIONS.get(monthStr);
                int year = parseInt(parts[2]);

                return LocalDate.of(year, month, day);
            } else {
                log.error("Unknown month string: {}", monthStr);
            }
        } catch (NumberFormatException e) {
            log.error("Failed to parse date: " + dateExpr, e);
        }

        return null;
    }

    /**
     * Expected date format: 2023-06-13
     *
     * @param numericDate The date as a string
     *
     * @return Parsed {@link LocalDate}
     */
    public static LocalDate parseNumericDate(String numericDate) {
        if (numericDate == null) {
            return null;
        }

        String[] parts = numericDate.strip().split("-");

        return LocalDate.of(parseInt(parts[0]), parseInt(parts[1]), parseInt(parts[2]));
    }

}
