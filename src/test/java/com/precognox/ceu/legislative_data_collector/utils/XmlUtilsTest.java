package com.precognox.ceu.legislative_data_collector.utils;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XmlUtilsTest {

    public static final String TEST_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<ul>\n"
            + "	<li id=\"first id\">\n"
            + "		Text 1\n"
            + "		<a href=\"http://y.com/x.pdf\">Link 1</a>\n"
            + "	</li>\n"
            + "	<li>\n"
            + "		Text 2\n"
            + "		<a href=\"http://y2.com/x2.pdf\">Link 2</a>\n"
            + "		Text 3\n"
            + "	</li>\n"
            + "</ul>";

    @Test
    public void find() {
        String expression = "//li/a";
        Document document = XmlUtils.parseXml(TEST_XML);
        Elements elements = XmlUtils.find(document, expression);
        assertFalse(elements.isEmpty());
        assertEquals(elements.get(0).text(), "Link 1");
    }
}