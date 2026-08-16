package org.androiddaisyreader.aozoraconvert.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AozoraBookモデルクラスのユニットテスト。
 */
class AozoraBookTest {

    @Test
    void testConstructorAndGetters() {
        AozoraBook book = new AozoraBook(
                1245, "宮沢賢治", 46511, "銀河鉄道の夜",
                "新字新仮名", "公開中", "2010-01-01", "作品社"
        );

        assertEquals(1245, book.getAuthorId());
        assertEquals("宮沢賢治", book.getAuthorName());
        assertEquals(46511, book.getWorkId());
        assertEquals("銀河鉄道の夜", book.getWorkName());
        assertEquals("新字新仮名", book.getKanaType());
        assertEquals("公開中", book.getStatus());
        assertEquals("2010-01-01", book.getStatusDate());
        assertEquals("作品社", book.getPublisher());
    }

    @Test
    void testGetUniqueId() {
        AozoraBook book = new AozoraBook(1245, "著者", 46511, "作品", "", "", "", "");
        assertEquals("1245_46511", book.getUniqueId());
    }

    @Test
    void testGetUniqueId_differentIds() {
        AozoraBook book1 = new AozoraBook(1, "A", 100, "作品1", "", "", "", "");
        AozoraBook book2 = new AozoraBook(2, "B", 200, "作品2", "", "", "", "");
        assertNotEquals(book1.getUniqueId(), book2.getUniqueId());
    }

    @Test
    void testGetPlaceholderPath() {
        AozoraBook book = new AozoraBook(1245, "著者", 46511, "作品", "", "", "", "");
        assertEquals("aozora://1245/46511", book.getPlaceholderPath());
    }

    @Test
    void testGetPlaceholderPath_smallIds() {
        AozoraBook book = new AozoraBook(1, "著者", 1, "作品", "", "", "", "");
        assertEquals("aozora://1/1", book.getPlaceholderPath());
    }

    @Test
    void testToString_containsKeyFields() {
        AozoraBook book = new AozoraBook(1245, "宮沢賢治", 46511, "銀河鉄道の夜",
                "新字新仮名", "公開中", "2010-01-01", "出版社");

        String str = book.toString();
        assertTrue(str.contains("1245"));
        assertTrue(str.contains("46511"));
        assertTrue(str.contains("銀河鉄道の夜"));
        assertTrue(str.contains("宮沢賢治"));
    }

    @Test
    void testEmptyFields() {
        AozoraBook book = new AozoraBook(0, "", 0, "", "", "", "", "");

        assertEquals(0, book.getAuthorId());
        assertEquals("", book.getAuthorName());
        assertEquals(0, book.getWorkId());
        assertEquals("", book.getWorkName());
        assertEquals("", book.getKanaType());
        assertEquals("", book.getStatus());
        assertEquals("", book.getStatusDate());
        assertEquals("", book.getPublisher());
        assertEquals("0_0", book.getUniqueId());
        assertEquals("aozora://0/0", book.getPlaceholderPath());
    }
}
