package com.precognox.ceu.legislative_data_collector.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;

public class ParserHelper {

    public static String getStringValue(Map<String, Object> map, String key) {
        return Objects.toString(map.get(key), null);
    }

    public static String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        return Objects.toString(map.get(key), defaultValue);
    }

    public static String getBestValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = getStringValue(map, key);

            if (StringUtils.isNoneBlank(value)) {
                return value;
            }
        }

        return null;
    }

}
