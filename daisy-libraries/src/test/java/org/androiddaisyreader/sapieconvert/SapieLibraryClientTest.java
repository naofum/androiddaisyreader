package org.androiddaisyreader.sapieconvert;

import org.androiddaisyreader.sapieconvert.model.Book;
import org.androiddaisyreader.sapieconvert.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SapieLibraryClientのユニットテスト。
 * 検索結果HTMLのパースロジックを検証する。
 */
class SapieLibraryClientTest {

    private final SapieLibraryClient client = new SapieLibraryClient("testuser", "testpass");

    // ========== 検索結果パースのテスト ==========

    @Test
    void testParseSearchResult_normalHtml() {
        String html = "<html><body>"
                + "<strong>該当件数：104件<br>1ページに50件まで表示します</strong>"
                + "<table class=\"FULL\"><tbody>"
                + row("4828134", "テスト図書1", "著者A", "音声デイジー", "3時間19分", "2016年", "府立図書館")
                + row("4685244", "テスト図書2", "著者B", "テキストデイジー", "8時間17分", "2012年", "三重県立図書館")
                + "</tbody></table>"
                + "<form method=\"post\" action=\"CN1MN1\">"
                + "  <input type=\"hidden\" name=\"S00221\" value=\"139348240\">"
                + "  <input type=\"hidden\" name=\"RTNTME\" value=\"121048300\">"
                + "</form>"
                + "</body></html>";

        SearchResult result = client.parseSearchResult(html, 1);

        assertEquals(104, result.getTotalCount());
        assertTrue(result.isHasNext());
        assertEquals("139348240", result.getSessionToken());

        List<Book> books = result.getBooks();
        assertEquals(2, books.size());

        assertEquals("4828134", books.get(0).getId());
        assertEquals("テスト図書1", books.get(0).getTitle());
        assertEquals("著者A", books.get(0).getAuthor());
        assertEquals("音声デイジー", books.get(0).getType());
        assertEquals("3時間19分", books.get(0).getTime());
        assertEquals("2016年", books.get(0).getPublisher());
        assertEquals("府立図書館", books.get(0).getLibrary());

        // ダウンロードフォームが抽出され、S00224 が bookId と一致すること
        java.util.Map<String, String> form = books.get(0).getDownloadForm();
        assertNotNull(form);
        assertEquals("J31DWN21", form.get("S00101"));
        assertEquals("4828134", form.get("S00224"));
        assertEquals("ASaTW4QRir2", form.get("S00102"));
        assertEquals("1", form.get("S00215"));
        // submit ボタンは除外されること
        assertFalse(form.containsValue("ダウン"));
    }

    @Test
    void testParseSearchResult_lastPage_noNext() {
        String html = "<html><body>"
                + "<strong>該当件数：50件<br>1ページに50件まで表示します</strong>"
                + "<table class=\"FULL\"><tbody>"
                + row("1111111", "図書", "著者", "図書", "1時間", "2020年", "図書館")
                + "</tbody></table>"
                + "<input type=\"hidden\" name=\"S00221\" value=\"123\">"
                + "<input type=\"hidden\" name=\"RTNTME\" value=\"456\">"
                + "</body></html>";

        SearchResult result = client.parseSearchResult(html, 1);

        assertEquals(50, result.getTotalCount());
        assertFalse(result.isHasNext());
    }

    @Test
    void testParseSearchResult_noResults() {
        String html = "<html><body>"
                + "<strong>該当件数：0件</strong>"
                + "<table class=\"FULL\"><tbody></tbody></table>"
                + "</body></html>";

        SearchResult result = client.parseSearchResult(html, 1);

        assertEquals(0, result.getTotalCount());
        assertFalse(result.isHasNext());
        assertTrue(result.getBooks().isEmpty());
    }

    private String row(String bookId, String title, String author, String type,
                       String time, String publisher, String library) {
        return "<tr>"
                + "<td class=\"RIGHT\">1</td>"
                + "<td><a href=\"CN1MN1?S00101=J00DTL14&amp;S00102=ASaTW4QRir2&amp;S00103=7op3EJ!0MN"
                + "&amp;S00221=139348240&amp;S00222=" + bookId + "&amp;RTNTME=121048300\">"
                + title + "</a></td>"
                + "<td>" + author + "</td>"
                + "<td>図書<span class=\"BR2\">" + type + "</span></td>"
                + "<td>" + time + "</td>"
                + "<td>" + publisher + "</td>"
                + "<td>" + library + "</td>"
                + "<td><form method=\"post\" action=\"https://cntdwn.sapie.or.jp/download/download.aspx\">"
                + "<input type=\"hidden\" name=\"S00101\" value=\"J31DWN21\">"
                + "<input type=\"hidden\" name=\"S00102\" value=\"ASaTW4QRir2\">"
                + "<input type=\"hidden\" name=\"S00103\" value=\"7op3EJ!0MN\">"
                + "<input type=\"hidden\" name=\"RTNTME\" value=\"061249381\">"
                + "<input type=\"hidden\" name=\"S00202\" value=\"22\">"
                + "<input type=\"hidden\" name=\"S00215\" value=\"1\">"
                + "<input type=\"hidden\" name=\"S00224\" value=\"" + bookId + "\">"
                + "<input type=\"hidden\" name=\"S00263\" value=\"J01LST11\">"
                + "<input type=\"submit\" value=\"ダウン\">"
                + "</form></td>"
                + "</tr>";
    }
}
