package org.androiddaisyreader.chattyconvert;

import okhttp3.mockwebserver.MockWebServer;
import org.androiddaisyreader.chattyconvert.exception.ChattyLibraryException;
import org.androiddaisyreader.chattyconvert.model.Book;
import org.androiddaisyreader.chattyconvert.model.BookDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChattyLibraryClientのユニットテスト。
 * MockWebServerを使用してHTTP通信をモックする。
 */
class ChattyLibraryClientTest {

    private MockWebServer server;
    private ChattyLibraryClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        client = new ChattyLibraryClient("testuser", "testpass");

        // リフレクションでBASE_URLを差し替え
        Field baseUrlField = ChattyLibraryClient.class.getDeclaredField("BASE_URL");
        // static finalの場合はリフレクションでは変更できないため、テスト用にURLを組み立てる
        // 代わりにテスト内でサーバーURLを使ってパスを構成する
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        server.shutdown();
    }

    // ========== CSVパースのテスト ==========

    @Test
    void testParseCsv_normalData() throws ChattyLibraryException {
        String csv = "図書ID,図書名,著者名,レイアウト,登録日時\n"
                + "123,テスト図書,テスト著者,DAISY,2024-01-01\n"
                + "456,\"半角 スペース入り\",著者2,PDF,2024-02-01\n";

        List<Book> books = client.parseCsv(csv);

        assertEquals(2, books.size());

        assertEquals(123, books.get(0).getId());
        assertEquals("テスト図書", books.get(0).getTitle());
        assertEquals("テスト著者", books.get(0).getAuthor());
        assertEquals("DAISY", books.get(0).getLayout());
        assertEquals("2024-01-01", books.get(0).getRegisteredDate());

        assertEquals(456, books.get(1).getId());
        assertEquals("半角 スペース入り", books.get(1).getTitle());
        assertEquals("著者2", books.get(1).getAuthor());
    }

    @Test
    void testParseCsv_emptyContent() throws ChattyLibraryException {
        String csv = "";
        List<Book> books = client.parseCsv(csv);
        assertTrue(books.isEmpty());
    }

    @Test
    void testParseCsv_headerOnly() throws ChattyLibraryException {
        String csv = "図書ID,図書名,著者名,レイアウト,登録日時\n";
        List<Book> books = client.parseCsv(csv);
        assertTrue(books.isEmpty());
    }

    @Test
    void testParseCsv_quotedFieldWithComma() throws ChattyLibraryException {
        String csv = "図書ID,図書名,著者名,レイアウト,登録日時\n"
                + "789,\"タイトル,カンマ入り\",著者名,DAISY,2024-03-01\n";

        List<Book> books = client.parseCsv(csv);

        assertEquals(1, books.size());
        assertEquals("タイトル,カンマ入り", books.get(0).getTitle());
    }

    @Test
    void testParseCsv_escapedQuote() throws ChattyLibraryException {
        String csv = "図書ID,図書名,著者名,レイアウト,登録日時\n"
                + "101,\"タイトル\"\"引用\"\"\",著者,DAISY,2024-04-01\n";

        List<Book> books = client.parseCsv(csv);

        assertEquals(1, books.size());
        assertEquals("タイトル\"引用\"", books.get(0).getTitle());
    }

    // ========== 図書詳細パースのテスト ==========

    @Test
    void testParseBookDetail_normalHtml() {
        String html = "<html><body>"
                + "<ul class=\"bookInfoList\">"
                + "  <li class=\"bookInfoListItem bookAuther\">"
                + "    <p class=\"heading\">著者名</p>"
                + "    <p class=\"contents\">山田太郎</p>"
                + "  </li>"
                + "  <li class=\"bookInfoListItem bookPublisher\">"
                + "    <p class=\"heading\">出版社名</p>"
                + "    <p class=\"contents\">テスト出版</p>"
                + "  </li>"
                + "  <li class=\"bookInfoListItem bookDescription\">"
                + "    <p class=\"heading\">説明文</p>"
                + "    <p class=\"contents\">テスト説明文</p>"
                + "  </li>"
                + "  <li class=\"bookInfoListItem bookPage\">"
                + "    <p class=\"heading\">ページ数</p>"
                + "    <p class=\"contents\">100</p>"
                + "  </li>"
                + "  <li class=\"bookInfoListItem bookCopyrigh\">"
                + "    <p class=\"heading\">著作権</p>"
                + "    <p class=\"contents\">CC BY</p>"
                + "  </li>"
                + "  <li class=\"bookInfoListItem bookISBN\">"
                + "    <p class=\"heading\">ISBN</p>"
                + "    <p class=\"contents\">978-4-123456-78-9</p>"
                + "  </li>"
                + "  <li class=\"bookInfoListItem bookIssueDate\">"
                + "    <p class=\"heading\">図書発行日</p>"
                + "    <p class=\"contents\">2024-01-15</p>"
                + "  </li>"
                + "  <li class=\"bookInfoListItem\">"
                + "    <p class=\"heading\">製作者名</p>"
                + "    <p class=\"contents\">製作者A</p>"
                + "  </li>"
                + "</ul>"
                + "<form action=\"https://chattylib.com/library/books/123/read\" method=\"POST\">"
                + "  <input type=\"hidden\" name=\"_method\" value=\"POST\">"
                + "  <input type=\"hidden\" name=\"_token\" value=\"test-csrf-token\">"
                + "  <button type=\"submit\" class=\"ChattyBoxBtn\">MY本箱に<br>入れる</button>"
                + "</form>"
                + "</body></html>";

        BookDetail detail = client.parseBookDetail(123, html);

        assertEquals(123, detail.getBookId());
        assertEquals("山田太郎", detail.getAuthor());
        assertEquals("テスト出版", detail.getPublisher());
        assertEquals("テスト説明文", detail.getDescription());
        assertEquals("100", detail.getPages());
        assertEquals("CC BY", detail.getCopyright());
        assertEquals("978-4-123456-78-9", detail.getIsbn());
        assertEquals("2024-01-15", detail.getIssueDate());
        assertEquals("製作者A", detail.getProducer());
        assertFalse(detail.isInMyBox());
        assertEquals("test-csrf-token", detail.getToken());
    }

    @Test
    void testParseBookDetail_alreadyInMyBox() {
        String html = "<html><body>"
                + "<ul class=\"bookInfoList\">"
                + "  <li class=\"bookInfoListItem bookAuther\">"
                + "    <p class=\"heading\">著者名</p>"
                + "    <p class=\"contents\">著者X</p>"
                + "  </li>"
                + "</ul>"
                + "<div class=\"\">"
                + "  <button type=\"submit\" class=\"ChattyBoxBtn\" disabled>MY本箱に<br>登録済み</button>"
                + "</div>"
                + "</body></html>";

        BookDetail detail = client.parseBookDetail(456, html);

        assertEquals(456, detail.getBookId());
        assertEquals("著者X", detail.getAuthor());
        assertTrue(detail.isInMyBox());
        assertNull(detail.getToken());
    }

    // ========== MY本箱エントリ検索のテスト ==========

    @Test
    void testFindMyBoxEntry_found() {
        String html = "<html><body>"
                + "<section class=\"mainArea\" id=\"mainArea\">"
                + "<div class=\"eachBook\">"
                + "  <div class=\"bookImg\">"
                + "    <img src=\"/library/storage/cover/123/cover.png\" id=\"top_image_24529\">"
                + "  </div>"
                + "  <button class=\"app_img\" data-book-id=\"24529\" "
                + "    data-book-read-url=\"https://chattylib.com/chattybox/books/read/24529\">"
                + "  </button>"
                + "  <form action=\"https://chattylib.com/chattybox/books/delete/24529\" method=\"POST\">"
                + "    <input type=\"hidden\" name=\"_token\" value=\"delete-token-abc\">"
                + "  </form>"
                + "</div>"
                + "</section>"
                + "</body></html>";

        ChattyLibraryClient.MyBoxEntry entry = client.findMyBoxEntry(html, 123);

        assertNotNull(entry);
        assertEquals(24529, entry.myBoxBookId);
        assertEquals("https://chattylib.com/chattybox/books/read/24529", entry.readUrl);
        assertEquals("delete-token-abc", entry.deleteToken);
    }

    @Test
    void testFindMyBoxEntry_notFound() {
        String html = "<html><body>"
                + "<section class=\"mainArea\" id=\"mainArea\">"
                + "<div class=\"eachBook\">"
                + "  <div class=\"bookImg\">"
                + "    <img src=\"/library/storage/cover/999/cover.png\" id=\"top_image_11111\">"
                + "  </div>"
                + "  <button class=\"app_img\" data-book-id=\"11111\" "
                + "    data-book-read-url=\"https://chattylib.com/chattybox/books/read/11111\">"
                + "  </button>"
                + "</div>"
                + "</section>"
                + "</body></html>";

        ChattyLibraryClient.MyBoxEntry entry = client.findMyBoxEntry(html, 123);

        assertNull(entry);
    }

    // ========== smil.js音声パス抽出のテスト ==========

    @Test
    void testExtractAudioSrcs_srcAndRefMixed() {
        String smil = "var cAudioItems = {\n"
                + "\ts001_00001:{src:'./sounds/sound00001.mp3',begin:0.030,end:2.820},\n"
                + "\ts001_00002:{ref:'s001_00001',begin:2.820,end:6.092},\n"
                + "\ts002_00001:{src:'./sounds/sound00002.mp3',begin:0.030,end:0.880},\n"
                + "\ts002_00002:{ref:'s002_00001',begin:0.880,end:1.540}\n"
                + "} ;";

        Set<String> srcs = client.extractAudioSrcs(smil);

        assertEquals(2, srcs.size());
        assertTrue(srcs.contains("./sounds/sound00001.mp3"));
        assertTrue(srcs.contains("./sounds/sound00002.mp3"));
    }

    @Test
    void testExtractAudioSrcs_duplicatesAreRemoved() {
        String smil = "var cAudioItems = {\n"
                + "\ts001_00001:{src:'./sounds/sound00001.mp3',begin:0.030,end:2.820},\n"
                + "\ts001_00002:{ref:'s001_00001',begin:2.820,end:6.092}\n"
                + "} ;";

        Set<String> srcs = client.extractAudioSrcs(smil);

        assertEquals(1, srcs.size());
        assertTrue(srcs.contains("./sounds/sound00001.mp3"));
    }

    @Test
    void testExtractAudioSrcs_noSrcEntries() {
        String smil = "var cAudioItems = {\n"
                + "\ts001_00002:{ref:'s001_00001',begin:2.820,end:6.092}\n"
                + "} ;";

        assertTrue(client.extractAudioSrcs(smil).isEmpty());
    }

    @Test
    void testExtractAudioSrcs_nullOrEmpty() {
        assertTrue(client.extractAudioSrcs(null).isEmpty());
        assertTrue(client.extractAudioSrcs("").isEmpty());
        assertTrue(client.extractAudioSrcs("var cAudioItems = {} ;").isEmpty());
    }
}
