package org.androiddaisyreader.util;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * OkHttpクライアントの生成とHTML取得を共通化するユーティリティ。
 *
 * <p>青空文庫・Chatty Library・自治体広報など、複数のクライアントで
 * 重複していたOkHttpClientの初期化処理（タイムアウト・リダイレクト設定）を
 * ここに集約する。</p>
 */
public final class HttpClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientFactory.class);

    private static final long CONNECT_TIMEOUT_SECONDS = 30;
    private static final long READ_TIMEOUT_SECONDS = 60;

    private HttpClientFactory() {
    }

    /**
     * 標準設定（接続タイムアウト30秒・読み取りタイムアウト60秒・リダイレクト追跡あり）の
     * {@link OkHttpClient.Builder}を返す。Cookieやプロキシなどの追加設定は呼び出し側で行う。
     *
     * @return 標準設定済みのビルダー
     */
    public static OkHttpClient.Builder newBuilder() {
        return newBuilder(false);
    }

    /**
     * 標準設定の{@link OkHttpClient.Builder}を返す。
     * trustAllCertsがtrueの場合はSSL証明書検証をスキップする（社内プロキシ等のMITM環境用）。
     *
     * @param trustAllCerts SSL証明書検証をスキップするか
     * @return 標準設定済みのビルダー
     */
    public static OkHttpClient.Builder newBuilder(boolean trustAllCerts) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true);
        if (trustAllCerts) {
            applyTrustAllCerts(builder);
        }
        return builder;
    }

    /**
     * 標準設定の{@link OkHttpClient}を生成する。
     *
     * @return 標準設定のクライアント
     */
    public static OkHttpClient createDefaultClient() {
        return newBuilder().build();
    }

    /**
     * 標準設定の{@link OkHttpClient}を生成する。
     *
     * @param trustAllCerts SSL証明書検証をスキップするか
     * @return 標準設定のクライアント
     */
    public static OkHttpClient createDefaultClient(boolean trustAllCerts) {
        return newBuilder(trustAllCerts).build();
    }

    /**
     * 指定URLのHTMLを取得して文字列で返す。
     *
     * @param httpClient 使用するHTTPクライアント
     * @param url        取得対象URL
     * @return HTML文字列
     * @throws IOException ネットワークエラーまたはHTTPステータスが非2xxの場合
     */
    public static String fetchHtml(OkHttpClient httpClient, String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch " + url + ": HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body: " + url);
            }
            return body.string();
        }
    }

    /**
     * ビルダーにSSL証明書検証をスキップする設定を適用する。
     */
    private static void applyTrustAllCerts(OkHttpClient.Builder builder) {
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
}
