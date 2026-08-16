package org.androiddaisyreader.kohoconvert;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KohoEpubConverterTest {

    private final KohoEpubConverter converter = new KohoEpubConverter();

    @Test
    void parseAudioRows_extractsTitleFileAndDuration() {
        String html = "<html><body><table>"
                + "<tr><td><a href=\"files/speech0001.mp3\">市報 1面（MP3：1.2MB）</a></td><td>2分04秒</td></tr>"
                + "<tr><td><a href=\"files/speech0002.mp3\">市報 2面（MP3：800KB）</a></td><td>1分30秒</td></tr>"
                + "</table></body></html>";

        List<KohoEpubConverter.AudioRow> rows = converter.parseAudioRows(html);

        assertEquals(2, rows.size());
        assertEquals("市報 1面", rows.get(0).title);
        assertEquals("documents/speech0001.mp3", rows.get(0).audioFile);
        assertEquals(124.0, rows.get(0).durationSeconds, 0.001);
        assertEquals("documents/speech0002.mp3", rows.get(1).audioFile);
        assertEquals(90.0, rows.get(1).durationSeconds, 0.001);
    }

    @Test
    void parseAudioRows_ignoresNonAudioLinks() {
        String html = "<html><body><table>"
                + "<tr><td><a href=\"files/readme.txt\">説明</a></td><td>1分00秒</td></tr>"
                + "<tr><td><a href=\"files/speech0001.mp3\">1面</a></td><td>30秒</td></tr>"
                + "</table></body></html>";

        List<KohoEpubConverter.AudioRow> rows = converter.parseAudioRows(html);
        assertEquals(1, rows.size());
        assertEquals("documents/speech0001.mp3", rows.get(0).audioFile);
    }

    @Test
    void cleanTitle_removesMp3SizeNote() {
        assertEquals("市報こだいら 1面", converter.cleanTitle("市報こだいら 1面（MP3：1.2MB）"));
        assertEquals("見出し", converter.cleanTitle("見出し(MP3:100KB)"));
    }

    @Test
    void parseDurationSeconds_parsesMinutesAndSeconds() {
        assertEquals(124.0, converter.parseDurationSeconds("2分04秒"), 0.001);
        assertEquals(90.0, converter.parseDurationSeconds("1分30秒"), 0.001);
        assertEquals(0.0, converter.parseDurationSeconds(""), 0.001);
        assertEquals(0.0, converter.parseDurationSeconds(null), 0.001);
    }

    @Test
    void formatDuration_formatsClock() {
        assertEquals("0:02:04.000", KohoEpubConverter.formatDuration(124.0));
        assertEquals("1:00:00.000", KohoEpubConverter.formatDuration(3600.0));
        assertEquals("0:00:00.000", KohoEpubConverter.formatDuration(0.0));
    }

    @Test
    void extractBookTitle_prefersH1() {
        Document doc = Jsoup.parse("<html><head><title>ヘッダ</title></head><body><h1>本体タイトル</h1></body></html>");
        assertEquals("本体タイトル", converter.extractBookTitle(doc));
    }

    @Test
    void extractBookTitle_fallsBackToTitleTag() {
        Document doc = Jsoup.parse("<html><head><title>ヘッダタイトル</title></head><body></body></html>");
        assertEquals("ヘッダタイトル", converter.extractBookTitle(doc));
    }

    @Test
    void extractBookTitle_defaultWhenNoTitle() {
        Document doc = Jsoup.parse("<html><head></head><body></body></html>");
        assertEquals("音声版広報", converter.extractBookTitle(doc));
    }

    @Test
    void parseAudioRows_emptyWhenNoRows() {
        assertTrue(converter.parseAudioRows("<html><body></body></html>").isEmpty());
    }
}
