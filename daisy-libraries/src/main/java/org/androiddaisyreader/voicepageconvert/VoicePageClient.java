package org.androiddaisyreader.voicepageconvert;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任意の音声ページ（複数リンク → 最新号 → mp3一覧）を辿って EPUB を作成するクライアント。
 */
public class VoicePageClient implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(VoicePageClient.class);

    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=[\"']?([A-Za-z0-9._-]+)");

    private final OkHttpClient httpClient;

    public VoicePageClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    /**
     * 音声ページを辿り、mp3をダウンロードして EPUB3 メディアオーバーレイを生成する。
     *
     * @param topUrl         音声ページのトップURL（号ごとのリンク一覧）
     * @param fallbackTitle  EPUBタイトル（号ページからタイトルを取得できない場合に使用）
     * @param outputDir      出力ディレクトリ
     * @return 生成された EPUB ファイル
     * @throws IOException 取得・生成に失敗した場合
     */
    public File download(String topUrl, String fallbackTitle, File outputDir) throws IOException {
        String topHtml = fetchHtml(topUrl);
        String issueUrl = findNewestIssueUrl(topHtml, topUrl);
        if (issueUrl == null) {
            throw new IOException("リンクが見つかりません: " + topUrl);
        }
        logger.info("最新号ページ: {}", issueUrl);

        String issueHtml = fetchHtml(issueUrl);
        String title = extractTitle(issueHtml, fallbackTitle);
        List<AudioEntry> entries = extractAudioEntries(issueHtml, issueUrl);
        if (entries.isEmpty()) {
            throw new IOException("mp3リンクが見つかりません: " + issueUrl);
        }

        File audioDir = new File(outputDir, "audio");
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }
        List<VoiceEpubConverter.AudioItem> items = new ArrayList<>();
        int index = 1;
        for (AudioEntry entry : entries) {
            File file = new File(audioDir, String.format(Locale.ROOT, "audio_%04d.mp3", index));
            downloadFile(entry.getUrl(), file);
            String itemTitle = entry.getTitle().isEmpty()
                    ? "音声" + index : entry.getTitle();
            items.add(new VoiceEpubConverter.AudioItem(itemTitle, file));
            index++;
        }

        File epubFile = new File(outputDir, "voice.epub");
        new VoiceEpubConverter().convert(title, items, epubFile);
        logger.info("音声EPUBを作成しました: {} ({}件)", epubFile.getAbsolutePath(), items.size());
        return epubFile;
    }

    /**
     * トップページHTMLから最新号ページのURLを抽出する。
     * リンクテキストの日付が最も新しいリンクを選び、日付が拾えなければ先頭リンクにフォールバックする。
     *
     * @param html    トップページHTML
     * @param baseUrl 相対URL解決の基準URL
     * @return 最新号ページURL。見つからない場合は null
     */
    String findNewestIssueUrl(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        LocalDate newest = null;
        String newestUrl = null;
        String firstUrl = null;

        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            if (isSkippable(href)) {
                continue;
            }
            String abs = a.absUrl("href");
            if (abs.isEmpty()) {
                continue;
            }
            if (firstUrl == null) {
                firstUrl = abs;
            }
            LocalDate date = JapaneseDateParser.parse(a.text());
            if (date != null && (newest == null || date.isAfter(newest))) {
                newest = date;
                newestUrl = abs;
            }
        }
        return newestUrl != null ? newestUrl : firstUrl;
    }

    /**
     * 号ページHTMLから音声エントリ（タイトル＋mp3 URL）を抽出する。
     * {@code <a href="...mp3">} を優先し、無ければ {@code <source src="...mp3">} を使う。
     *
     * @param html    号ページHTML
     * @param baseUrl 相対URL解決の基準URL
     * @return 音声エントリ一覧（順序維持・重複除去済み）
     */
    List<AudioEntry> extractAudioEntries(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        List<AudioEntry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // <a href="...mp3"> を優先
        for (Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            if (!isMp3(href)) {
                continue;
            }
            String abs = a.absUrl("href");
            if (!seen.add(abs)) {
                continue;
            }
            entries.add(new AudioEntry(a.text().trim(), abs));
        }

        // <source src="...mp3"> へのフォールバック
        if (entries.isEmpty()) {
            int n = 1;
            for (Element s : doc.select("source[src]")) {
                String src = s.attr("src");
                if (!isMp3(src)) {
                    continue;
                }
                String abs = s.absUrl("src");
                if (!seen.add(abs)) {
                    continue;
                }
                entries.add(new AudioEntry("音声" + n, abs));
                n++;
            }
        }
        return entries;
    }

    private String extractTitle(String html, String fallback) {
        Document doc = Jsoup.parse(html);
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().trim().isEmpty()) {
            return h1.text().trim();
        }
        String title = doc.title();
        if (title != null && !title.trim().isEmpty()) {
            return title.trim();
        }
        return fallback != null ? fallback : "音声版広報";
    }

    private boolean isSkippable(String href) {
        if (href == null) {
            return true;
        }
        String h = href.trim();
        return h.startsWith("#") || h.startsWith("javascript:")
                || h.startsWith("mailto:") || h.startsWith("tel:");
    }

    private boolean isMp3(String url) {
        if (url == null) {
            return false;
        }
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int f = path.indexOf('#');
        if (f >= 0) {
            path = path.substring(0, f);
        }
        return path.toLowerCase(Locale.ROOT).endsWith(".mp3");
    }

    private String fetchHtml(String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + url);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("空のレスポンス: " + url);
            }
            byte[] bytes = body.bytes();
            return decode(bytes, response.header("Content-Type"));
        }
    }

    private void downloadFile(String url, File destFile) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + url);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("空のレスポンス: " + url);
            }
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    private String decode(byte[] bytes, String contentType) {
        String charset = charsetFromContentType(contentType);
        if (charset == null) {
            charset = charsetFromMeta(bytes);
        }
        if (charset == null) {
            charset = "UTF-8";
        }
        try {
            return new String(bytes, charset);
        } catch (UnsupportedEncodingException e) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private String charsetFromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        Matcher m = CHARSET_PATTERN.matcher(contentType);
        return m.find() ? m.group(1) : null;
    }

    private String charsetFromMeta(byte[] bytes) {
        int limit = Math.min(bytes.length, 2048);
        String head;
        try {
            head = new String(bytes, 0, limit, "ASCII");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
        Matcher m = CHARSET_PATTERN.matcher(head);
        return m.find() ? m.group(1) : null;
    }
}
