package org.androiddaisyreader.voicepageconvert;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoicePageClientTest {

    private static final String BASE = "https://www.city.kodaira.tokyo.jp/shihou-voice/";
    private static final String HOST = "https://www.city.kodaira.tokyo.jp";

    @Test
    void findNewestIssueUrl_picksLatestDatedLink() {
        String html = "<html><body>"
                + "<a href=\"/shihou/128/voice_128809.html\">市報こだいら2026年7月20日号音声版</a>"
                + "<a href=\"/shihou/129/voice_129809.html\">市報こだいら2026年8月5日号音声版</a>"
                + "<a href=\"/shihou/127/voice_127809.html\">市報こだいら2026年6月15日号音声版</a>"
                + "</body></html>";

        String url = new VoicePageClient().findNewestIssueUrl(html, BASE);
        assertEquals(HOST + "/shihou/129/voice_129809.html", url);
    }

    @Test
    void findNewestIssueUrl_fallsBackToFirstLinkWhenNoDates() {
        String html = "<html><body>"
                + "<a href=\"/shihou/128/voice_128809.html\">音声版</a>"
                + "<a href=\"/shihou/129/voice_129809.html\">音声版</a>"
                + "</body></html>";

        String url = new VoicePageClient().findNewestIssueUrl(html, BASE);
        assertEquals(HOST + "/shihou/128/voice_128809.html", url);
    }

    @Test
    void extractAudioEntries_prefersAnchorMp3AndResolvesRelativeUrls() {
        String html = "<html><body>"
                + "<a href=\"/shihou/files/129809/129809/att_0000146.mp3\" class=\" icon_mp3\">2026年8月5日号　1面</a>"
                + "<a href=\"/shihou/files/129809/129809/att_0000147.mp3\" class=\" icon_mp3\">2026年8月5日号　2面</a>"
                + "<source src=\"/shihou/files/129809/129809/att_0000146.mp3\" type=\"audio/mpeg\">"
                + "</body></html>";

        List<AudioEntry> entries = new VoicePageClient().extractAudioEntries(html, BASE);
        assertEquals(2, entries.size());
        assertEquals(HOST + "/shihou/files/129809/129809/att_0000146.mp3", entries.get(0).getUrl());
        assertEquals("2026年8月5日号　1面", entries.get(0).getTitle());
    }

    @Test
    void extractAudioEntries_fallsBackToSourceWhenNoAnchor() {
        String html = "<html><body>"
                + "<source src=\"/shihou/files/129809/129809/att_0000146.mp3\" type=\"audio/mpeg\">"
                + "<source src=\"/shihou/files/129809/129809/att_0000147.mp3\" type=\"audio/mpeg\">"
                + "</body></html>";

        List<AudioEntry> entries = new VoicePageClient().extractAudioEntries(html, BASE);
        assertEquals(2, entries.size());
        assertEquals(HOST + "/shihou/files/129809/129809/att_0000146.mp3", entries.get(0).getUrl());
    }

    @Test
    void extractAudioEntries_deduplicatesByUrl() {
        String html = "<html><body>"
                + "<a href=\"/shihou/files/129809/129809/att_0000146.mp3\">1面</a>"
                + "<a href=\"/shihou/files/129809/129809/att_0000146.mp3\">1面（重複）</a>"
                + "</body></html>";

        List<AudioEntry> entries = new VoicePageClient().extractAudioEntries(html, BASE);
        assertEquals(1, entries.size());
    }

    @Test
    void extractAudioEntries_ignoresNonMp3Links() {
        String html = "<html><body>"
                + "<a href=\"/shihou/129/voice_129809.html\">音声版</a>"
                + "<a href=\"/shihou/files/129809/129809/att_0000146.mp3\">1面</a>"
                + "</body></html>";

        List<AudioEntry> entries = new VoicePageClient().extractAudioEntries(html, BASE);
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).getUrl().endsWith(".mp3"));
    }
}
