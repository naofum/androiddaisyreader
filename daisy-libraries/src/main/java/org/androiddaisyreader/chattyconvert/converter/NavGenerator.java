package org.androiddaisyreader.chattyconvert.converter;

/**
 * EPUB3ナビゲーションドキュメント（nav.xhtml）を生成する。
 */
public class NavGenerator {

    /**
     * 目次を含むnav.xhtmlを生成する。
     *
     * @param data      図書データ
     * @param bookTitle 図書タイトル
     * @return nav.xhtmlの内容
     */
    public String generate(BookData data, String bookTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" ")
                .append("xmlns:epub=\"http://www.idpf.org/2007/ops\" ")
                .append("xml:lang=\"ja\" lang=\"ja\">\n");
        sb.append("<head>\n");
        sb.append("<meta charset=\"UTF-8\"/>\n");
        sb.append("<title>目次</title>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<nav epub:type=\"toc\" id=\"toc\">\n");
        sb.append("<h1>目次</h1>\n");
        sb.append("<ol>\n");
        for (Chapter chapter : data.getChapters()) {
            String href = String.format("chapter_%04d.xhtml", chapter.getIndex());
            sb.append("<li><a href=\"").append(href).append("\">")
                    .append(escapeXml(chapter.getTitle())).append("</a></li>\n");
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
