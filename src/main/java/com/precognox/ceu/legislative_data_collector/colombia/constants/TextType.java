package com.precognox.ceu.legislative_data_collector.colombia.constants;

public enum TextType {
    GAZETTE("gazette"),
    BILL_TEXT("bill text"),
    AMENDMENT_STAGE_1_TEXT("first debate amendment text"),
    AMENDMENT_STAGE_1_LINK_TEXT("first debate amendment link text"),
    AMENDMENT_STAGE_2_TEXT("second debate amendment text"),
    AMENDMENT_STAGE_2_LINK_TEXT("second debate amendment link text"),
    AMENDMENT_STAGE_3_TEXT("third debate amendment text"),
    AMENDMENT_STAGE_3_LINK_TEXT("third debate amendment link text"),
    AMENDMENT_STAGE_4_TEXT("fourth debate amendment text"),
    AMENDMENT_STAGE_4_LINK_TEXT("fourth debate amendment link text"),
    AMENDMENT_STAGE_13_JOINED_TEXT("first & third debate amendment text"),
    AMENDMENT_STAGE_13_JOINED_LINK_TEXT("first & third debate amendment link text");

    public final String label;

    TextType(final String label) {
        this.label = label;
    }
}
