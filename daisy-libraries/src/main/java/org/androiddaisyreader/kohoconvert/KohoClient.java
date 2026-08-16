package org.androiddaisyreader.kohoconvert;

import okhttp3.OkHttpClient;
import org.androiddaisyreader.kohoconvert.exception.KohoException;
import org.androiddaisyreader.kohoconvert.model.VoicePage;
import org.androiddaisyreader.util.HttpClientFactory;
import org.androiddaisyreader.util.ResourceDownloader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自治体広報（音声版）サイトにアクセスするためのクライアントクラス。
 * トップページのURLを指定すると、トップページのHTMLを取得し、
 * ページ内でリンクされている音声版広報ページの一覧を返す。
 *
 * <p>使い方:
 * <pre>
 *   try (KohoClient client = new KohoClient()) {
 *       List&lt;VoicePage&gt; pages = client.fetchVoicePages("https://www.koho.metro.tokyo.lg.jp/");
 *   }
 * </pre>
 */
public class KohoClient implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(KohoClient.class);

    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("/(\\d{4})/(\\d{2})/");

    private final OkHttpClient httpClient;

    /**
     * 標準設定のHTTPクライアントでKohoClientを構築する。
     */
    public KohoClient() {
        this(HttpClientFactory.createDefaultClient());
    }

    /**
     * SSL証明書検証スキップとプロキシを指定してKohoClientを構築する（社内プロキシ等のMITM環境用）。
     *
     * @param trustAllCerts SSL証明書検証をスキップするか
     * @param proxyHost     プロキシホスト（nullまたは空の場合はプロキシなし）
     * @param proxyPort     プロキシポート
     */
    public KohoClient(boolean trustAllCerts, String proxyHost, int proxyPort) {
        OkHttpClient.Builder builder = HttpClientFactory.newBuilder(trustAllCerts);
        if (proxyHost != null && !proxyHost.isEmpty()) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        }
        this.httpClient = builder.build();
    }

    /**
     * 指定したHTTPクライアントでKohoClientを構築する。テストなどで差し替え可能。
     *
     * @param httpClient 使用するHTTPクライアント
     */
    public KohoClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * トップページのURLを指定して、ページ内でリンクされている音声版広報ページの一覧を取得する。
     * 各ページのリンク先を参照してページタイトル（&lt;title&gt;）も取得して返す。
     * タイトル取得に失敗したページのタイトルはnullになる。
     *
     * @param topPageUrl トップページのURL
     * @return 音声版広報ページの一覧（見つからない場合は空リスト）
     * @throws KohoException トップページの取得に失敗した場合
     */
    public List<VoicePage> fetchVoicePages(String topPageUrl) throws KohoException {
        logger.info("トップページから音声版広報ページを検出します: {}", topPageUrl);

        String html;
        try {
            html = HttpClientFactory.fetchHtml(httpClient, topPageUrl);
        } catch (IOException e) {
            throw new KohoException("トップページの取得に失敗しました: " + topPageUrl, e);
        }

        List<VoicePage> pages = parseVoicePages(html, topPageUrl);
        List<VoicePage> enriched = new ArrayList<>(pages.size());
        for (VoicePage page : pages) {
            enriched.add(page.withPageTitle(fetchPageTitle(page.getUrl())));
        }
        return enriched;
    }

    /**
     * 指定した音声版広報ページにアクセスし、HTMLをダウンロードして保存するとともに、
     * HTML内でリンクされたリソース（音声mp3・CSS・JavaScript・画像など）を
     * 配下のディレクトリにダウンロードする。
     *
     * @param pageUrl   音声版広報ページのURL
     * @param outputDir 出力先ディレクトリ
     * @return ダウンロード先ディレクトリ（outputDir配下の年_月フォルダ）
     * @throws KohoException ダウンロードに失敗した場合
     */
    public File downloadVoicePage(String pageUrl, File outputDir) throws KohoException {
        logger.info("音声版広報ページをダウンロードします: {}", pageUrl);

        String html;
        try {
            html = HttpClientFactory.fetchHtml(httpClient, pageUrl);
        } catch (IOException e) {
            throw new KohoException("広報ページの取得に失敗しました: " + pageUrl, e);
        }

        File workDir = new File(outputDir, deriveDirectoryName(pageUrl));
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw new KohoException("出力ディレクトリの作成に失敗しました: " + workDir);
        }

        try {
            // メインHTMLをindex.htmlとして保存
            writeStringToFile(new File(workDir, "index.html"), html);

            // リンクされたリソース（音声・CSS・JS・画像）をダウンロード
            int slash = pageUrl.lastIndexOf('/');
            String baseUrl = slash >= 0 ? pageUrl.substring(0, slash + 1) : pageUrl + "/";
            ResourceDownloader.downloadLinkedResources(httpClient, html, baseUrl, workDir, pageUrl);
        } catch (IOException e) {
            throw new KohoException("広報ページのダウンロード中にエラーが発生しました: " + pageUrl, e);
        }

        logger.info("広報ページのダウンロードが完了しました: {}", workDir.getAbsolutePath());
        return workDir;
    }

    /**
     * HTMLから音声版広報ページへのリンクを抽出する。
     * 音声版ページは「hrefがvoice.htmlで終わる」または「リンクテキストに"音声版"を含む」リンクと判定する。
     * 相対URLはbaseUrlを基準に絶対URLへ解決し、重複は除去する。
     *
     * @param html    HTML文字列
     * @param baseUrl 相対URL解決の基準URL（トップページのURL）
     * @return 音声版広報ページの一覧
     */
    List<VoicePage> parseVoicePages(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        List<VoicePage> pages = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Element link : doc.select("a[href]")) {
            String href = link.attr("href");
            if (href == null || href.trim().isEmpty()) {
                continue;
            }
            String trimmedHref = href.trim();
            if (trimmedHref.startsWith("#")
                    || trimmedHref.startsWith("javascript:")
                    || trimmedHref.startsWith("mailto:")
                    || trimmedHref.startsWith("tel:")) {
                continue;
            }
            String absUrl = link.absUrl("href");
            if (absUrl.isEmpty()) {
                continue;
            }
            String text = link.text().trim();
            if (!isVoicePageLink(absUrl, text)) {
                continue;
            }
            if (!seen.add(absUrl)) {
                continue;
            }
            pages.add(createVoicePage(absUrl, text));
        }

        logger.info("音声版広報ページを{}件検出しました: {}", pages.size(), baseUrl);
        return pages;
    }

    /**
     * 指定したリンクが音声版広報ページへのリンクかどうかを判定する。
     *
     * @param url      絶対URL
     * @param linkText リンクテキスト
     * @return 音声版広報ページならtrue
     */
    boolean isVoicePageLink(String url, String linkText) {
        if (linkText != null && linkText.contains("音声版")) {
            return true;
        }
        String path = stripQueryAndFragment(url);
        return path.endsWith("/voice.html") || path.endsWith("/voice");
    }

    private VoicePage createVoicePage(String url, String text) {
        Integer year = null;
        Integer month = null;
        Matcher matcher = YEAR_MONTH_PATTERN.matcher(url);
        if (matcher.find()) {
            year = safeParseInt(matcher.group(1));
            month = safeParseInt(matcher.group(2));
        }
        return new VoicePage(url, text, year, month);
    }

    /**
     * 指定URLのHTMLを取得してページタイトル（&lt;title&gt;）を返す。
     * 取得に失敗した場合はnullを返す。
     *
     * @param url 取得対象のURL
     * @return ページタイトル。取得できない場合はnull
     */
    private String fetchPageTitle(String url) {
        try {
            String html = HttpClientFactory.fetchHtml(httpClient, url);
            return extractTitle(html);
        } catch (IOException e) {
            logger.warn("ページタイトルの取得に失敗しました: {} - {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * HTMLからページタイトル（&lt;title&gt;）を抽出する。
     *
     * @param html HTML文字列
     * @return ページタイトル。見つからない場合は空文字
     */
    String extractTitle(String html) {
        String title = Jsoup.parse(html).title();
        return title == null ? "" : title.trim();
    }

    /**
     * ページURLから出力先ディレクトリ名を導出する。
     * URLに「/年/月/」の形式があれば「年_月」、無ければファイル名から生成する。
     */
    private String deriveDirectoryName(String pageUrl) {
        Matcher matcher = YEAR_MONTH_PATTERN.matcher(pageUrl);
        if (matcher.find()) {
            return matcher.group(1) + "_" + matcher.group(2);
        }
        String path = stripQueryAndFragment(pageUrl);
        int slash = path.lastIndexOf('/');
        String last = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = last.lastIndexOf('.');
        if (dot > 0) {
            last = last.substring(0, dot);
        }
        return last.isEmpty() ? "koho" : last;
    }

    private void writeStringToFile(File file, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String stripQueryAndFragment(String url) {
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int fragment = path.indexOf('#');
        if (fragment >= 0) {
            path = path.substring(0, fragment);
        }
        return path;
    }

    private Integer safeParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
