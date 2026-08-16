package org.androiddaisyreader.mykohoconvert;

import okhttp3.OkHttpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * マイ広報紙の自治体一覧と各自治体の広報情報（記事一覧・PDF・音声）を収集してCSVに出力する。
 *
 * <p>実行例:
 * <pre>
 *   java MyKohoScraper [CSV出力先] [最大処理件数] [リクエスト間隔ms]
 * </pre>
 * 引数はすべて省略可。既定値は順に mykoho.csv / 全件 / 1000ms。</p>
 *
 * <p>社内ネットワーク等のプロキシ・SSL証明書検証スキップはシステムプロパティで指定できます:
 * <pre>
 *   java -Dmykoho.trustAllCerts=true -Dmykoho.proxyHost=tkyproxy-std -Dmykoho.proxyPort=8080 MyKohoScraper
 * </pre>
 * </p>
 */
public class MyKohoScraper {

    private static final String DEFAULT_OUTPUT = "mykoho.csv";
    private static final long DEFAULT_DELAY_MILLIS = 1000;

    public static void main(String[] args) throws IOException {
        String outputPath = args.length > 0 ? args[0] : DEFAULT_OUTPUT;
        int maxCount = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        long delayMillis = args.length > 2 ? Long.parseLong(args[2]) : DEFAULT_DELAY_MILLIS;

        OkHttpClient httpClient = buildHttpClient();
        MyKohoClient client = new MyKohoClient(httpClient, delayMillis);

        try {
            System.out.println("=== 自治体一覧を取得 ===");
            List<MyKohoInfo> municipalities = client.fetchMunicipalities();
            System.out.println("自治体数: " + municipalities.size());

            int limit = maxCount > 0 ? Math.min(maxCount, municipalities.size()) : municipalities.size();
            System.out.println("処理対象: " + limit + "件");

            int processed = 0;
            int withKoho = 0;
            int withPdf = 0;
            int withAudio = 0;

            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(outputPath), StandardCharsets.UTF_8)) {
                writer.write('\uFEFF'); // BOM（ExcelでUTF-8を正しく開くため）
                writer.write("都道府県名,市区町村名,地方公共団体コード,マイ広報紙自治体コード,"
                        + "自治体ページURL,マイ広報紙コード,最新号記事一覧URL,広報URL,音声URL\r\n");

                for (int i = 0; i < limit; i++) {
                    MyKohoInfo info = municipalities.get(i);
                    try {
                        MyKohoInfo result = client.inspectLatestIssue(client.inspectMunicipality(info));
                        writer.write(toCsvRow(result) + "\r\n");
                        if (result.getKohoUrl() != null) {
                            withKoho++;
                        }
                        if (result.getPdfUrl() != null) {
                            withPdf++;
                        }
                        if (result.getAudioUrl() != null) {
                            withAudio++;
                        }
                        processed++;
                        System.out.printf("[%d/%d] %s %s%s%n", i + 1, limit,
                                result.getPrefecture(), result.getMunicipality(),
                                result.getAudioUrl() != null ? " (音声あり)" : "");
                    } catch (IOException e) {
                        System.err.printf("[%d/%d] スキップ: %s %s - %s%n", i + 1, limit,
                                info.getPrefecture(), info.getMunicipality(), e.getMessage());
                        writer.write(toCsvRow(info) + "\r\n");
                    }
                }
            }

            System.out.println("=== 完了 ===");
            System.out.println("処理件数: " + processed);
            System.out.println("最新号記事あり: " + withKoho);
            System.out.println("PDF版あり: " + withPdf);
            System.out.println("音声あり: " + withAudio);
            System.out.println("CSV出力先: " + outputPath);
        } finally {
            client.close();
        }
    }

    private static String toCsvRow(MyKohoInfo info) {
        return csvField(info.getPrefecture()) + ","
                + csvField(info.getMunicipality()) + ","
                + csvField(info.getLgCode()) + ","
                + csvField(info.getMyKohoCode()) + ","
                + csvField(info.getLgUrl()) + ","
                + csvField(info.getKohoCode()) + ","
                + csvField(info.getKohoUrl()) + ","
                + csvField(info.getPdfUrl()) + ","
                + csvField(info.getAudioUrl());
    }

    private static String csvField(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static OkHttpClient buildHttpClient() {
        boolean trustAllCerts = Boolean.parseBoolean(
                System.getProperty("mykoho.trustAllCerts", "false"));
        String proxyHost = System.getProperty("mykoho.proxyHost");
        String proxyPort = System.getProperty("mykoho.proxyPort");

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true);
        if (trustAllCerts) {
            applyTrustAllCerts(builder);
        }
        if (proxyHost != null && !proxyHost.isEmpty()) {
            int port = proxyPort != null && !proxyPort.isEmpty()
                    ? Integer.parseInt(proxyPort) : 8080;
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, port)));
        }
        return builder.build();
    }

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
            throw new IllegalStateException("SSL検証スキップの設定に失敗しました", e);
        }
    }
}
