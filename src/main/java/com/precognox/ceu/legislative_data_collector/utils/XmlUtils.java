package com.precognox.ceu.legislative_data_collector.utils;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.poi.hpsf.CodePageString;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.utils.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Selector;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;

@Slf4j
public class XmlUtils {

    public static final String TR_REPLACEMENT = "-tr-";

    public static Document parseXml(String xml) {
        System.setProperty(W3CDom.XPathFactoryProperty, "net.sf.saxon.xpath.XPathFactoryImpl");

        String cleanXml = HtmlCleanerUtil.formatToCleanXHtml(xml);
        return Jsoup.parse(cleanXml);
    }

    public static Elements find(Element element, String xPath) {
        return element.selectXpath(xPath);
    }

    public static Optional<Element> findFirst(Element element, String xPath) {
        return Optional.ofNullable(find(element, xPath).first());
    }

    public static String findElementText(Element element, String xPath) {
        try {
            if (element == null) {
                return "";
            }

            Elements elements = element.selectXpath(xPath);

            return !elements.isEmpty() ? elements.first().text() : "";
        } catch (Selector.SelectorParseException e) {
            log.error(String.format("Xpath is invalid: %s;", xPath), e);
        }

        return "";
    }

    public static String findAttribute(Element element, String xPath, String attribute) {
        try {
            return element.selectXpath(xPath).attr(attribute);
        } catch (Selector.SelectorParseException e) {
            log.error(String.format("Xpath is invalid: %s;", xPath), e);
            return "";
        }
    }

    public static String xmlToText(String xml) {
        if (xml == null) {
            return null;
        }
        if (xml.isEmpty()) {
            return "";
        }
        return StringEscapeUtils.unescapeXml(xml)
                .replace("<br/>", "\n")
                .replaceAll("<tr[^<>]*>", TR_REPLACEMENT)
                .replaceAll("(?ms)</(td|th)>[^<>]*<(td|th)[^<>]*>", "|")
                .replaceAll("<(td|th)[^<>]*>", "")
                .replaceAll("(<[/](p|tr|h[^<>]+)>[\\s]*)", "\n")
                .replaceAll("<[^<>]+>", " ")
                .replaceAll("(&nbsp;|\u3000| )", " ")
                .replaceAll("[ ]+", " ")
                .trim();
    }

    public static String byteToXml(byte[] content) throws IOException {
        return openAsXml(new ByteArrayInputStream(content));
    }

    public static Optional<String> tryOpenUrlAsXml(String url) {
        try {
            if(StringUtils.isBlank(url)) {
                return Optional.empty();
            }
            String asXml = RetryUtils.execute(() -> openAsXml(new URL(url).openStream()));
            return Optional.ofNullable(asXml);
        } catch (IOException e) {
            log.error("Failed to download: " + url, e);
            return Optional.empty();
        }
    }

    public static String openUrlAsXml(String url) throws IOException {
        if(StringUtils.isBlank(url)) {
            return null;
        }
        return RetryUtils.execute(() -> openAsXml(new URL(url).openStream()));
    }

    public static String openAsText(String url) throws IOException {
        return RetryUtils.execute(() -> openAsText(new URL(url).openStream()));
    }

    public static String openAsXml(InputStream inputStream) throws IOException {
        return openWithHandler(inputStream, new ToXMLContentHandler());
    }

    public static String openAsText(InputStream inputStream) throws IOException {
        return openWithHandler(inputStream, new ToTextContentHandler());
    }

    public static String openWithHandler(InputStream inputStream, ContentHandler handler) throws IOException {
        try {
            CodePageString.setMaxRecordLength(Integer.MAX_VALUE);
            AutoDetectParser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
//            ToTextContentHandler handler = new ToTextContentHandler();
//            ToXMLContentHandler handler = new ToXMLContentHandler();
//            ToHTMLContentHandler handler = new ToHTMLContentHandler();
            PDFParserConfig pdfConfig = new PDFParserConfig();
            pdfConfig.setExtractInlineImages(true);
            pdfConfig.setExtractUniqueInlineImagesOnly(false);
            ParseContext parseContext = new ParseContext();
            //        parseContext.set(TesseractOCRConfig.class, config);
            parseContext.set(PDFParserConfig.class, pdfConfig);
            parseContext.set(Parser.class, parser);
            parser.parse(inputStream, handler, metadata, parseContext);
            return handler.toString();
        } catch (IOException | TikaException | SAXException e) {
            throw new IOException(e);
        }
    }
}
