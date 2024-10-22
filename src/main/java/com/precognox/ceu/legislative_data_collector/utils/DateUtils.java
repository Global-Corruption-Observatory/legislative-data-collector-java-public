package com.precognox.ceu.legislative_data_collector.utils;

import com.precognox.ceu.legislative_data_collector.entities.Country;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class DateUtils {

    private static final DateTimeFormatter hungaryDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd.");
    private static final DateTimeFormatter colombiaDateFormatter = DateTimeFormatter.ofPattern("M/d/yyyy");
    private static final DateTimeFormatter colombiaDateFormatterInVote = DateTimeFormatter.ofPattern("d/MM/yyyy");
    private static final DateTimeFormatter colombiaDateFormatterInLawText = DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final DateTimeFormatter chileDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter usaDateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter polandDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter southAfricaDateFormatter =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter southAfricaIADateFormatter =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter southAfricaAffectingLawDateFormatter =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter australiaDateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    private static final Map<Country, DateTimeFormatter> formatsForCountries = Map.of(
            Country.HUNGARY, hungaryDateFormatter,
            Country.COLOMBIA, colombiaDateFormatter,
            Country.CHILE, chileDateFormatter,
            Country.POLAND, polandDateFormatter,
            Country.SOUTH_AFRICA, southAfricaDateFormatter
                                                                                     );

    public static LocalDate toLocalDate(String dateString, String dateFormat) {
        return toLocalDate(dateString, dateFormat, null);
    }

    public static LocalDate toLocalDate(String dateString, String dateFormat, boolean trim) {
        return toLocalDate(dateString, dateFormat, null, trim);
    }

    public static LocalDate toLocalDate(String dateString, String dateFormat, Locale locale) {
        return toLocalDate(dateString, dateFormat, locale, false);
    }

    public static LocalDate toLocalDate(String dateString, String dateFormat, Locale locale, boolean trim) {
        if (StringUtils.isNoneBlank(dateString)) {
            if (trim && dateString.length() > dateFormat.length()) {
                dateString = dateString.substring(0, dateFormat.length());
            }
            try {
                DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(dateFormat);
                if (locale != null) {
                    dateTimeFormatter = dateTimeFormatter.localizedBy(locale);
                }
                return LocalDate.parse(dateString, dateTimeFormatter);
            } catch (DateTimeParseException e) {
                log.error("toLocalDate dateString: '{}',  dateFormat: '{}'", dateString, dateFormat);
                log.error("Failed to parse Date time", e);
            }
        }

        return null;
    }

    public static LocalDate parseUkDate(String dateString) {
        if (dateString != null && !dateString.isBlank()) {
            return LocalDateTime.parse(dateString).toLocalDate();
        }

        return null;
    }

    public static LocalDate parseDateForCountry(String dateString, Country country) {
        if (!formatsForCountries.containsKey(country)) {
            return null;
        }

        if (dateString != null && !dateString.isBlank()) {
            return LocalDate.parse(dateString, formatsForCountries.get(country));
        }

        return null;
    }

    public static LocalDate parseHungaryDate(String dateString) {
        if (dateString != null && !dateString.isBlank()) {
            return LocalDate.parse(dateString, hungaryDateFormatter);
        }

        return null;
    }

    public static LocalDate parseColombiaDate(String dateString) {
        if (dateString != null && !dateString.isBlank()) {
            try {
                return LocalDate.parse(dateString, colombiaDateFormatter);
            } catch (DateTimeParseException e) {
                try {
                    return toLocalDate(dateString, "yyyy. MM. dd.");
                } catch (DateTimeParseException ex) {
                    ex.printStackTrace();
                    return null;
                }
            }
        }
        return null;
    }

    public static LocalDate parseColombiaVotingDate(String dateString) {
        if (dateString != null && !dateString.isBlank()) {
            try {
                return LocalDate.parse(dateString, colombiaDateFormatterInVote);
            } catch (DateTimeParseException e) {
                try {
                    return toLocalDate(dateString, "yyyy. MM. dd.");
                } catch (DateTimeParseException ex) {
                    ex.printStackTrace();
                    return null;
                }
            }
        }
        return null;
    }

    public static LocalDate parseColombiaLawTextDate(String dateString) {
        if (dateString != null && !dateString.isBlank()) {
            try {
                return LocalDate.parse(dateString, colombiaDateFormatterInLawText);
            } catch (DateTimeParseException e) {
                try {
                    return toLocalDate(dateString, "yyyy. MM. dd.");
                } catch (DateTimeParseException ex) {
                    ex.printStackTrace();
                    return null;
                }
            }
        }
        return null;
    }

    public static LocalDate parseChileDate(String dateString) {
        if (dateString != null && !dateString.isBlank()) {
            return LocalDate.parse(dateString, chileDateFormatter);
        }

        return null;
    }

    public static LocalDate parseUsaDate(String dateString) {
        if (dateString != null && !dateString.isBlank()) {
            return LocalDate.parse(dateString, usaDateFormatter);
        }

        return null;
    }

    public static LocalDate parsePolandDate(String dateString) {
        if (dateString != null && !dateString.isBlank()) {
            return LocalDate.parse(dateString, polandDateFormatter);
        }

        return null;
    }

    public static LocalDate parseSouthAfricaDate(String dateString) {
        return parseSouthAfricaDateFormat(dateString, southAfricaDateFormatter);
    }

    public static LocalDate parseSouthAfricaImpactAssessmentDate(String dateString) {
        return parseSouthAfricaDateFormat(dateString, southAfricaIADateFormatter);
    }

    public static LocalDate parseSouthAfricaAffectingLawDate(String dateString) {
        return parseSouthAfricaDateFormat(dateString, southAfricaAffectingLawDateFormatter);
    }

    public static LocalDate parseSouthAfricaDateFormat(String dateString, DateTimeFormatter formatter) {
        return Optional.ofNullable(dateString)
                .filter(s -> !s.isBlank())
                .map(date -> {
                    try {
                        return LocalDate.parse(date, formatter);
                    } catch (DateTimeParseException dtpe) {
                        log.error("Unable to parse " + date + " date: ", dtpe);
                        return null;
                    }
                })
                .orElse(null);
    }

    public static LocalDate parseAustraliaDate(String dateString) {
        if (dateString != null && !dateString.isBlank()) {
            return LocalDate.parse(dateString, australiaDateFormatter);
        }

        return null;
    }

    public static Period getDifference(LocalDate date1, LocalDate date2) {
        LocalDate start, end;
        if (date1.isBefore(date2)) {
            start = date1;
            end = date2;
        } else {
            start = date2;
            end = date1;
        }

        return Period.between(start, end);
    }

}
