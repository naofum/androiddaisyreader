package org.androiddaisyreader.kohoconvert;

import okhttp3.OkHttpClient;
import org.androiddaisyreader.kohoconvert.model.VoicePage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KohoClientTest {

    private static final String BASE = "https://www.city.example.jp/koho/";

    private final KohoClient client = new KohoClient(new OkHttpClient());

    @Test
    void parseVoicePages_extractsVoicePageLinks() {
        String html = "<html><body>"
                + "<a href=\"/koho/2026/08/voice.html\">音声版</a>"
                + "<a href=\"/koho/2026/07/voice.html\">2026年7月号音声版</a>"
                + "</body></html>";

        List<VoicePage> pages = client.parseVoicePages(html, BASE);
        assertEquals(2, pages.size());
        assertEquals("https://www.city.example.jp/koho/2026/08/voice.html", pages.get(0).getUrl());
    }

    @Test
    void parseVoicePages_extractsYearMonthFromUrl() {
        String html = "<html><body>"
                + "<a href=\"https://www.city.example.jp/koho/2026/08/voice.html\">音声版</a>"
                + "</body></html>";

        List<VoicePage> pages = client.parseVoicePages(html, BASE);
        assertEquals(1, pages.size());
        assertEquals(Integer.valueOf(2026), pages.get(0).getYear());
        assertEquals(Integer.valueOf(8), pages.get(0).getMonth());
    }

    @Test
    void parseVoicePages_yearMonthNullWhenNotInUrl() {
        String html = "<html><body>"
                + "<a href=\"/koho/voice.html\">音声版</a>"
                + "</body></html>";

        List<VoicePage> pages = client.parseVoicePages(html, BASE);
        assertEquals(1, pages.size());
        assertNull(pages.get(0).getYear());
        assertNull(pages.get(0).getMonth());
    }

    @Test
    void parseVoicePages_filtersNonVoiceLinks() {
        String html = "<html><body>"
                + "<a href=\"/koho/about.html\">概要</a>"
                + "<a href=\"/koho/2026/08/voice.html\">音声版</a>"
                + "</body></html>";

        List<VoicePage> pages = client.parseVoicePages(html, BASE);
        assertEquals(1, pages.size());
        assertEquals("https://www.city.example.jp/koho/2026/08/voice.html", pages.get(0).getUrl());
    }

    @Test
    void parseVoicePages_deduplicates() {
        String html = "<html><body>"
                + "<a href=\"/koho/2026/08/voice.html\">音声版</a>"
                + "<a href=\"/koho/2026/08/voice.html\">音声版（再掲）</a>"
                + "</body></html>";

        List<VoicePage> pages = client.parseVoicePages(html, BASE);
        assertEquals(1, pages.size());
    }

    @Test
    void parseVoicePages_skipsHashJavascriptMailto() {
        String html = "<html><body>"
                + "<a href=\"#section\">目次</a>"
                + "<a href=\"javascript:void(0)\">JS</a>"
                + "<a href=\"mailto:test@example.com\">メール</a>"
                + "<a href=\"/koho/2026/08/voice.html\">音声版</a>"
                + "</body></html>";

        List<VoicePage> pages = client.parseVoicePages(html, BASE);
        assertEquals(1, pages.size());
    }

    @Test
    void isVoicePageLink_trueWhenLinkTextContainsVoice() {
        assertTrue(client.isVoicePageLink("https://example.com/koho/foo.html", "音声版はこちら"));
    }

    @Test
    void isVoicePageLink_trueWhenUrlEndsWithVoice() {
        assertTrue(client.isVoicePageLink("https://example.com/koho/2026/08/voice.html", "8月号"));
        assertTrue(client.isVoicePageLink("https://example.com/koho/2026/08/voice", "8月号"));
    }

    @Test
    void isVoicePageLink_falseForOtherLinks() {
        assertFalse(client.isVoicePageLink("https://example.com/koho/2026/08/index.html", "8月号"));
    }

    @Test
    void extractTitle_returnsTitleText() {
        assertEquals("ページタイトル",
                client.extractTitle("<html><head><title>ページタイトル</title></head><body></body></html>"));
    }

    @Test
    void extractTitle_returnsEmptyWhenNoTitle() {
        assertEquals("", client.extractTitle("<html><body></body></html>"));
    }
}
