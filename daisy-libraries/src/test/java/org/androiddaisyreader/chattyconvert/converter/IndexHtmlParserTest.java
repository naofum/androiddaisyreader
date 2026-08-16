package org.androiddaisyreader.chattyconvert.converter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexHtmlParserTest {

    private final IndexHtmlParser parser = new IndexHtmlParser();

    @Test
    void testParse_metadataAndChapters() {
        String html = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!DOCTYPE html>\n"
                + "<html lang=\"ja\">\n"
                + "<head>\n"
                + "<title>銀河鉄道の夜</title>\n"
                + "<meta name=\"title\" content=\"銀河鉄道の夜\"/>\n"
                + "<meta name=\"author\" content=\"宮沢賢治\"/>\n"
                + "<meta name=\"publisher\" content=\"サンプル出版\"/>\n"
                + "<meta name=\"publication_date\" content=\"2005/8/18\"/>\n"
                + "<meta name=\"total_time\" content=\"2:17:56.835\"/>\n"
                + "<meta name=\"uuid\" content=\"abc-123\"/>\n"
                + "<meta name=\"cover_image\" content=\"./images/cover.png\"/>\n"
                + "</head>\n"
                + "<body>\n"
                + "<section class=\"html_page section_h1 sh5_epub\" id=\"section_1_section0001_xhtml\">\n"
                + "  <div id=\"pid_00001\"><h1 class=\"section\"><span id=\"s001_00001\" class=\"read sh5index index_level_1\" title=\"銀河鉄道の夜\">本文</span></h1></div>\n"
                + "  <p class=\"section\" id=\"pid_00002\"><span id=\"s001_00002\" class=\"read\">テキスト</span></p>\n"
                + "</section>\n"
                + "<section class=\"html_page section_h1 sh5_epub\" id=\"section_2_section0002_xhtml\">\n"
                + "  <div id=\"pid_00003\"><h1 class=\"section\"><span id=\"s002_00001\" class=\"read sh5index index_level_1\" title=\"一　午後の授業\">見出し</span></h1></div>\n"
                + "</section>\n"
                + "</body>\n"
                + "</html>";

        BookData data = parser.parse(html);

        assertEquals("銀河鉄道の夜", data.getTitle());
        assertEquals("宮沢賢治", data.getAuthor());
        assertEquals("サンプル出版", data.getPublisher());
        assertEquals("2005/8/18", data.getPublicationDate());
        assertEquals("2:17:56.835", data.getTotalTime());
        assertEquals("abc-123", data.getUuid());
        assertEquals("images/cover.png", data.getCoverImage());

        assertEquals(2, data.getChapters().size());

        Chapter chapter1 = data.getChapters().get(0);
        assertEquals(1, chapter1.getIndex());
        assertEquals("section_1_section0001_xhtml", chapter1.getId());
        assertEquals("銀河鉄道の夜", chapter1.getTitle());

        Chapter chapter2 = data.getChapters().get(1);
        assertEquals(2, chapter2.getIndex());
        assertEquals("一　午後の授業", chapter2.getTitle());
    }

    @Test
    void testParse_chapterTitleFallback() {
        String html = "<html><body>"
                + "<section class=\"html_page sh5_epub\"><h1>フォールバック見出し</h1></section>"
                + "</body></html>";

        BookData data = parser.parse(html);

        assertEquals("フォールバック見出し", data.getChapters().get(0).getTitle());
    }

    @Test
    void testParse_noChapters() {
        BookData data = parser.parse("<html><body></body></html>");
        assertTrue(data.getChapters().isEmpty());
        assertNull(data.getUuid());
        assertNotNull(data.getTitle());
    }
}
