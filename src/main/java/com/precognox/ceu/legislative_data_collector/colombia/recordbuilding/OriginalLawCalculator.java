package com.precognox.ceu.legislative_data_collector.colombia.recordbuilding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OriginalLawCalculator {
    private final static List<String> MODIFYING_WORDS = new ArrayList<>();
    private final static List<String> MODIFIED_PARTS = new ArrayList<>();

    static {
        MODIFYING_WORDS.add("modifica");
        MODIFYING_WORDS.add("modifican");
        MODIFYING_WORDS.add("adicionan");
        MODIFYING_WORDS.add("adiciona");
        MODIFYING_WORDS.add("alteran");
        MODIFYING_WORDS.add("altera");
        MODIFYING_WORDS.add("sustituye");
        MODIFYING_WORDS.add("sustituir");
        MODIFYING_WORDS.add("substituye");
        MODIFYING_WORDS.add("substituir");
        MODIFYING_WORDS.add("modificaciones");
        MODIFYING_WORDS.add("deroga");
        MODIFYING_WORDS.add("derogan");
        MODIFYING_WORDS.add("reforma");
        MODIFYING_WORDS.add("reforman");
        MODIFYING_WORDS.add("reformas");
        MODIFYING_WORDS.add("prorroga");
        MODIFYING_WORDS.add("prorrogan");

        MODIFIED_PARTS.add("ley");
        MODIFIED_PARTS.add("leyes");
        MODIFIED_PARTS.add("decreto");
        MODIFIED_PARTS.add("decretos");
        MODIFIED_PARTS.add("artículos");
        MODIFIED_PARTS.add("artículo");
        MODIFIED_PARTS.add("articulo");
        MODIFIED_PARTS.add("articulos");
        MODIFIED_PARTS.add("c.digo");
        MODIFIED_PARTS.add("c.digos");
    }

    public OriginalLawCalculator() {
    }

    public boolean isOriginalLaw(String text) {
        return !(containsAny(MODIFIED_PARTS, text) && containsAny(MODIFYING_WORDS, text));
    }

    private boolean containsAny(List<String> regexStrings, String text) {
        return regexStrings.stream()
                .map(word -> Pattern.compile(word, Pattern.CASE_INSENSITIVE))
                .map(regex -> regex.matcher(text))
                .anyMatch(Matcher::find);
    }

}
