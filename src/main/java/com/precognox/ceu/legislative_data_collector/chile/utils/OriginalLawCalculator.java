package com.precognox.ceu.legislative_data_collector.chile.utils;

import com.precognox.ceu.legislative_data_collector.entities.LegislativeDataRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class OriginalLawCalculator {
    private final static List<String> MODIFYING_WORDS = new ArrayList<>();
    private final static List<String> MODIFIED_PARTS = new ArrayList<>();

    static {
        MODIFYING_WORDS.add("mod.fica");
        MODIFYING_WORDS.add("reforma");
        MODIFYING_WORDS.add("reforman");
        MODIFYING_WORDS.add("reformas");
        MODIFYING_WORDS.add("mod.fican");
        MODIFYING_WORDS.add("ad.c.onan");
        MODIFYING_WORDS.add("ad.c.ona");
        MODIFYING_WORDS.add("alteran");
        MODIFYING_WORDS.add("altera");
        MODIFYING_WORDS.add("sustituye");
        MODIFYING_WORDS.add("sustituir");
        MODIFYING_WORDS.add("substituye");
        MODIFYING_WORDS.add("substituir");
        MODIFYING_WORDS.add("mod.ficaciones");
        MODIFYING_WORDS.add("deroga");
        MODIFYING_WORDS.add("derogan");
        MODIFYING_WORDS.add("prorrogan");
        MODIFYING_WORDS.add("prorroga");

        MODIFIED_PARTS.add("ley");
        MODIFIED_PARTS.add("leyes");
        MODIFIED_PARTS.add("decreto");
        MODIFIED_PARTS.add("decretos");
        MODIFIED_PARTS.add("artículos");
        MODIFIED_PARTS.add("artículo");
        MODIFIED_PARTS.add("articulo");
        MODIFIED_PARTS.add("articulos");
        MODIFIED_PARTS.add("dfl");
        MODIFIED_PARTS.add("t.tul.");
        MODIFIED_PARTS.add("libro");
        MODIFIED_PARTS.add("c.digo");
        MODIFIED_PARTS.add("c.digos");
        MODIFIED_PARTS.add("D\\W*L\\W*N");
        MODIFIED_PARTS.add("D\\W*F\\W*L");
    }

    public void fillOriginalLaw(LegislativeDataRecord record) {
        if (nonNull(record.getChileCountrySpecificVariables())
                && isNotBlank(record.getChileCountrySpecificVariables().getLawTitle())) {
            record.setOriginalLaw(isOriginalLaw(record.getChileCountrySpecificVariables().getLawTitle()));
        } else {
            record.setOriginalLaw(isOriginalLaw(record.getBillTitle()));
        }
    }

    public boolean isOriginalLaw(String title) {
        if (isNotBlank(title)) {
            return !(containsAny(MODIFYING_WORDS, title) && containsAny(MODIFIED_PARTS, title));
        }

        return true;
    }

    private boolean containsAny(List<String> regexStrings, String text) {
        return regexStrings.stream()
                .map(word -> Pattern.compile(word, Pattern.CASE_INSENSITIVE))
                .map(regex -> regex.matcher(text))
                .anyMatch(Matcher::find);
    }

}
