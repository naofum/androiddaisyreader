package org.androiddaisyreader.aozoraconvert;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.androiddaisyreader.aozoraconvert.model.AozoraBook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 青空文庫にアクセスするためのクライアントクラス。
 * カタログCSVのダウンロード・パースおよび図書のダウンロードを行う。
 *
 * <p>使い方:
 * <pre>
 *   try (AozoraClient client = new AozoraClient()) {
 *       List&lt;AozoraBook&gt; catalog = client.downloadCatalog();
 *       File epubDir = client.downloadBook(1245, 46511, outputDir);
 *   }
 * </pre>
 */
public class AozoraClient implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(AozoraClient.class);

    private static final String BASE_URL = "https://www.aozora.gr.jp";
    public static final String CATALOG_URL = BASE_URL + "/index_pages/list_person_all_utf8.zip";

    private static final Pattern XHTML_LINK_PATTERN =
            Pattern.compile("<a\\s+href=\"\\./files/(\\d+_\\d+\\.html)\".*?>.*?XHTML.*?</a>", Pattern.DOTALL);
    private static final Pattern ENCODING_PATTERN =
            Pattern.compile("(?:encoding|charset)\\s*=\\s*[\"']?([^\"'\\s;>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SRC_PATTERN =
            Pattern.compile("\\ssrc=\"([^\"]+)\"");
    private static final Pattern CSS_HREF_PATTERN =
            Pattern.compile("href=\"(\\.\\./(\\.\\./)?(aozora|default)\\.css)\"");

    private final OkHttpClient httpClient;

    public AozoraClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    /**
     * カタログCSV（ZIPアーカイブ）をダウンロードしてパースする。
     *
     * @return 青空文庫の図書リスト
     * @throws IOException ネットワークまたはパースエラー時
     */
    public List<AozoraBook> downloadCatalog() throws IOException {
        logger.info("Downloading catalog from: {}", CATALOG_URL);

        Request request = new Request.Builder().url(CATALOG_URL).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download catalog: HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body");
            }
            try (InputStream is = body.byteStream();
                 ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().endsWith(".csv")) {
                        return parseCatalogCsv(zis);
                    }
                }
            }
        }
        throw new IOException("No CSV file found in catalog archive");
    }

    /**
     * CSVストリームをパースしてAozoraBookリストを返す。
     * ヘッダ: 人物ID,著者名,作品ID,作品名,仮名遣い種別,翻訳者名等,入力者名,校正者名,状態,状態の開始日,底本名,出版社名,...
     */
    List<AozoraBook> parseCatalogCsv(InputStream is) throws IOException {
        List<AozoraBook> books = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

        // ヘッダ行をスキップ
        String header = reader.readLine();
        if (header == null) return books;

        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = parseCsvLine(line);
            if (fields.length < 12) continue;

            try {
                int authorId = Integer.parseInt(fields[0].trim());
                String authorName = fields[1].trim();
                int workId = Integer.parseInt(fields[2].trim());
                String workName = fields[3].trim();
                String kanaType = fields[4].trim();
                // fields[5]: 翻訳者名等, [6]: 入力者名, [7]: 校正者名
                String status = fields[8].trim();
                String statusDate = fields[9].trim();
                // fields[10]: 底本名
                String publisher = fields[11].trim();

                books.add(new AozoraBook(authorId, authorName, workId, workName,
                        kanaType, status, statusDate, publisher));
            } catch (NumberFormatException e) {
                // 不正な行はスキップ
                logger.debug("Skipping invalid CSV line: {}", line);
            }
        }

        logger.info("Parsed {} books from catalog", books.size());
        return books;
    }

    /**
     * CSV行をパースする（ダブルクォート内のカンマを考慮）。
     */
    String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++; // エスケープされたダブルクォート
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    /**
     * 図書カードHTMLから本文XHTMLのURLを取得する。
     *
     * @param authorId 人物ID
     * @param workId   作品ID
     * @return 本文XHTMLの完全URL
     * @throws IOException ネットワークエラーまたはURL未発見時
     */
    public String getXhtmlUrl(int authorId, int workId) throws IOException {
        String cardUrl = String.format("%s/cards/%06d/card%d.html", BASE_URL, authorId, workId);
        logger.info("Fetching book card: {}", cardUrl);

        Request request = new Request.Builder().url(cardUrl).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch book card: HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body");
            }
            String html = body.string();

            // XHTML版リンクを探す
            Matcher matcher = XHTML_LINK_PATTERN.matcher(html);
            if (matcher.find()) {
                String relPath = matcher.group(1);
                return String.format("%s/cards/%06d/files/%s", BASE_URL, authorId, relPath);
            }

            // Jsoupでも試行（正規表現で取れない場合のフォールバック）
            Document doc = Jsoup.parse(html);
            Elements links = doc.select("a[href*=files/]");
            for (Element link : links) {
                String href = link.attr("href");
                String text = link.text();
                if (text.contains("XHTML") || href.endsWith(".html")) {
                    if (href.startsWith("./")) {
                        href = href.substring(2);
                    }
                    return String.format("%s/cards/%06d/%s", BASE_URL, authorId, href);
                }
            }
        }

        throw new IOException("XHTML URL not found for author=" + authorId + " work=" + workId);
    }

    /**
     * 図書をダウンロードする。本文XHTMLとリソース（CSS, 画像）を一時ディレクトリに保存する。
     * 外字は ConvertUtil で UTF-8文字に変換し、エンコーディングはUTF-8に統一する。
     *
     * @param authorId  人物ID
     * @param workId    作品ID
     * @param outputDir 出力ディレクトリ（一時ファイル格納先）
     * @return 本文XHTML等が保存されたディレクトリ
     * @throws IOException ダウンロードエラー時
     */
    public File downloadBook(int authorId, int workId, File outputDir) throws IOException {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outputDir);
        }

        // 作業ディレクトリ
        File workDir = new File(outputDir, authorId + "_" + workId);
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw new IOException("Failed to create work directory: " + workDir);
        }

        // 1. XHTMLのURLを取得
        String xhtmlUrl = getXhtmlUrl(authorId, workId);
        logger.info("Downloading XHTML: {}", xhtmlUrl);

        // ベースURL（リソース解決用）
        String baseUrl = xhtmlUrl.substring(0, xhtmlUrl.lastIndexOf('/'));

        // 2. XHTMLをダウンロード
        Request request = new Request.Builder().url(xhtmlUrl).build();
        String xhtmlContent;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download XHTML: HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty XHTML response body");
            }

            // エンコーディング検出
            Charset charset = detectCharset(body, response);
            byte[] rawBytes = body.bytes();
            xhtmlContent = new String(rawBytes, charset);
        }

        // 3. エンコーディング宣言をUTF-8に書き換え
        xhtmlContent = xhtmlContent.replace("Shift_JIS", "UTF-8");
        xhtmlContent = xhtmlContent.replace("shift_jis", "UTF-8");

        // 4. scriptタグ除去
        xhtmlContent = xhtmlContent.replaceAll("<script[^>]*>.*?</script>", "");

        // 5. CSSパスを正規化
        Matcher cssMatcher = CSS_HREF_PATTERN.matcher(xhtmlContent);
        List<String> cssResources = new ArrayList<>();
        StringBuffer sb = new StringBuffer();
        while (cssMatcher.find()) {
            String originalPath = cssMatcher.group(1);
            String cssName = cssMatcher.group(3) + ".css";
            cssMatcher.appendReplacement(sb, "href=\"" + cssName + "\"");
            cssResources.add(baseUrl + "/" + originalPath);
        }
        cssMatcher.appendTail(sb);
        xhtmlContent = sb.toString();

        // 6. 画像パスの正規化 + 外字変換
        Matcher srcMatcher = SRC_PATTERN.matcher(xhtmlContent);
        List<String> imageResources = new ArrayList<>();
        sb = new StringBuffer();
        while (srcMatcher.find()) {
            String srcPath = srcMatcher.group(1);
            String[] pathElements = srcPath.split("/");
            String fileName = pathElements[pathElements.length - 1];

            // 外字変換
            String replacement = null;
            if (xhtmlContent.contains("class=\"gaiji\"")) {
                // gaiji画像タグ全体を探して変換
                Pattern gaijiPattern = Pattern.compile(
                        "<img\\s+src=\"" + Pattern.quote(srcPath) + "\"[^>]*class=\"gaiji\"[^>]*/>");
                Matcher gaijiMatcher = gaijiPattern.matcher(xhtmlContent);
                if (gaijiMatcher.find()) {
                    String converted = ConvertUtil.convert(fileName);
                    if (converted != null) {
                        // 外字画像をUTF-8文字に置換（あとで一括処理する）
                        replacement = converted;
                    }
                }
            }

            if (replacement == null) {
                // 通常の画像: パスを正規化してリソースリストに追加
                imageResources.add(baseUrl + "/" + srcPath);
                srcMatcher.appendReplacement(sb, " src=\"" + fileName + "\"");
            } else {
                srcMatcher.appendReplacement(sb, " src=\"" + fileName + "\"");
            }
        }
        srcMatcher.appendTail(sb);
        xhtmlContent = sb.toString();

        // 7. 外字画像タグの一括変換
        xhtmlContent = convertGaijiImages(xhtmlContent);

        // 8. XHTMLをUTF-8で保存
        File xhtmlFile = new File(workDir, "chapter1.xhtml");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(xhtmlFile), StandardCharsets.UTF_8)) {
            writer.write(xhtmlContent);
        }

        // 9. CSSリソースダウンロード
        for (String cssUrl : cssResources) {
            String cssFileName = cssUrl.substring(cssUrl.lastIndexOf('/') + 1);
            downloadResource(cssUrl, new File(workDir, cssFileName));
            sleep();
        }

        // 10. 画像リソースダウンロード（外字でないもの）
        for (String imgUrl : imageResources) {
            String imgFileName = imgUrl.substring(imgUrl.lastIndexOf('/') + 1);
            File imgFile = new File(workDir, imgFileName);
            if (!imgFile.exists()) {
                try {
                    downloadResource(imgUrl, imgFile);
                    sleep();
                } catch (IOException e) {
                    logger.warn("Failed to download image: {}", imgUrl);
                }
            }
        }

        logger.info("Book download complete: {}", workDir.getAbsolutePath());
        return workDir;
    }

    /**
     * 外字画像タグをUTF-8文字に変換する。
     * パターン: <img src="XXX.png" ... class="gaiji" />
     */
    String convertGaijiImages(String html) {
        Pattern gaijiPattern = Pattern.compile(
                "<img\\s+[^>]*src=\"([^\"]+)\"[^>]*class=\"gaiji\"[^>]*/?>",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = gaijiPattern.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String srcValue = matcher.group(1);
            String[] parts = srcValue.split("/");
            String fileName = parts[parts.length - 1];
            String converted = ConvertUtil.convert(fileName);
            if (converted != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(converted));
            } else {
                // 変換できない外字はそのまま残す
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);

        // class="gaiji" が src の前にある場合にも対応
        Pattern gaijiPattern2 = Pattern.compile(
                "<img\\s+[^>]*class=\"gaiji\"[^>]*src=\"([^\"]+)\"[^>]*/?>",
                Pattern.CASE_INSENSITIVE);
        matcher = gaijiPattern2.matcher(sb.toString());
        StringBuffer sb2 = new StringBuffer();
        while (matcher.find()) {
            String srcValue = matcher.group(1);
            String[] parts = srcValue.split("/");
            String fileName = parts[parts.length - 1];
            String converted = ConvertUtil.convert(fileName);
            if (converted != null) {
                matcher.appendReplacement(sb2, Matcher.quoteReplacement(converted));
            } else {
                matcher.appendReplacement(sb2, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb2);
        return sb2.toString();
    }

    /**
     * h1 と div 要素にID属性がない場合に連番IDを付与する。
     * リーダーがID属性でセクションをナビゲーションするため必要。
     * テキストを持たない空要素にはIDを付けない（リーダーでの空Part生成を回避）。
     */
    String assignElementIds(String html) {
        Document doc = Jsoup.parse(html);
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        doc.outputSettings().escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);
        doc.outputSettings().charset(java.nio.charset.StandardCharsets.UTF_8);

        int idNum = 1;
        for (Element el : doc.select("h1, div")) {
            if (el.id() != null && !el.id().isEmpty()) {
                continue; // 既にIDあり
            }
            // テキストコンテンツがない要素はスキップ
            if (el.text().trim().isEmpty()) {
                continue;
            }
            String prefix = "h1".equalsIgnoreCase(el.tagName()) ? "ops" : "id_";
            el.attr("id", prefix + idNum++);
        }
        // <html><head>...</head><body>...</body></html> のbody内部のみ返す
        return doc.html();
    }

    /**
     * レスポンスからエンコーディングを検出する。
     */
    private Charset detectCharset(ResponseBody body, Response response) {
        // Content-Typeヘッダーから検出
        String contentType = response.header("Content-Type", "");
        if (contentType != null) {
            Matcher m = ENCODING_PATTERN.matcher(contentType);
            if (m.find()) {
                String charsetName = m.group(1);
                try {
                    return Charset.forName(charsetName);
                } catch (Exception ignored) {}
            }
        }

        // デフォルトはShift_JIS（青空文庫のほとんどのファイル）
        return Charset.forName("Shift_JIS");
    }

    /**
     * リソースファイルをダウンロードして保存する。
     */
    private void downloadResource(String url, File outputFile) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download resource: " + url + " HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) return;

            try (InputStream is = body.byteStream();
                 FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    /**
     * robots.txt準拠のアクセス間隔を確保する。
     */
    private void sleep() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
