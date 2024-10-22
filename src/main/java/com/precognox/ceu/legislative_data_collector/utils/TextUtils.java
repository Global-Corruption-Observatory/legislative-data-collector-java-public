package com.precognox.ceu.legislative_data_collector.utils;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TextUtils {

    private static final String GENERAL_JUSTIFICATION_HEADER = "Általános indokolás";
    private static final List<String> TITLES = List.of("Dr. ", "Dr ", "Mrs ", "Mr ", "Ms ", "Sir ");

    public static String cleanName(String origName) {
        if (origName != null) {
            return TITLES.stream().reduce(origName, (name, s) -> name.replace(s, "")).strip();
        }

        return null;
    }

    public static void removeGeneralJustification(LegislativeDataRecord record) {
        if (record.getBillText() != null) {
            String billText = record.getBillText();

            if (billText.contains(GENERAL_JUSTIFICATION_HEADER)) {
                int idx = billText.indexOf(GENERAL_JUSTIFICATION_HEADER);
                String cleaned = billText.substring(0, idx);
                String removedPart = billText.substring(idx);

                record.setBillText(cleaned);
                record.setBillTextGeneralJustification(removedPart);
            }
        }
    }

    public static int getLengthWithoutWhitespace(String text) {
        if (text == null) {
            return 0;
        }

        return text.replaceAll("\\s", "").length();
    }

    public static String findText(String text, String regex) {
        List<String> result = findTexts(text, regex, true, -1);
        return result.isEmpty() ? "" : result.get(0);
    }

    public static List<String> findTexts(String text, String regex) {
        return findTexts(text, regex, true, -1);
    }

    public static List<String> findTexts(String text, String regex, boolean enableTrim, Integer group) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);

        List<String> resultList = new ArrayList<>();

        while (matcher.find()) {
            Integer selectedGroup = group;

            if (selectedGroup == null || selectedGroup < 0) {
                selectedGroup = matcher.groupCount();
            }

            String result = matcher.group(selectedGroup);
            result = enableTrim ? result.trim() : result;
            resultList.add(result);
        }

        return resultList;
    }

    /**
     * Searches for regex patterns in the provided string. Returns the first match.
     *
     * @param text    The string in which we'd like to find a pattern.
     * @param regexes The patterns we are matching to the string.
     * @return The first match in an array of two. The first element is the matched section of the text,
     * the second is the matching pattern. If there were no matches it returns with null.
     */
    public static String[] getMatchingRegex(final String text, final String[] regexes) {
        var result = new String[2];

        for (String regex : regexes) {
            String matchedText = findText(text, regex);
            if (!matchedText.isEmpty()) {
                result[0] = matchedText;
                result[1] = regex;
                return result;
            }
        }

        return null;
    }

    public static String removeHtml(String text) {
        //remove html tags and entities
        return text.replaceAll("<.+?>", "").replaceAll("&[a-z]+?;", "");
    }

    public static String cleanHTMLEntitiesFromText(String text) {
        String temp = text
                .replaceAll("<.*?>", " ")
                .replaceAll("[^\\S\\r\\n]+", " ");

        return StringEscapeUtils.unescapeHtml4(temp).trim();
    }

    public static Integer toInteger(String value, Integer defaultVal) {
        if (StringUtils.isBlank(value)) {
            return defaultVal;
        } else {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException var3) {
                return defaultVal;
            }
        }
    }

    public static List<String> findSections(
            String text, String sectionStartRegex, String sectionEndRegex, boolean enableTrim, Integer group) {
        Pattern pattern = Pattern.compile(sectionStartRegex);
        Matcher matcher = pattern.matcher(text);

        List<String> resultList = new ArrayList<>();
        List<Integer> startSectionList = new ArrayList<>();

        while (matcher.find()) {
            Integer selectedGroup = group;
            if (selectedGroup == null || selectedGroup < 0) {
                selectedGroup = matcher.groupCount();
            }

            int start = matcher.start(selectedGroup);
            startSectionList.add(start);
        }

        for (int i = 0; i < startSectionList.size(); i++) {
            Integer startSection = startSectionList.get(i);
            Integer endSection = startSectionList.size() > (i + 1) ? startSectionList.get(i + 1) : text.length();

            if (StringUtils.isNotBlank(sectionEndRegex)) {
                int endSectionFromRegex = getFirstPosition(text, matcher, sectionEndRegex, group);
                if (endSectionFromRegex < endSection) {
                    endSection = endSectionFromRegex;
                }
            }

            String sectionText = text.substring(startSection, endSection);
            sectionText = enableTrim ? sectionText.trim() : sectionText;
            resultList.add(sectionText);
        }

        return resultList;
    }
    private static int getFirstPosition(String text, Matcher matcher, String regex, Integer group) {
        if (matcher.find()) {
            Integer selectedGroup = group;
            if (selectedGroup == null || selectedGroup < 0) {
                selectedGroup = matcher.groupCount();
            }

            return matcher.start(selectedGroup);
        }

        return text.length() - 1;
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    public static List<String> findSections(String text, String sectionStartRegex) {
        return findSections(text, sectionStartRegex, sectionStartRegex, true, null);
    }

    public static String trimLines(String fullText) {
        return fullText.lines().map(String::trim).collect(Collectors.joining("\n"));
    }

    public static Optional<Integer> getTextSize(String text) {
        if (StringUtils.isEmpty(text)) {
            return Optional.empty();
        }
        return Optional.of(TextUtils.getLengthWithoutWhitespace(text));
    }

    public static String convertCamelCaseToSnakeCase(String orig) {
        return orig
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase();
    }

}
