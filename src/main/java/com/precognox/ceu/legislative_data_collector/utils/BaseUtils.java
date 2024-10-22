package com.precognox.ceu.legislative_data_collector.utils;

import java.util.Optional;

public class BaseUtils {

    public static String readParam(String key, String defaultValue) {
        return Optional.ofNullable(System.getProperty(key, System.getenv(key))).orElse(defaultValue);
    }
}
