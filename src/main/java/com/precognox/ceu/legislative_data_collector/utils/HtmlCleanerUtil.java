package com.precognox.ceu.legislative_data_collector.utils;

import com.google.common.io.Files;
import lombok.extern.slf4j.Slf4j;
import org.htmlcleaner.BrowserCompactXmlSerializer;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.jsoup.Jsoup;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Slf4j
public class HtmlCleanerUtil {

    private static Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static String formatToCleanXHtml(String html) {
        return formatToCleanXHtml(html, false);
    }

    public static String formatToCleanXHtml(String html, boolean createXmlsForDebug) {
        try {
            if (createXmlsForDebug) {
                File inputPageSource = new File("pageSource_orig.xml");
                Files.write(html, inputPageSource, DEFAULT_CHARSET);
                log.info("pageSource File: " + inputPageSource.getAbsolutePath());
            }
//            html = html.replaceAll("\\[&quot;[^]]+&quot;\\]", "");
            //for debug
            //			result = StringEscapeUtils.unescapeXml(result).replaceAll("&nbsp;", "");
            //            if (createXmlsForDebug) {
            //                File inputPageSource = new File("pageSource.xml");
            //                Files.write(html, inputPageSource, Charset.forName("UTF-8"));
            //                Utils.log("pageSource File: " + inputPageSource.getAbsolutePath());
            //            }
            String xhtml;
            try {
                xhtml = formatToXHtmlWithHtmlCleaner(html);
            } catch (Exception e) {
                log.error("formatToXHtmlWithHtmlCleaner", e);
                xhtml = formatToXHtml(html);
            }

            if (createXmlsForDebug) {
                File pageSource = new File("pageSourceAfterConvert.xml");

                Files.write(xhtml, pageSource, DEFAULT_CHARSET);

                log.info("pageSource File: " + pageSource.getAbsolutePath());
            }
            return xhtml;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static HtmlCleaner getHtmlCleaner() {
        HtmlCleaner cleaner = new HtmlCleaner();

        // take default cleaner properties
        CleanerProperties props = cleaner.getProperties();
        props.setNamespacesAware(false);
        props.setOmitHtmlEnvelope(false);
        props.setOmitXmlDeclaration(false);
        props.setOmitUnknownTags(false);

        props.setUseCdataForScriptAndStyle(true);
        props.setAdvancedXmlEscape(true);
        props.setTranslateSpecialEntities(true);
        props.setRecognizeUnicodeChars(true);
        props.setOmitDoctypeDeclaration(true);
        props.setUseEmptyElementTags(true);
        props.setAllowMultiWordAttributes(true);
        props.setHyphenReplacementInComment("=");
//For debug
//		props.setAllowInvalidAttributeNames(false);
//		props.setAllowHtmlInsideAttributes(true);
//		props.setInvalidXmlAttributeNamePrefix("data-words");
//		props.setAdvancedXmlEscape(true);
        return cleaner;
    }

    private static String formatToXHtml(String html) {
        org.jsoup.nodes.Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
        document.outputSettings().charset(DEFAULT_CHARSET);
        return document.toString();
    }

    private static String formatToXHtmlWithHtmlCleaner(String html) throws IOException {
        StringWriter output = new StringWriter();
        TagNode node = getHtmlCleaner().clean(html);
        node.serialize(new BrowserCompactXmlSerializer(getHtmlCleaner().getProperties()), output);
        output.flush();
        return output.toString();
    }
}
