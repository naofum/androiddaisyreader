package org.androiddaisyreader.aozoraconvert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConvertUtilのユニットテスト。
 * 外字画像ファイル名→UTF-8文字変換のテスト。
 */
class ConvertUtilTest {

    @Test
    void testConvert_knownGaiji_withoutExtension() {
        // 1-14-01 → 俱
        assertEquals("俱", ConvertUtil.convert("1-14-01"));
    }

    @Test
    void testConvert_knownGaiji_withPngExtension() {
        // .png 拡張子付きでも変換可能
        assertEquals("俱", ConvertUtil.convert("1-14-01.png"));
    }

    @Test
    void testConvert_knownGaiji_variousEntries() {
        // いくつかの既知エントリをテスト
        assertNotNull(ConvertUtil.convert("1-14-03"));
        assertNotNull(ConvertUtil.convert("1-14-04"));
        assertNotNull(ConvertUtil.convert("1-14-48"));
    }

    @Test
    void testConvert_knownGaiji_secondPlane() {
        // 第2面のエントリ
        assertNotNull(ConvertUtil.convert("2-94-85"));
    }

    @Test
    void testConvert_unknownGaiji() {
        // 存在しないコードはnullを返す
        assertNull(ConvertUtil.convert("9-99-99"));
    }

    @Test
    void testConvert_unknownGaiji_withPng() {
        assertNull(ConvertUtil.convert("9-99-99.png"));
    }

    @Test
    void testConvert_null() {
        assertNull(ConvertUtil.convert(null));
    }

    @Test
    void testConvert_emptyString() {
        assertNull(ConvertUtil.convert(""));
    }

    @Test
    void testConvert_invalidFormat() {
        // マップに存在しない任意文字列
        assertNull(ConvertUtil.convert("invalid"));
        assertNull(ConvertUtil.convert("abc.png"));
    }

    @Test
    void testConvert_surrogatePair() {
        // 1-14-02 はサロゲートペア文字 "\uD840\uDC0B"
        String result = ConvertUtil.convert("1-14-02");
        assertNotNull(result);
        assertEquals(2, result.length()); // サロゲートペアは2 char
        assertEquals("\uD840\uDC0B", result);
    }

    @Test
    void testConvert_pngExtensionStripping() {
        // "1-14-05.png" → 拡張子除去後に "1-14-05" で検索される
        String withExt = ConvertUtil.convert("1-14-05.png");
        String withoutExt = ConvertUtil.convert("1-14-05");
        assertEquals(withExt, withoutExt);
        assertNotNull(withExt);
    }

    @Test
    void testConvert_charmapNotEmpty() {
        // charmapが読み込まれていることを確認（少なくともいくつかのエントリが存在する）
        // 第1面の最初と最後あたりのエントリを確認
        assertNotNull(ConvertUtil.convert("1-14-01"));
        assertNotNull(ConvertUtil.convert("1-14-89"));
    }
}
