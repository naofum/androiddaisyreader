package org.androiddaisyreader.mykohoconvert;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.androiddaisyreader.mykohoconvert.pdf.AudioSection;
import org.androiddaisyreader.mykohoconvert.pdf.Mp3SectionDetector;
import org.androiddaisyreader.mykohoconvert.pdf.PdfBlockExtractor;
import org.androiddaisyreader.mykohoconvert.pdf.PdfEpubConverter;
import org.androiddaisyreader.mykohoconvert.pdf.PdfPageRenderer;
import org.androiddaisyreader.mykohoconvert.pdf.TextAudioAligner;
import org.androiddaisyreader.mykohoconvert.pdf.TextBlock;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.NodeList;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * マイ広報紙（https://mykoho.jp）にアクセスして自治体一覧と各自治体の広報情報を収集するクライアント。
 *
 * <p>処理の流れ（specification_mykoho.md 準拠）:
 * <ol>
 *   <li>自治体一覧ページ（https://mykoho.jp/lg）を取得する</li>
 *   <li>&lt;a&gt;タグからURL（/lg/{地方公共団体コード}/{マイ広報紙自治体コード}）と市区町村名を取得する</li>
 *   <li>各自治体ページを開き &lt;div class="jichitaiPosts-More"&gt; 内の最新号記事一覧URLを確認する</li>
 *   <li>最新号記事一覧ページを開き &lt;div class="kohoHeader-Btn"&gt; 内のPDF版URLを確認する</li>
 *   <li>同じページに &lt;audio id="audio_player"&gt; が含まれるかを確認する</li>
 * </ol>
 * </p>
 */
public class MyKohoClient implements Closeable {

    private static final String LIST_URL = "https://mykoho.jp/lg";
    private static final Pattern LG_PATH_PATTERN = Pattern.compile("/lg/(\\d+)/(\\d+)");
    private static final Pattern KOHO_PATH_PATTERN = Pattern.compile("/koho/(\\d+)/(\\d+)");

    private final OkHttpClient httpClient;
    private final long delayMillis;

    /**
     * デフォルトのHTTPクライアントでクライアントを構築する。
     * リクエスト間隔は500msに設定される。
     */
    public MyKohoClient() {
        this(new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .build(), 500);
    }

    /**
     * クライアントを構築する。
     *
     * @param httpClient  使用するHTTPクライアント
     * @param delayMillis 各リクエスト間の待機時間（ミリ秒）。0以下の場合は待機しない
     */
    public MyKohoClient(OkHttpClient httpClient, long delayMillis) {
        this.httpClient = httpClient;
        this.delayMillis = delayMillis;
    }

