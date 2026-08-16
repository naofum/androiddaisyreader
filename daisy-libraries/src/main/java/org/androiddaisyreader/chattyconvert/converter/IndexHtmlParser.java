package org.androiddaisyreader.chattyconvert.converter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;

/**
 * index.html を解析して、メタデータと章（section要素）の一覧を抽出する。
 * <p>
 * jsoupでHTMLを解析し、XML直列化に必要なOutputSettingsを設定しておく。
 */
public class IndexHtmlParser {

    /**
     * index.htmlの内容を解析してBookDataを構築する。
     *
     * @param indexHtml index.htmlの内容
     * @return 抽出された図書データ
     */
    public BookData parse(String indexHtml) {
        Document doc = Jsoup.parse(indexHtml);
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        doc.outputSettings().escapeMode(Entities.EscapeMode.xhtml);
        doc.outputSettings().charset(java.nio.charset.StandardCharsets.UTF_8);

        BookData data = new BookData();
        data.setTitle(firstNonBlank(meta(doc, "title"), doc.title()));
        data.setAuthor(meta(doc, "author"));
        data.setPublisher(meta(doc, "publisher"));
        data.setProducer(meta(doc, "producer"));
        data.setNarrator(meta(doc, "narrator"));
        data.setPublicationDate(meta(doc, "publication_date"));
        data.setTotalTime(meta(doc, "total_time"));
        data.setUuid(meta(doc, "uuid"));
        data.setCoverImage(normalizePath(meta(doc, "cover_image")));

        Elements sections = doc.select("section.html_page");
        for (int i = 0; i < sections.size(); i++) {
            Element section = sections.get(i);
            int index = i + 1;
            String id = section.id();
            String title = extractChapterTitle(section, index);
            data.addChapter(new Chapter(index, id, title, section));
        }
        return data;
    }

    private static String extractChapterTitle(Element section, int index) {
        Element heading = section.selectFirst("span.sh5index");
        if (heading != null) {
            String title = heading.attr("title");
            if (isNotBlank(title)) {
                return title;
            }
            String text = heading.text();
            if (isNotBlank(text)) {
                return text;
            }
        }
        Element h1 = section.selectFirst("h1");
        if (h1 != null && isNotBlank(h1.text())) {
            return h1.text();
        }
        return "第" + index + "章";
    }

    private static String meta(Document doc, String name) {
        Element meta = doc.selectFirst("meta[name=" + name + "]");
        if (meta == null) {
            return null;
        }
        return meta.attr("content");
    }

    private static String firstNonBlank(String first, String second) {
        return isNotBlank(first) ? first : second;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }
}
