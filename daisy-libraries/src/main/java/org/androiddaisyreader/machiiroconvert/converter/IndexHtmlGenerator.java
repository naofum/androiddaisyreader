package org.androiddaisyreader.machiiroconvert.converter;

import org.androiddaisyreader.machiiroconvert.model.Magazine;

import java.util.List;
import java.util.Locale;

/**
 * 広報誌の目次ページ（index.html）を生成する。
 * 誌名・号タイトルと、各ページへのリンク一覧を出力する。
 */
public class IndexHtmlGenerator {

    /**
     * index.html の内容を生成する。
     *
     * @param magazine 広報誌データ
     * @param pages    ページ一覧
     * @return index.html の内容
     */
    public String generate(Magazine magazine, List<Page> pages) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" ")
                .append("xml:lang=\"ja\" lang=\"ja\">\n");
        sb.append("<head>\n<meta charset=\"UTF-8\"/>\n");
        sb.append("<title>").append(escapeXml(bookTitle(magazine))).append("</title>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<h1>").append(escapeXml(bookTitle(magazine))).append("</h1>\n");
        sb.append("<h2>目次</h2>\n");
        sb.append("<ol>\n");
        int index = 1;
        for (Page page : pages) {
            String href = String.format(Locale.ROOT, "chapter_%04d.xhtml", index++);
            sb.append("<li><a href=\"").append(href).append("\">")
                    .append(escapeXml(page.getTitle())).append("</a></li>\n");
        }
        sb.append("</ol>\n");
        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private static String bookTitle(Magazine magazine) {
        StringBuilder sb = new StringBuilder();
        if (isNotBlank(magazine.getMagazineTitle())) {
            sb.append(magazine.getMagazineTitle().trim());
        }
        if (isNotBlank(magazine.getTitle())) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(magazine.getTitle().trim());
        }
        return sb.length() > 0 ? sb.toString() : "広報誌";
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
