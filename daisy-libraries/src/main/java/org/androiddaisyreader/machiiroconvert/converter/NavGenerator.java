package org.androiddaisyreader.machiiroconvert.converter;

import java.util.List;
import java.util.Locale;

/**
 * EPUB3ナビゲーションドキュメント（nav.xhtml）を生成する。
 */
public class NavGenerator {

    /**
     * 目次を含むnav.xhtmlを生成する。
     *
     * @param bookTitle 図書タイトル
     * @param pages     ページ一覧
     * @return nav.xhtmlの内容
     */
    public String generate(String bookTitle, List<Page> pages) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" ")
                .append("xml:lang=\"ja\" lang=\"ja\">\n");
        sb.append("<head>\n<meta charset=\"UTF-8\"/>\n");
        sb.append("<title>目次</title>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<nav epub:type=\"toc\" id=\"toc\">\n");
        sb.append("<h1>目次</h1>\n");
        sb.append("<ol>\n");
        int index = 1;
        for (Page page : pages) {
            String href = String.format(Locale.ROOT, "chapter_%04d.xhtml", index++);
            sb.append("<li><a href=\"").append(href).append("\">")
                    .append(escapeXml(page.getTitle())).append("</a></li>\n");
        }
        sb.append("</ol>\n");
        sb.append("</nav>\n");
        sb.append("</body>\n</html>");
        return sb.toString();
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
}
