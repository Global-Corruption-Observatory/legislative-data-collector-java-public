package com.precognox.ceu.legislative_data_collector.usa;

import java.util.regex.Pattern;

public class Constants {

    public static String SITE_BASE_URL = "https://www.congress.gov";
    public static final Pattern DATE_REGEX = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");
    public static final Pattern PERIOD_REGEX = Pattern.compile("(\\d{3})(th|rd|nd)-congress");
    public static final String LAW_ID_REGEX = "\\d{2,3}-\\d{1,3}";
    public static final Pattern MODIFIED_LAW_PATTERN =
            Pattern.compile("(?<=Public Law) \\d{1,3}-\\d{1,3}(?=.{1,50}(is|are) (amended|repealed))", Pattern.MULTILINE | Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    public static final String TERM_LIST_PAGE_TEMPLATE =
            "https://www.congress.gov/search" +
                    "?pageSize=250" +
                    "&q=%7B%22source%22%3A%22legislation%22%2C%22type%22%3A%22bills%22%2C%22congress%22%3A{0}%7D" +
                    "&page={1}";

}
