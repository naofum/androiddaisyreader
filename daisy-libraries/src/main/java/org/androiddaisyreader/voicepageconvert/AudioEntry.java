package org.androiddaisyreader.voicepageconvert;

/**
 * 音声ページから抽出した1つの音声エントリ（タイトル＋mp3 URL）。
 */
public class AudioEntry {

    private final String title;
    private final String url;

    public AudioEntry(String title, String url) {
        this.title = title;
        this.url = url;
    }

    /** タイトル（例: "2026年8月5日号 1面"）。 */
    public String getTitle() {
        return title;
    }

    /** mp3 の絶対URL。 */
    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "AudioEntry{title='" + title + "', url='" + url + "'}";
    }
}
