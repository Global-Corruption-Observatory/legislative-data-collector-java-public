package com.precognox.ceu.legislative_data_collector.colombia.constants;

public enum PageType {
    ORIGINATOR("originator"),
    VOTES("votes"),
    LAW("law"),
    BILL("bill"),
    LAW_TEXT("law text");

    public final String label;

    PageType(final String label) {
        this.label = label;

    }
}
