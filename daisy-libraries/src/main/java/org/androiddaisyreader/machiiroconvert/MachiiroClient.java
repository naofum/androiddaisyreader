package org.androiddaisyreader.machiiroconvert;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.androiddaisyreader.machiiroconvert.converter.MagazineJsonParser;
import org.androiddaisyreader.machiiroconvert.converter.MachiiroEpubConverter;
import org.androiddaisyreader.machiiroconvert.converter.PageImage;
import org.androiddaisyreader.machiiroconvert.converter.PageImageExtractor;
import org.androiddaisyreader.machiiroconvert.exception.MachiiroConvertException;
import org.androiddaisyreader.machiiroconvert.exception.MachiiroException;
import org.androiddaisyreader.machiiroconvert.model.Magazine;
import org.androiddaisyreader.machiiroconvert.model.Municipality;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * マチイロ（https://machiiro.town）にアクセスするためのファサードクラス。
 * 自治体一覧の取得、発行物の検出、テキストデータの取得、ページ画像のダウンロード、
 * EPUBへの変換を提供する。
 *
 * <p>スレッドセーフ: このクラスのインスタンスは複数スレッドから安全に使用できる。</p>
 */
public class MachiiroClient implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(MachiiroClient.class);

    private static final String BASE_URL = "https://machiiro.town";
    private static final String MAP_URL = BASE_URL + "/map";

    /** 自治体LPのURLパターン: https://machiiro.town/lp/{自治体ID} */
    private static final Pattern MUNICIPALITY_URL_PATTERN =
            Pattern.compile("^(?:https://machiiro\\.town)?/lp/([^/?#]+)");

    /** 発行IDのパターン: /p/{発行ID}/ja */
    private static final Pattern ISSUE_ID_PATTERN =
            Pattern.compile("/p/(\\d+)/ja");

    private final OkHttpClient httpClient;

    /**
     * MachiiroClientを構築する。
     */
    public MachiiroClient() {
        this(false, null, 0);
    }

    /**
     * MachiiroClientを構築する。
     *
     * @param trustAllCerts SSL証明書検証をスキップする（プロキシ環境用）
     */
    public MachiiroClient(boolean trustAllCerts) {
        this(trustAllCerts, null, 0);
    }

    /**
     * MachiiroClientを構築する。
     *
     * @param trustAllCerts SSL証明書検証をスキップする（プロキシ環境用）
     * @param proxyHost     プロキシホスト（nullの場合はプロキシなし）
     * @param proxyPort     プロキシポート
     */
    public MachiiroClient(boolean trustAllCerts, String proxyHost, int proxyPort) {
        CookieJar cookieJar = new CookieJar() {
            private final Map<String, List<Cookie>> cookieStore =
                    Collections.synchronizedMap(new HashMap<>());

            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                cookieStore.put(url.host(), cookies);
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                List<Cookie> cookies = cookieStore.get(url.host());
                return cookies != null ? cookies : Collections.emptyList();
            }
        };

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true);

        if (proxyHost != null && !proxyHost.isEmpty()) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        }

        if (trustAllCerts) {
            try {
                X509TrustManager trustManager = new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                };
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{trustManager}, null);
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                builder.sslSocketFactory(sslSocketFactory, trustManager);
                builder.hostnameVerifier((hostname, session) -> true);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                logger.warn("SSL検証スキップの設定に失敗しました", e);
            }
        }

        this.httpClient = builder.build();
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    // ========== 自治体一覧取得 ==========

    /**
     * マチイロの自治体一覧を取得する。
     * 一覧ページ（https://machiiro.town/map）のHTMLから、
     * /lp/{自治体ID} 形式のリンクと自治体名を抽出する。
     *
     * @return 自治体のリスト
     * @throws MachiiroException 取得失敗時
     */
    public synchronized List<Municipality> getMunicipalities() throws MachiiroException {
        logger.info("自治体一覧を取得します");
        try {
            String html = get(MAP_URL);
            Document doc = Jsoup.parse(html, MAP_URL);

            List<Municipality> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Elements anchors = doc.select("a[href]");
            for (Element anchor : anchors) {
                String href = anchor.absUrl("href");
                String id = extractMunicipalityId(href);
                if (id == null || seen.contains(id)) {
                    continue;
                }
                String name = anchor.text().trim();
                if (name.isEmpty()) {
                    continue;
                }
                seen.add(id);
                result.add(new Municipality(id, name, href));
            }
            logger.info("自治体: {}件", result.size());
            return result;
        } catch (IOException e) {
            throw new MachiiroException("自治体一覧の取得に失敗しました", e);
        }
    }

    /**
     * URLから自治体IDを抽出する。形式に合致しない場合はnullを返す。
     */
    String extractMunicipalityId(String href) {
        if (href == null) {
            return null;
        }
        Matcher matcher = MUNICIPALITY_URL_PATTERN.matcher(href);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ========== 発行IDの検出 ==========

    /**
     * 自治体のランディングページから発行IDを検出する。
     *
     * @param municipality 自治体
     * @return 発行ID
     * @throws MachiiroException 発行IDが見つからない場合、または取得失敗時
     */
    public synchronized String findIssueId(Municipality municipality) throws MachiiroException {
        if (municipality == null) {
            throw new MachiiroException("自治体がnullです");
        }
        return findIssueId(municipality.getUrl());
    }

    /**
     * 指定URL（自治体ランディングページ）から発行IDを検出する。
     * /p/{発行ID}/ja 形式の &lt;a href&gt; または &lt;button onclick&gt; を探す。
     *
     * @param lpUrl 自治体のランディングページURL
     * @return 発行ID
     * @throws MachiiroException 発行IDが見つからない場合、または取得失敗時
     */
    public synchronized String findIssueId(String lpUrl) throws MachiiroException {
        logger.info("発行IDを検索します: {}", lpUrl);
        try {
            String html = get(lpUrl);
            String issueId = extractIssueId(html);
            if (issueId == null) {
                throw new MachiiroException("発行IDが見つかりません: " + lpUrl);
            }
            logger.info("発行IDを検出しました: {}", issueId);
            return issueId;
        } catch (IOException e) {
            throw new MachiiroException("発行IDの検索に失敗しました: " + lpUrl, e);
        }
    }

    /**
     * HTMLから発行ID（/p/{発行ID}/ja）を抽出する。見つからない場合はnull。
     */
    String extractIssueId(String html) {
        if (html == null) {
            return null;
        }
        Document doc = Jsoup.parse(html);

        // <a href="/p/{発行ID}/ja" ...>
        for (Element anchor : doc.select("a[href]")) {
            Matcher matcher = ISSUE_ID_PATTERN.matcher(anchor.attr("href"));
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        // <button onclick="window.open('/p/{発行ID}/ja', '_blank')">
        for (Element element : doc.select("[onclick]")) {
            Matcher matcher = ISSUE_ID_PATTERN.matcher(element.attr("onclick"));
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    // ========== 広報誌データ取得 ==========

    /**
     * 発行IDから広報誌のデータを取得する。
     * リーダーページ（/p/{発行ID}/ja）を開いてCookieとCSRFトークンを引き継ぎつつ、
     * テキストデータAPIからJSONを取得して解析する。
     *
     * @param issueId 発行ID
     * @return 広報誌データ
     * @throws MachiiroException 取得失敗時
     */
    public synchronized Magazine getMagazine(String issueId) throws MachiiroException {
        logger.info("広報誌データを取得します: issueId={}", issueId);
        try {
            // 1. リーダーページを開き、CookieとCSRFトークンを取得
            String readerUrl = BASE_URL + "/p/" + issueId + "/ja";
            String readerHtml = get(readerUrl);
            String csrfToken = extractCsrfToken(readerHtml);

            // 2. テキストデータAPIを呼び出す
            String apiUrl = BASE_URL + "/magazine-documents/text_edit/" + issueId + "/ja";
            Request.Builder requestBuilder = new Request.Builder()
                    .url(apiUrl)
                    .get()
                    .header("Referer", readerUrl)
                    .header("X-Requested-With", "XMLHttpRequest");
            if (csrfToken != null && !csrfToken.isEmpty()) {
                requestBuilder.header("X-CSRF-Token", csrfToken);
            }

            String json;
            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                if (!response.isSuccessful()) {
                    throw new MachiiroException("テキストデータの取得に失敗しました: HTTP " + response.code());
                }
                ResponseBody body = response.body();
                json = body != null ? body.string() : "";
            }

            return new MagazineJsonParser().parse(issueId, json);
        } catch (IOException e) {
            throw new MachiiroException("広報誌データの取得中にネットワークエラーが発生しました", e);
        }
    }

    /**
     * HTMLからCSRFトークンを抽出する。見つからない場合はnull。
     */
    String extractCsrfToken(String html) {
        if (html == null) {
            return null;
        }
        Document doc = Jsoup.parse(html);
        Element meta = doc.selectFirst("meta[name=csrf-token]");
        if (meta != null && !meta.attr("content").isEmpty()) {
            return meta.attr("content");
        }
        Element input = doc.selectFirst("input[name=_token], input[name=authenticity_token]");
        return input != null ? input.attr("value") : null;
    }

    // ========== ページ画像取得 ==========

    /**
     * htmlUrlのHTMLからページ画像（data:image）を取得する。
     *
     * @param htmlUrl ページ画像版のHTML URL
     * @return ページ画像のリスト
     * @throws MachiiroException 取得失敗時
     */
    public synchronized List<PageImage> downloadPageImages(String htmlUrl) throws MachiiroException {
        logger.info("ページ画像を取得します: {}", htmlUrl);
        try {
            String html = get(htmlUrl);
            return new PageImageExtractor().extract(html);
        } catch (IOException e) {
            throw new MachiiroException("ページ画像の取得に失敗しました", e);
        }
    }

    // ========== ダウンロード処理 ==========

    /**
     * 発行IDを指定して広報誌をダウンロードし、EPUBファイルとして保存する。
     *
     * @param issueId   発行ID
     * @param outputDir 出力先ディレクトリ
     * @return 生成されたEPUBファイル
     * @throws MachiiroException ダウンロード失敗時
     */
    public synchronized File download(String issueId, File outputDir) throws MachiiroException {
        logger.info("ダウンロード処理を開始します: issueId={}", issueId);

        Magazine magazine = getMagazine(issueId);

        List<PageImage> images = new ArrayList<>();
        if (magazine.getHtmlUrl() != null && !magazine.getHtmlUrl().isEmpty()) {
            images = downloadPageImages(magazine.getHtmlUrl());
        }

        File epubFile = new File(outputDir, issueId + ".epub");
        try {
            new MachiiroEpubConverter().convert(magazine, images, epubFile);
            logger.info("EPUBファイルを作成しました: {}", epubFile.getAbsolutePath());
        } catch (MachiiroConvertException | IOException e) {
            throw new MachiiroException("EPUB変換に失敗しました: issueId=" + issueId, e);
        }
        return epubFile;
    }

    /**
     * 自治体を指定して広報誌をダウンロードし、EPUBファイルとして保存する。
     * 発行IDの検出とダウンロードを連続して行う。
     *
     * @param municipality 自治体
     * @param outputDir    出力先ディレクトリ
     * @return 生成されたEPUBファイル
     * @throws MachiiroException ダウンロード失敗時
     */
    public synchronized File downloadMunicipality(Municipality municipality, File outputDir)
            throws MachiiroException {
        String issueId = findIssueId(municipality);
        return download(issueId, outputDir);
    }

    // ========== 内部ヘルパー ==========

    /**
     * 指定URLへGETリクエストを送信し、レスポンスボディを文字列として返す。
     */
    private String get(String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + url);
            }
            ResponseBody body = response.body();
            return body != null ? body.string() : "";
        }
    }
}
