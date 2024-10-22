package com.precognox.ceu.legislative_data_collector.bulgaria;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Constants {

    static final List<String> MODIFYING_LAW_PATTERNS = List.of(
            "Законопроект за изменение и допълнение на Закона",
            "Законопроект за изменение на Закона",
            "Законопроект за допълнение на Закона",
            "за изменение и допълнение на Закона",
            "ЗИД на Закона"
    );

    static final Map<String, Integer> MONTH_TRANSLATIONS = new HashMap<>();

    static {
        MONTH_TRANSLATIONS.put("януари", 1);
        MONTH_TRANSLATIONS.put("февруари", 2);
        MONTH_TRANSLATIONS.put("март", 3);
        MONTH_TRANSLATIONS.put("април", 4);
        MONTH_TRANSLATIONS.put("май", 5);
        MONTH_TRANSLATIONS.put("юни", 6);
        MONTH_TRANSLATIONS.put("юли", 7);
        MONTH_TRANSLATIONS.put("август", 8);
        MONTH_TRANSLATIONS.put("септември", 9);
        MONTH_TRANSLATIONS.put("октомври", 10);
        MONTH_TRANSLATIONS.put("ноември", 11);
        MONTH_TRANSLATIONS.put("декември", 12);
    }

}
