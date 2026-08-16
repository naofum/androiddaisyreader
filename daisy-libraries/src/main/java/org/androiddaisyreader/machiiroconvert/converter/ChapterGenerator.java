package org.androiddaisyreader.machiiroconvert.converter;

import java.util.Locale;

/**
 * 広報誌の1ページ分のXHTMLコンテンツ（chapter_NNNN.xhtml）を生成する。
 * ページ画像と、そのページのテキストブロックを含む。
 */
public class ChapterGenerator {

    /**
     * 1ページ分のXHTMLを生成する。
     *
     * @param index 章番号（1始まり）
     * @param page  ページ
     * @return XHTML文字列
     */
    public String generate(int index, Page page) {
        String id = String.format(Locale.ROOT, "chapter_%04d", index);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" ")
                .append("xml:lang=\"ja\" lang=\"ja\">\n");
        sb.append("<head>\n<meta charset=\"UTF-8\"/>\n");
        sb.append("<title>").append(escapeXml(page.getTitle())).append("</title>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<h1 id=\"").append(id).append("\">").append(page.getTitle()).append("</h1>\n");

        if (page.getImageHref() != null) {
            sb.append("<div class=\"page-image\"><img src=\"")
                    .append(page.getImageHref())
                    .append("\" alt=\"").append(escapeXml(page.getTitle())).append("\"/></div>\n");
        }

//        for (String text : page.getTexts()) {
        for (int i = 0; i < page.getIds().size(); i++) {
            String text = page.getTexts().get(i);
            int pid = page.getIds().get(i);
            sb.append("<p id=\"" + String.valueOf(pid) + "\">").append(escapeMultiline(text)).append("</p>\n");
        }

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private static String escapeMultiline(String value) {
        if (value == null) {
            return "";
        }
        return escapeXml(value).replace("\n", "<br/>\n");
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
