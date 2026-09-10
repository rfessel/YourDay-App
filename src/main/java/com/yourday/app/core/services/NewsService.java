package com.yourday.app.core.services;

import com.yourday.app.core.model.NewsItem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Baixa e interpreta feeds RSS 2.0 e Atom usando apenas o XML do JDK. */
public final class NewsService {

    private static final int MAX_ITEMS = 15;

    private NewsService() {
    }

    /** Busca um feed (chamar em thread de fundo). */
    public static List<NewsItem> fetch(String url, String source) throws Exception {
        String xml = HttpSupport.get(url);
        List<NewsItem> items = parse(xml, source);
        if (items.size() > MAX_ITEMS) {
            items = items.subList(0, MAX_ITEMS);
        }
        return items;
    }

    static List<NewsItem> parse(String xml, String source) {
        List<NewsItem> out = new ArrayList<>();
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            // Detecta Atom pelo elemento raiz.
            Element root = doc.getDocumentElement();
            if (root != null && root.getLocalName() != null && root.getLocalName().equalsIgnoreCase("feed")) {
                parseAtom(doc, source, out);
            } else {
                parseRss(doc, source, out);
            }
        } catch (Exception ignore) {
            // feed malformado: retorna lista parcial/vazia
        }
        return out;
    }

    private static void parseRss(Document doc, String source, List<NewsItem> out) {
        NodeList items = doc.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            NewsItem n = new NewsItem();
            n.sourceName = source;
            n.title = text(item, "title");
            n.link = text(item, "link");
            n.summary = stripHtml(text(item, "description"));
            n.pubDate = parseRfc822(text(item, "pubDate"));
            if (n.title.isEmpty()) {
                continue;
            }
            out.add(n);
        }
    }

    private static void parseAtom(Document doc, String source, List<NewsItem> out) {
        NodeList entries = doc.getElementsByTagName("entry");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            NewsItem n = new NewsItem();
            n.sourceName = source;
            n.title = text(entry, "title");
            n.link = atomLink(entry);
            n.summary = stripHtml(firstText(entry, "summary", "content"));
            String updated = firstText(entry, "updated", "published");
            n.pubDate = parseIsoDate(updated);
            if (n.title.isEmpty()) {
                continue;
            }
            out.add(n);
        }
    }

    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n.getParentNode() == parent) {
                return n.getTextContent().trim();
            }
        }
        return "";
    }

    private static String firstText(Element parent, String... tags) {
        for (String t : tags) {
            String v = text(parent, t);
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private static String atomLink(Element entry) {
        NodeList links = entry.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String rel = link.getAttribute("rel");
            if (rel.isEmpty() || rel.equals("alternate")) {
                return link.getAttribute("href");
            }
        }
        return "";
    }

    private static long parseRfc822(String v) {
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return OffsetDateTime.parse(v.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            // "Fri, 04 Mar 2022 12:00:00 +0000" -> normaliza para RFC1123
            try {
                String s = v.trim().replaceFirst(" ([+-]\\d{4})$", " GMT$1");
                return OffsetDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli();
            } catch (DateTimeParseException ignore) {
                return 0;
            }
        }
    }

    private static long parseIsoDate(String v) {
        if (v == null || v.isBlank()) {
            return 0;
        }
        try {
            return Instant.parse(v.trim()).toEpochMilli();
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(v.trim()).toInstant().toEpochMilli();
            } catch (Exception e2) {
                return 0;
            }
        }
    }

    static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&#39;|&apos;", "'")
                .replaceAll("&quot;", "\"")
                .replaceAll("\\s+", " ").trim();
    }
}