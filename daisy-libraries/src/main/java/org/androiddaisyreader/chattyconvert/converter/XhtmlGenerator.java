package org.androiddaisyreader.chattyconvert.converter;

import java.util.List;

/**
 * 章のXHTMLコンテンツ（EPUB3用）を生成する。
 * 元のsection要素のHTMLを保持しつつ、EPUBに必要なヘッダを付与する。
 */
public class XhtmlGenerator {

    /**
     * 章のXHTMLドキュメントを生成する。
     *
     * @param chapter   章
     * @param cssFiles  OEBPSルートからの相対パスで表したCSSファイル一覧
     * @return XHTML文字列
     */
    public String generate(Chapter chapter, List<String> cssFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" ")
                .append("xml:lang=\"ja\" lang=\"ja\">\n");
        sb.append("<head>\n");
        sb.append("<meta charset=\"UTF-8\"/>\n");
        sb.append("<title>").append(escapeXml(chapter.getTitle())).append("</title>\n");
        for (String css : cssFiles) {
            sb.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"")
                    .append(css).append("\"/>\n");
        }
        sb.append("</head>\n<body>\n");
        sb.append(chapter.getSectionElement().outerHtml());
        sb.append("\n</body>\n</html>");
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