    /**
     * 指定URLのHTMLを取得して文字列で返す。
     *
     * @param url 取得対象URL
     * @return HTML文字列
     * @throws IOException ネットワークエラーまたはHTTPステータスが非2xxの場合
     */
    public String fetchHtml(String url) throws IOException {
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
     * 自治体一覧ページを取得して、市区町村の一覧を返す。
     *
     * @return 市区町村の一覧（都道府県名付き）
     * @throws IOException 一覧ページの取得に失敗した場合
     */
    public List<MyKohoInfo> fetchMunicipalities() throws IOException {
        String html = fetchHtml(LIST_URL);
        return parseMunicipalities(html);
    }

    /**
     * municipalities.xml形式のInputStreamをパースして、市区町村の一覧を返す。
     *
     * <p>XMLフォーマット:
     * <pre>{@code
     * <municipalities>
     *   <municipality>
     *     <name>北海道森町</name>
     *     <code>013455</code>
     *     <issueId>269040</issueId>
     *     <uri>mykoho://013455/269040</uri>
     *   </municipality>
     *   ...
     * </municipalities>
     * }</pre>
     * </p>
     *
     * <p>このメソッドはHTTPクライアントを使用しないため、staticメソッドとして提供する。</p>
     *
     * @param inputStream municipalities.xmlのInputStream
     * @return 市区町村の一覧
     * @throws IOException XMLの読み込みに失敗した場合
     */
    public static List<MyKohoInfo> parseMunicipalitiesXml(InputStream inputStream) throws IOException {
        List<MyKohoInfo> result = new ArrayList<>();
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            try {
                dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            } catch (Exception ignored) {}
            try {
                dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            } catch (Exception ignored) {}
            try {
                dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception ignored) {}
            try {
                dbFactory.setXIncludeAware(false);
            } catch (Exception ignored) {}
            dbFactory.setExpandEntityReferences(false);

            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(inputStream);
            doc.getDocumentElement().normalize();

            NodeList municipalityNodes = doc.getElementsByTagName("municipality");
            for (int i = 0; i < municipalityNodes.getLength(); i++) {
                org.w3c.dom.Element elem = (org.w3c.dom.Element) municipalityNodes.item(i);
                String name = getElementText(elem, "name");
                String code = getElementText(elem, "code");
                String issueId = getElementText(elem, "issueId");
                String uri = getElementText(elem, "uri");
                if (name.isEmpty() || code.isEmpty()) {
                    continue;
                }
                // uri要素をそのまま使用（mykoho:// または https:// のいずれか）
                String lgUrl = uri.isEmpty()
                        ? "mykoho://" + code + "/" + issueId
                        : uri;
                result.add(new MyKohoInfo(
                        "",       // prefecture（XMLには都道府県単体の情報なし、nameに含まれる）
                        name,     // municipality（都道府県名+市区町村名）
                        code,     // lgCode
                        issueId,  // myKohoCode（issueIdをマイ広報紙自治体コードとして使用）
                        lgUrl     // lgUrl
                ));
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("municipalities.xml のパースに失敗しました", e);
        }
        return result;
    }

    /**
     * XML要素から指定タグ名の子要素テキストを取得する。
     */
    private static String getElementText(org.w3c.dom.Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        org.w3c.dom.Element elem = (org.w3c.dom.Element) nodes.item(0);
        return elem.getTextContent() != null ? elem.getTextContent().trim() : "";
    }

    /**
     * 自治体一覧ページのHTMLをパースして、市区町村の一覧を返す。
     *
     * <p>一覧は「&lt;details class="areaBox-Item"&gt;」が都道府県単位で並び、
     * その中に「&lt;summary class="areaBox-Item_Title"&gt;」の都道府県名と
     * 「&lt;a class="areaList-Item_Link"&gt;」の市区町村リンクが並ぶ。</p>
     *
     * @param html 自治体一覧ページのHTML
     * @return 市区町村の一覧
     */
    List<MyKohoInfo> parseMunicipalities(String html) {
        Document doc = Jsoup.parse(html, LIST_URL);
        List<MyKohoInfo> result = new ArrayList<>();

        for (Element details : doc.select("details.areaBox-Item")) {
            String prefecture = "";
            Element summary = details.selectFirst("summary.areaBox-Item_Title");
            if (summary != null) {
                prefecture = summary.text().trim();
            }

            for (Element a : details.select("a.areaList-Item_Link")) {
                String url = a.absUrl("href");
                Matcher matcher = LG_PATH_PATTERN.matcher(url);
                if (!matcher.find()) {
                    continue;
                }
                String lgCode = matcher.group(1);
                String myKohoCode = matcher.group(2);
                String name = a.text().trim();
                if (name.isEmpty()) {
                    continue;
                }
                result.add(new MyKohoInfo(prefecture, name, lgCode, myKohoCode, url));
            }
        }
        return result;
    }

    /**
     * 自治体ページを開き、最新号記事一覧URL（マイ広報紙コード）を収集する。
     * 該当リンクが無い場合は kohoUrl が null のまま返る。
     *
     * @param info 収集対象の自治体情報
     * @return 記事一覧情報を反映したMyKohoInfo
     * @throws IOException 自治体ページの取得に失敗した場合
     */
    public MyKohoInfo inspectMunicipality(MyKohoInfo info) throws IOException {
        String html = fetchHtml(info.getLgUrl());
        sleep();
        String kohoUrl = findKohoUrl(html);
        if (kohoUrl == null || kohoUrl.isEmpty()) {
            return info;
        }
        String kohoCode = extractKohoCode(kohoUrl);
        return info.withKoho(kohoCode, kohoUrl);
    }

    /**
     * 最新号記事一覧ページを開き、PDF版URLと音声URLを収集する。
     * 記事一覧URLが無い場合は何もせずそのまま返す。
     *
     * @param info 収集対象の自治体情報（kohoUrlが設定済みであること）
     * @return PDF版URL・音声URLを反映したMyKohoInfo
     * @throws IOException 記事一覧ページの取得に失敗した場合
     */
    public MyKohoInfo inspectLatestIssue(MyKohoInfo info) throws IOException {
        if (info.getKohoUrl() == null || info.getKohoUrl().isEmpty()) {
            return info;
        }
        String html = fetchHtml(info.getKohoUrl());
        sleep();
        return info.withMedia(findPdfUrl(html), findAudioUrl(html));
    }

    /**
     * 自治体ページのHTMLから最新号記事一覧URLを抽出する。
     * &lt;div class="jichitaiPosts-More"&gt; 内の「最新号の記事を全部見る」リンクを対象とする。
     *
     * @param html 自治体ページのHTML
     * @return 最新号記事一覧URL。見つからない場合はnull
     */
    String findKohoUrl(String html) {
        Document doc = Jsoup.parse(html);
        Element more = doc.selectFirst("div.jichitaiPosts-More");
        if (more == null) {
            return null;
        }
        Element a = more.selectFirst("a.btn-primary.btn-arrow-white");
        return a == null ? null : a.absUrl("href");
    }

    /**
     * 最新号記事一覧ページのHTMLから広報URL（PDF版）を抽出する。
     * &lt;div class="kohoHeader-Btn"&gt; 内の「PDF版を見る」リンクを対象とする。
     *
     * @param html 最新号記事一覧ページのHTML
     * @return 広報URL（PDF版）。見つからない場合はnull
     */
    public String findPdfUrl(String html) {
        Document doc = Jsoup.parse(html);
        Element btn = doc.selectFirst("div.kohoHeader-Btn");
        if (btn == null) {
            return null;
        }
        Element a = btn.selectFirst("a.btn-thin-primary.btn-arrow-white");
        return a == null ? null : a.absUrl("href");
    }

    /**
     * 最新号記事一覧ページのHTMLから音声URLを抽出する。
     * &lt;audio id="audio_player"&gt; の src 属性を対象とする。
     *
     * @param html 最新号記事一覧ページのHTML
     * @return 音声URL（mp3）。見つからない場合はnull
     */
    String findAudioUrl(String html) {
        Document doc = Jsoup.parse(html);
        Element audio = doc.selectFirst("audio#audio_player");
        if (audio == null) {
            return null;
        }
        String src = audio.attr("src");
        return src == null || src.isEmpty() ? null : src;
    }

    /**
     * 最新号記事一覧URLからマイ広報紙コードを抽出する。
     * URLは https://mykoho.jp/koho/{地方公共団体コード}/{マイ広報紙コード} の形式。
     *
     * @param kohoUrl 最新号記事一覧URL
     * @return マイ広報紙コード。抽出できない場合はnull
     */
    String extractKohoCode(String kohoUrl) {
        Matcher matcher = KOHO_PATH_PATTERN.matcher(kohoUrl);
        return matcher.find() ? matcher.group(2) : null;
    }

    private void sleep() {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 指定された自治体の最新号広報紙PDFをダウンロードする。
     *
     * <p>処理手順:
     * <ol>
     *   <li>自治体ページ https://mykoho.jp/lg/{lgCode}/{myKohoCode} を取得</li>
     *   <li>HTMLから最新号記事一覧URLを抽出（findKohoUrl）</li>
     *   <li>最新号記事一覧ページを取得し、PDF版URLを抽出（findPdfUrl）</li>
     *   <li>PDF URLからファイルをダウンロード</li>
     * </ol>
     * </p>
     *
     * @param lgCode     地方公共団体コード
     * @param myKohoCode マイ広報紙自治体コード（自治体ページのID）
     * @param outputDir  保存先ディレクトリ
     * @param fileName   保存ファイル名（.pdf拡張子付き）
     * @return ダウンロードされたPDFファイル
     * @throws IOException PDF URLの取得またはダウンロードに失敗した場合
     */
    public File downloadPdf(String lgCode, String myKohoCode, File outputDir, String fileName)
            throws IOException {
        // 1. 自治体ページを取得し、最新号記事一覧URLを抽出
        String lgUrl = "https://mykoho.jp/lg/" + lgCode + "/" + myKohoCode;
        String lgHtml = fetchHtml(lgUrl);
        sleep();
        String kohoUrl = findKohoUrl(lgHtml);
        if (kohoUrl == null || kohoUrl.isEmpty()) {
            throw new IOException("最新号記事一覧URLが見つかりません: " + lgUrl);
        }

        // 2. 最新号記事一覧ページを取得し、PDF版URLを抽出
        String kohoHtml = fetchHtml(kohoUrl);
        sleep();
        String pdfUrl = findPdfUrl(kohoHtml);
        if (pdfUrl == null || pdfUrl.isEmpty()) {
            throw new IOException("PDF URL が見つかりません: " + kohoUrl);
        }

        // 3. PDFをダウンロード
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        File pdfFile = new File(outputDir, fileName);

        Request request = new Request.Builder().url(pdfUrl).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("PDF download failed: HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body for PDF: " + pdfUrl);
            }
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(pdfFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }

        return pdfFile;
    }

    /**
     * HTMLからPDF版URLを抽出する（staticユーティリティ）。
     * app モジュールからOkHttpなしで使える。
     *
     * @param html 広報紙ページのHTML
     * @return PDF版URL。見つからない場合はnull
     */
    public static String findPdfUrlFromHtml(String html) {
        Document doc = Jsoup.parse(html);
        Element btn = doc.selectFirst("div.kohoHeader-Btn");
        if (btn == null) {
            return null;
        }
        Element a = btn.selectFirst("a.btn-thin-primary.btn-arrow-white");
        return a == null ? null : a.absUrl("href");
    }

    /**
     * 指定された自治体の最新号広報紙をPDF・音声から取得し、EPUB3を生成する。
     *
     * <p>処理手順:
     * <ol>
     *   <li>自治体ページから最新号記事一覧URLを抽出</li>
     *   <li>記事一覧ページからPDF版URL・音声URL・号タイトルを抽出</li>
     *   <li>PDF・MP3をダウンロード</li>
     *   <li>PDFからテキストブロックを抽出（pdfbox）</li>
     *   <li>MP3から無音区間を検出して音声セクションを作成（jlayer）</li>
     *   <li>文字数と時間の比例配分でブロックと音声を突き合わせ</li>
     *   <li>EPUB3マルチメディアオーバーレイを生成</li>
     * </ol>
     * </p>
     *
     * @param lgCode     地方公共団体コード
     * @param myKohoCode マイ広報紙自治体コード（自治体ページのID）
     * @param outputDir  保存先ディレクトリ
     * @return 生成されたEPUBファイル
     * @throws IOException 取得・生成のいずれかに失敗した場合
     */
    public File downloadEpub(String lgCode, String myKohoCode, File outputDir) throws IOException {
        // 1. 自治体ページから最新号記事一覧URLを抽出
        String lgUrl = "https://mykoho.jp/lg/" + lgCode + "/" + myKohoCode;
        String lgHtml = fetchHtml(lgUrl);
        sleep();
        String kohoUrl = findKohoUrl(lgHtml);
        if (kohoUrl == null || kohoUrl.isEmpty()) {
            throw new IOException("最新号記事一覧URLが見つかりません: " + lgUrl);
        }

        // 2. 記事一覧ページからPDF・音声・タイトルを抽出
        String kohoHtml = fetchHtml(kohoUrl);
        sleep();
        String pdfUrl = findPdfUrl(kohoHtml);
        String audioUrl = findAudioUrl(kohoHtml);
        String title = findIssueTitle(kohoHtml);
        if (pdfUrl == null || pdfUrl.isEmpty()) {
            throw new IOException("PDF URL が見つかりません: " + kohoUrl);
        }
        if (audioUrl == null || audioUrl.isEmpty()) {
            throw new IOException("音声URLが見つかりません: " + kohoUrl);
        }

        // 3. PDF・MP3をダウンロード
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("出力ディレクトリの作成に失敗しました: " + outputDir);
        }
        File pdfFile = new File(outputDir, myKohoCode + ".pdf");
        File mp3File = new File(outputDir, myKohoCode + ".mp3");
        downloadFile(pdfUrl, pdfFile);
        sleep();
        downloadFile(audioUrl, mp3File);

        // 4. PDFからテキストブロックを抽出
        List<TextBlock> blocks = PdfBlockExtractor.extract(pdfFile);
        if (blocks.isEmpty()) {
            throw new IOException("PDFからテキストを抽出できませんでした: " + pdfFile);
        }

        // 4b. PDFからページ画像をレンダリング（低DPI）
        java.util.Map<Integer, byte[]> pageImages = PdfPageRenderer.render(pdfFile, 72);

        // 5. MP3から音声セクションを検出
        List<AudioSection> sections = Mp3SectionDetector.detect(mp3File, 100.0, 500, 20);
        if (sections.isEmpty()) {
            throw new IOException("MP3から音声セクションを検出できませんでした: " + mp3File);
        }

        // 6. 突き合わせ
        TextAudioAligner.align(blocks, sections);

        // 7. EPUB生成
        File epubFile = new File(outputDir, myKohoCode + ".epub");
        return new PdfEpubConverter().convert(blocks, pageImages, mp3File, title, epubFile);
    }

    /**
     * 記事一覧ページのHTMLから号タイトルを抽出する。
     * &lt;h1 class="kohoHeader-Title"&gt; を対象とする。
     *
     * @param html 記事一覧ページのHTML
     * @return 号タイトル。見つからない場合はnull
     */
    String findIssueTitle(String html) {
        Document doc = Jsoup.parse(html);
        Element h1 = doc.selectFirst("h1.kohoHeader-Title");
        if (h1 != null && !h1.text().trim().isEmpty()) {
            return h1.text().trim();
        }
        String title = doc.title();
        return title != null && !title.trim().isEmpty() ? title.trim() : null;
    }

    /**
     * 指定URLのファイルをダウンロードして保存する。
     */
    private void downloadFile(String url, File destFile) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Download failed: " + url + " HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body: " + url);
            }
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    /**
     * 使用したHTTPクライアントの接続・スレッドを解放する。
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
