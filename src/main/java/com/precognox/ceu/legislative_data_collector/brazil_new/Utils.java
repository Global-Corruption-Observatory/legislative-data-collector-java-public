package com.precognox.ceu.legislative_data_collector.brazil_new;

import com.precognox.ceu.legislative_data_collector.entities.OriginType;
import com.precognox.ceu.legislative_data_collector.entities.Originator;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

public class Utils {
    public static final String BASE_DOMAIN = "https://www.lexml.gov.br";
    public static final DateTimeFormatter DATE_PARSER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final String DATE_REGEX = "\\d{2}/\\d{2}/\\d{4}";
    public static final Pattern DATE_PATTERN = Pattern.compile(DATE_REGEX);

    public static String toAbsolute(String relativeUrl) {
        return relativeUrl.startsWith("http") ? relativeUrl : BASE_DOMAIN + relativeUrl;
    }

    public static LocalDate parseDate(String date) {
        return LocalDate.parse(date, DATE_PARSER);
    }

    public static OriginType getOriginType(List<Originator> origs) {
        if (origs.isEmpty()) {
            return null;
        }

        //origin type depends on originator name (MP or gov. body) - no affiliation means gov. body
        if (origs.size() == 1) {
            if (origs.get(0).getName().contains("Comissão")) {
                return OriginType.LEGISLATIVE; //if the originator is a committee
            } else if (origs.get(0).getAffiliation() == null) {
                return OriginType.GOVERNMENT;
            }
        }

        return OriginType.INDIVIDUAL_MP;
    }
}
