package org.androiddaisyreader.util;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HTML内でリンクされたリソース（CSS・JavaScript・画像・音声など）を
 * ディレクトリ構造を保ってダウンロードするためのユーティリティ。
 *
 * <p>Chatty Library・自治体広報など複数のクライアントで共有する。</p>
 */
public final class ResourceDownloader {

    private static final Logger logger = LoggerFactory.getLogger(ResourceDownloader.class);

    private static final Set<String> MEDIA_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp3", "m4a", "m4b", "wav", "ogg", "oga", "opus", "aac", "flac", "wma"));

    private ResourceDownloader() {
    }

    /**
     * HTML内でリンクされたリソースをすべてダウンロードする。
     * css(&lt;link&gt;), js(&lt;script&gt;), 画像(&lt;img&gt;), 音声(&lt;audio&gt;)、
     * および音声ファイルへの&lt;a&gt;リンクを対象とする。&lt;a&gt;のうち通常のページ遷移リンクは対象外。
     * リソースのベースURLはHTMLの&lt;base&gt;タグまたは引数のbaseUrlから判定する。
     *
     * @param httpClient 使用するHTTPクライアント
     * @param html       HTML文字列
     * @param baseUrl    リソース解決の基準URL
     * @param outputDir  出力先ディレクトリ
     * @param refererUrl リクエストに付与するReferer（不要ならnull）
     * @throws IOException 入出力エラー時
     */
    public static void downloadLinkedResources(OkHttpClient httpClient, String html, String baseUrl,
                                               File outputDir, String refererUrl) throws IOException {
        Document doc = Jsoup.parse(html, baseUrl);

        // <base href="..."> がある場合はそれを使用
        Element baseTag = doc.selectFirst("base[href]");
        if (baseTag != null) {
            String baseHref = baseTag.absUrl("href");
            if (!baseHref.isEmpty()) {
                doc.setBaseUri(baseHref);
            }
        }

        List<String> urls = new ArrayList<>();

        for (Element el : doc.select("link[href]")) {
            urls.add(el.absUrl("href"));
        }
        for (Element el : doc.select("script[src]")) {
            urls.add(el.absUrl("src"));
        }
        for (Element el : doc.select("img[src]")) {
            urls.add(el.absUrl("src"));
        }
        for (Element el : doc.select("audio[src]")) {
            urls.add(el.absUrl("src"));
        }
        for (Element el : doc.select("audio source[src]")) {
            urls.add(el.absUrl("src"));
        }
        // 音声・メディアファイルへのリンクのみを対象にする
        for (Element el : doc.select("a[href]")) {
            String href = el.absUrl("href");
            if (!href.isEmpty() && isMediaFile(href)) {
                urls.add(href);
            }
        }

        logger.info("ダウンロード対象リソース: {}件", urls.size());
        for (String resourceUrl : urls) {
            if (resourceUrl.isEmpty()) {
                continue;
            }
            try {
                downloadFile(httpClient, resourceUrl, baseUrl, outputDir, refererUrl);
            } catch (IOException e) {
                logger.warn("リソースのダウンロードに失敗しました: {} - {}", resourceUrl, e.getMessage());
            }
        }
    }

    /**
     * 指定URLからファイルをダウンロードして出力先に保存する。
     * ベースURLからの相対パスを維持してディレクトリ構造を保つ。
     *
     * @param httpClient 使用するHTTPクライアント
     * @param url        ダウンロード元URL
     * @param baseUrl    相対パス算出の基準URL
     * @param outputDir  出力先ディレクトリ
     * @param refererUrl リクエストに付与するReferer（不要ならnull）
     * @throws IOException 入出力エラー時
     */
    public static void downloadFile(OkHttpClient httpClient, String url, String baseUrl,
                                    File outputDir, String refererUrl) throws IOException {
        Request.Builder requestBuilder = new Request.Builder().url(url).get();
        if (refererUrl != null && !refererUrl.isEmpty()) {
            requestBuilder.addHeader("Referer", refererUrl);
        }
        Request request = requestBuilder.build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("ダウンロード失敗: {} HTTP {}", url, response.code());
                return;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return;
            }

            // ベースURLからの相対パスを算出
            String relativePath = extractRelativePath(url, baseUrl);
            File outFile = new File(outputDir, relativePath);

            // サブディレクトリがある場合は作成
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = body.byteStream().read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            logger.debug("ダウンロード完了: {}", relativePath);
        }
    }

    /**
     * ベースURLに相対パスを解決して絶対URLを返す。
     *
     * @param baseUrl      ベースURL
     * @param relativePath 相対パス（例: ./sounds/sound00001.mp3）
     * @return 解決された絶対URL。解決できない場合はnull
     */
    public static String resolveUrl(String baseUrl, String relativePath) {
        HttpUrl base = HttpUrl.parse(baseUrl);
        HttpUrl resolved = base != null ? base.resolve(relativePath) : null;
        return resolved != null ? resolved.toString() : null;
    }

    /**
     * ダウンロードURLからベースURLを基準にした相対パスを抽出する。
     * 例: baseUrl=https://example.com/storage/book/381/reflow/
     *     url=https://example.com/storage/book/381/reflow/css/style.css
     *     → css/style.css
     */
    public static String extractRelativePath(String url, String baseUrl) {
        // ベースURLのパス部分を取得
        if (url.startsWith(baseUrl)) {
            String relative = url.substring(baseUrl.length());
            if (!relative.isEmpty()) {
                return relative;
            }
        }

        // ベースURLと一致しない場合はURLのパスから相対パスを推定
        try {
            HttpUrl httpUrl = HttpUrl.parse(url);
            HttpUrl base = HttpUrl.parse(baseUrl);
            if (httpUrl != null && base != null) {
                List<String> urlSegments = httpUrl.pathSegments();
                List<String> baseSegments = base.pathSegments();

                // 共通部分をスキップして残りを相対パスにする
                int commonLen = 0;
                for (int i = 0; i < Math.min(urlSegments.size(), baseSegments.size()); i++) {
                    if (urlSegments.get(i).equals(baseSegments.get(i))) {
                        commonLen = i + 1;
                    } else {
                        break;
                    }
                }

                if (commonLen > 0 && commonLen < urlSegments.size()) {
                    List<String> relativeParts = urlSegments.subList(commonLen, urlSegments.size());
                    return String.join("/", relativeParts);
                }
            }
        } catch (Exception e) {
            // フォールバック
        }

        // フォールバック: ファイル名のみ
        return extractFileName(url);
    }

    /**
     * URLからファイル名のみを抽出する（フォールバック用）。
     */
    public static String extractFileName(String url) {
        try {
            HttpUrl httpUrl = HttpUrl.parse(url);
            if (httpUrl == null) {
                return "unknown";
            }
            List<String> segments = httpUrl.pathSegments();
            if (segments.isEmpty()) {
                return "unknown";
            }
            String last = segments.get(segments.size() - 1);
            return last.isEmpty() ? "index.html" : last;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * URLが音声・メディアファイルへのリンクかどうかを判定する。
     */
    private static boolean isMediaFile(String url) {
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int fragment = path.indexOf('#');
        if (fragment >= 0) {
            path = path.substring(0, fragment);
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return false;
        }
        String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return MEDIA_EXTENSIONS.contains(extension);
    }
}
