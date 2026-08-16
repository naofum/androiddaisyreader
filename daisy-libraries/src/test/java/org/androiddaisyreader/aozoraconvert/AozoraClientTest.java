package org.androiddaisyreader.aozoraconvert;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.androiddaisyreader.aozoraconvert.model.AozoraBook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AozoraClientのユニットテスト。
 * CSVパース・外字変換ロジックをテストする。
 */
class AozoraClientTest {

    private AozoraClient client;

    @BeforeEach
    void setUp() {
        client = new AozoraClient();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    // ========== CSVパースのテスト ==========

    @Test
    void testParseCatalogCsv_normalData() throws IOException {
        String csv = "人物ID,著者名,作品ID,作品名,仮名遣い種別,翻訳者名等,入力者名,校正者名,状態,状態の開始日,底本名,出版社名,その他\n"
                + "1245,宮沢賢治,46511,銀河鉄道の夜,新字新仮名,,入力太郎,校正次郎,公開中,2010-01-01,日本文学全集,作品社,other\n"
                + "148,夏目漱石,752,坊っちゃん,新字新仮名,,入力者A,校正者B,公開中,2005-03-15,漱石全集,岩波書店,extra\n";

        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<AozoraBook> books = client.parseCatalogCsv(is);

        assertEquals(2, books.size());

        AozoraBook book1 = books.get(0);
        assertEquals(1245, book1.getAuthorId());
        assertEquals("宮沢賢治", book1.getAuthorName());
        assertEquals(46511, book1.getWorkId());
        assertEquals("銀河鉄道の夜", book1.getWorkName());
        assertEquals("新字新仮名", book1.getKanaType());
        assertEquals("公開中", book1.getStatus());
        assertEquals("2010-01-01", book1.getStatusDate());
        assertEquals("作品社", book1.getPublisher());

        AozoraBook book2 = books.get(1);
        assertEquals(148, book2.getAuthorId());
        assertEquals("夏目漱石", book2.getAuthorName());
        assertEquals(752, book2.getWorkId());
        assertEquals("坊っちゃん", book2.getWorkName());
    }

    @Test
    void testParseCatalogCsv_emptyContent() throws IOException {
        String csv = "";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<AozoraBook> books = client.parseCatalogCsv(is);
        assertTrue(books.isEmpty());
    }

    @Test
    void testParseCatalogCsv_headerOnly() throws IOException {
        String csv = "人物ID,著者名,作品ID,作品名,仮名遣い種別,翻訳者名等,入力者名,校正者名,状態,状態の開始日,底本名,出版社名\n";
        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<AozoraBook> books = client.parseCatalogCsv(is);
        assertTrue(books.isEmpty());
    }

    @Test
    void testParseCatalogCsv_tooFewFields() throws IOException {
        // 12フィールド未満の行はスキップされる
        String csv = "人物ID,著者名,作品ID,作品名,仮名遣い種別,翻訳者名等,入力者名,校正者名,状態,状態の開始日,底本名,出版社名\n"
                + "1245,宮沢賢治,46511\n"; // 3フィールドしかない

        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<AozoraBook> books = client.parseCatalogCsv(is);
        assertTrue(books.isEmpty());
    }

    @Test
    void testParseCatalogCsv_invalidAuthorId() throws IOException {
        // 人物IDが数値でない行はスキップされる
        String csv = "人物ID,著者名,作品ID,作品名,仮名遣い種別,翻訳者名等,入力者名,校正者名,状態,状態の開始日,底本名,出版社名\n"
                + "invalid,著者名,46511,作品名,新字新仮名,,入力者,校正者,公開中,2010-01-01,底本名,出版社名\n";

        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<AozoraBook> books = client.parseCatalogCsv(is);
        assertTrue(books.isEmpty());
    }

    @Test
    void testParseCatalogCsv_quotedFieldWithComma() throws IOException {
        // カンマ入りフィールドがダブルクォートで囲まれている場合
        String csv = "人物ID,著者名,作品ID,作品名,仮名遣い種別,翻訳者名等,入力者名,校正者名,状態,状態の開始日,底本名,出版社名\n"
                + "100,\"著者, 名前\",200,\"作品名, サブ\",新字新仮名,,入力者,校正者,公開中,2020-01-01,底本名,出版社名\n";

        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<AozoraBook> books = client.parseCatalogCsv(is);

        assertEquals(1, books.size());
        assertEquals("著者, 名前", books.get(0).getAuthorName());
        assertEquals("作品名, サブ", books.get(0).getWorkName());
    }

    @Test
    void testParseCatalogCsv_escapedQuote() throws IOException {
        // ダブルクォート内のエスケープされたダブルクォート
        String csv = "人物ID,著者名,作品ID,作品名,仮名遣い種別,翻訳者名等,入力者名,校正者名,状態,状態の開始日,底本名,出版社名\n"
                + "100,\"著者\"\"名前\"\"\",200,作品名,新字新仮名,,入力者,校正者,公開中,2020-01-01,底本名,出版社名\n";

        InputStream is = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        List<AozoraBook> books = client.parseCatalogCsv(is);

        assertEquals(1, books.size());
        assertEquals("著者\"名前\"", books.get(0).getAuthorName());
    }

    // ========== parseCsvLineのテスト ==========

    @Test
    void testParseCsvLine_simple() {
        String[] fields = client.parseCsvLine("a,b,c,d");
        assertArrayEquals(new String[]{"a", "b", "c", "d"}, fields);
    }

    @Test
    void testParseCsvLine_quotedComma() {
        String[] fields = client.parseCsvLine("\"a,b\",c,d");
        assertEquals("a,b", fields[0]);
        assertEquals("c", fields[1]);
        assertEquals("d", fields[2]);
    }

    @Test
    void testParseCsvLine_escapedQuotes() {
        String[] fields = client.parseCsvLine("\"a\"\"b\"\"\",c");
        assertEquals("a\"b\"", fields[0]);
        assertEquals("c", fields[1]);
    }

    @Test
    void testParseCsvLine_emptyFields() {
        String[] fields = client.parseCsvLine("a,,c,");
        assertEquals(4, fields.length);
        assertEquals("a", fields[0]);
        assertEquals("", fields[1]);
        assertEquals("c", fields[2]);
        assertEquals("", fields[3]);
    }

    @Test
    void testParseCsvLine_singleField() {
        String[] fields = client.parseCsvLine("hello");
        assertEquals(1, fields.length);
        assertEquals("hello", fields[0]);
    }

    // ========== 外字変換のテスト ==========

    @Test
    void testConvertGaijiImages_convertKnown() {
        String html = "<p>テスト<img src=\"../gaiji/1-14-01.png\" alt=\"\" class=\"gaiji\" />テスト</p>";
        String result = client.convertGaijiImages(html);
        assertTrue(result.contains("俱"));
        assertFalse(result.contains("<img"));
    }

    @Test
    void testConvertGaijiImages_preserveUnknown() {
        String html = "<p>テスト<img src=\"9-99-99.png\" alt=\"\" class=\"gaiji\" />テスト</p>";
        String result = client.convertGaijiImages(html);
        // 変換不可の外字はそのまま画像タグとして残る
        assertTrue(result.contains("<img"));
        assertTrue(result.contains("class=\"gaiji\""));
    }

    @Test
    void testConvertGaijiImages_classBeforeSrc() {
        // class属性がsrc属性の前にあるパターン
        String html = "<p><img class=\"gaiji\" src=\"1-14-03.png\" alt=\"\" /></p>";
        String result = client.convertGaijiImages(html);
        assertTrue(result.contains("㐂"));
        assertFalse(result.contains("<img"));
    }

    @Test
    void testConvertGaijiImages_multipleGaiji() {
        String html = "<p><img src=\"1-14-01.png\" class=\"gaiji\" />"
                + "<img src=\"1-14-03.png\" class=\"gaiji\" /></p>";
        String result = client.convertGaijiImages(html);
        assertTrue(result.contains("俱"));
        assertTrue(result.contains("㐂"));
        assertFalse(result.contains("<img"));
    }

    @Test
    void testConvertGaijiImages_noGaiji() {
        String html = "<p>普通のテキスト</p><img src=\"photo.jpg\" class=\"normal\" />";
        String result = client.convertGaijiImages(html);
        assertEquals(html, result);
    }

    @Test
    void testConvertGaijiImages_mixedGaijiAndNormalImg() {
        String html = "<p><img src=\"photo.jpg\" class=\"image\" />"
                + "<img src=\"1-14-04.png\" alt=\"\" class=\"gaiji\" /></p>";
        String result = client.convertGaijiImages(html);
        // 通常画像は残り、外字画像は変換される
        assertTrue(result.contains("photo.jpg"));
        assertTrue(result.contains("丨"));
    }

    @Test
    void testConvertGaijiImages_pathWithSubdirectory() {
        // パスにサブディレクトリがある場合でもファイル名で変換される
        String html = "<img src=\"../gaiji/1-14-05.png\" alt=\"\" class=\"gaiji\" />";
        String result = client.convertGaijiImages(html);
        assertTrue(result.contains("丯"));
        assertFalse(result.contains("<img"));
    }
}
