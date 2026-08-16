package org.androiddaisyreader.chattyconvert;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.androiddaisyreader.chattyconvert.exception.ChattyLibraryException;
import org.androiddaisyreader.chattyconvert.model.Book;
import org.androiddaisyreader.chattyconvert.model.BookDetail;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Chatty Libraryにアクセスするためのファサードクラス。
 * ログイン、検索、図書詳細取得、MY本箱操作、ダウンロードの機能を提供する。
 *
 * <p>スレッドセーフ: このクラスのインスタンスは複数スレッドから安全に使用できる。
 * ただし、各操作はsynchronizedで直列化される。</p>
 */
public class ChattyLibraryClient implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(ChattyLibraryClient.class);

    private static final String BASE_URL = "https://chattylib.com";
    private static final String LOGIN_URL = BASE_URL + "/library/login";
    private static final String BOOKS_URL = BASE_URL + "/library/books";
    private static final String CHATTYBOX_URL = BASE_URL + "/chattybox/";

    private final String loginId;
    private final String password;
    private final OkHttpClient httpClient;
    private boolean loggedIn;

    /**
     * ChattyLibraryClientを構築する。
     *
     * @param loginId  ログインID
     * @param password パスワード
     */
    public ChattyLibraryClient(String loginId, String password) {
        this(loginId, password, false, null, 0);
    }

    /**
     * ChattyLibraryClientを構築する。
     *
     * @param loginId          ログインID
     * @param password         パスワード
     * @param trustAllCerts    SSL証明書検証をスキップする（プロキシ環境用）
     */
    public ChattyLibraryClient(String loginId, String password, boolean trustAllCerts) {
        this(loginId, password, trustAllCerts, null, 0);
    }

    /**
     * ChattyLibraryClientを構築する。
     *
     * @param loginId          ログインID
     * @param password         パスワード
     * @param trustAllCerts    SSL証明書検証をスキップする（プロキシ環境用）
     * @param proxyHost        プロキシホスト（nullの場合はプロキシなし）
     * @param proxyPort        プロキシポート
     */
    public ChattyLibraryClient(String loginId, String password, boolean trustAllCerts,
                               String proxyHost, int proxyPort) {
        this.loginId = loginId;
        this.password = password;
        this.loggedIn = false;

        // Cookie管理を行うCookieJar
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

        // プロキシ設定
        if (proxyHost != null && !proxyHost.isEmpty()) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        }

        if (trustAllCerts) {
            try {
                X509TrustManager trustManager = new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
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


    // ========== ログイン処理 ==========

    /**
     * Chatty Libraryにログインする。
     * CSRFトークンを取得してからログインPOSTを行う。
     *
     * @throws ChattyLibraryException ログイン失敗時
     */
    public synchronized void login() throws ChattyLibraryException {
        logger.info("ログイン処理を開始します: loginId={}", org.androiddaisyreader.util.LogMask.mask(loginId));

        try {
            // 1. GETでログインページを取得し、CSRFトークンを抽出
            Request getRequest = new Request.Builder().url(LOGIN_URL).get().build();
            String html;
            try (Response response = httpClient.newCall(getRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new ChattyLibraryException("ログインページの取得に失敗しました: HTTP " + response.code());
                }
                ResponseBody body = response.body();
                html = body != null ? body.string() : "";
            }

            Document doc = Jsoup.parse(html);
            Element metaCsrf = doc.selectFirst("meta[name=csrf-token]");
            if (metaCsrf == null) {
                throw new ChattyLibraryException("CSRFトークンが見つかりません");
            }
            String token = metaCsrf.attr("content");

            // 2. POSTでログイン
            FormBody formBody = new FormBody.Builder()
                    .add("_token", token)
                    .add("login_id", loginId)
                    .add("password", password)
                    .build();

            Request postRequest = new Request.Builder()
                    .url(LOGIN_URL)
                    .post(formBody)
                    .build();

            try (Response response = httpClient.newCall(postRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new ChattyLibraryException("ログインに失敗しました: HTTP " + response.code());
                }
            }

            loggedIn = true;
            logger.info("ログイン成功");
        } catch (IOException e) {
            throw new ChattyLibraryException("ログイン中にネットワークエラーが発生しました", e);
        }
    }

    private synchronized void ensureLoggedIn() throws ChattyLibraryException {
        if (!loggedIn) {
            login();
        }
    }


    // ========== 検索処理 ==========

    /**
     * 図書を検索する（全件取得）。
     * CSV形式のレスポンスをパースしてBookリストを返す。
     *
     * @return 検索結果のBookリスト
     * @throws ChattyLibraryException 検索失敗時
     */
    public synchronized List<Book> search() throws ChattyLibraryException {
        ensureLoggedIn();
        logger.info("検索処理を開始します");

        try {
            HttpUrl url = HttpUrl.parse(BOOKS_URL + "/export/csv").newBuilder()
                    .addQueryParameter("layout", "2")
                    .addQueryParameter("order", "registrants_count")
                    .addQueryParameter("dir", "desc")
                    .addQueryParameter("per", "1000")
                    .addQueryParameter("page", "1")
                    .addQueryParameter("freeWord", "")
                    .build();

            Request request = new Request.Builder().url(url).get().build();

            byte[] csvBytes;
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new ChattyLibraryException("検索に失敗しました: HTTP " + response.code());
                }
                ResponseBody body = response.body();
                csvBytes = body != null ? body.bytes() : new byte[0];
            }

            // MS932 (Shift_JIS) でデコード
            String csvContent = new String(csvBytes, Charset.forName("MS932"));
            return parseCsv(csvContent);
        } catch (IOException e) {
            throw new ChattyLibraryException("検索中にネットワークエラーが発生しました", e);
        }
    }

    /**
     * CSV文字列をパースしてBookリストに変換する。
     * 形式: カンマ区切り、ダブルクォーテーション囲みあり、ヘッダ行あり。
     */
    List<Book> parseCsv(String csvContent) throws ChattyLibraryException {
        List<Book> books = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            // ヘッダ行をスキップ
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return books;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> fields = parseCsvLine(line);
                if (fields.size() < 5) {
                    logger.warn("不正なCSV行をスキップします: {}", line);
                    continue;
                }
                try {
                    int id = Integer.parseInt(fields.get(0).trim());
                    Book book = new Book(id, fields.get(1).trim(), fields.get(2).trim(),
                            fields.get(3).trim(), fields.get(4).trim());
                    books.add(book);
                } catch (NumberFormatException e) {
                    logger.warn("図書IDのパースに失敗しました: {}", fields.get(0));
                }
            }
        } catch (IOException e) {
            throw new ChattyLibraryException("CSVのパースに失敗しました", e);
        }
        logger.info("検索結果: {}件", books.size());
        return books;
    }

    /**
     * CSV行をフィールドに分割する。ダブルクォーテーション囲みに対応。
     */
    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }


    // ========== 図書詳細情報取得 ==========

    /**
     * 図書の詳細情報を取得する。
     *
     * @param bookId 図書ID
     * @return 図書詳細情報
     * @throws ChattyLibraryException 取得失敗時
     */
    public synchronized BookDetail getBookDetail(int bookId) throws ChattyLibraryException {
        ensureLoggedIn();
        logger.info("図書詳細情報を取得します: bookId={}", bookId);

        try {
            String url = BOOKS_URL + "/" + bookId + "/show";
            Request request = new Request.Builder().url(url).get().build();

            String html;
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new ChattyLibraryException("図書詳細の取得に失敗しました: HTTP " + response.code());
                }
                ResponseBody body = response.body();
                html = body != null ? body.string() : "";
            }

            return parseBookDetail(bookId, html);
        } catch (IOException e) {
            throw new ChattyLibraryException("図書詳細取得中にネットワークエラーが発生しました", e);
        }
    }

    /**
     * 図書詳細HTMLをパースしてBookDetailを構築する。
     */
    BookDetail parseBookDetail(int bookId, String html) {
        Document doc = Jsoup.parse(html);
        BookDetail.Builder builder = new BookDetail.Builder().bookId(bookId);

        // bookInfoListから各項目を抽出
        Elements items = doc.select("ul.bookInfoList li.bookInfoListItem");
        for (Element item : items) {
            Element heading = item.selectFirst("p.heading");
            Element contents = item.selectFirst("p.contents");
            if (heading == null || contents == null) {
                continue;
            }
            String headingText = heading.text().trim();
            String contentsText = contents.text().trim();

            switch (headingText) {
                case "著者名":
                    builder.author(contentsText);
                    break;
                case "出版社名":
                    builder.publisher(contentsText);
                    break;
                case "説明文":
                    builder.description(contentsText);
                    break;
                case "ページ数":
                    builder.pages(contentsText);
                    break;
                case "著作権":
                    builder.copyright(contentsText);
                    break;
                case "ISBN":
                    builder.isbn(contentsText);
                    break;
                case "図書発行日":
                    builder.issueDate(contentsText);
                    break;
                case "製作者名":
                    builder.producer(contentsText);
                    break;
                default:
                    break;
            }
        }

        // MY本箱ステータス判定
        Element disabledBtn = doc.selectFirst("button.ChattyBoxBtn[disabled]");
        if (disabledBtn != null) {
            // 既にMY本箱に入っている → 読む権限あり
            builder.inMyBox(true);
            builder.readable(true);
        } else {
            builder.inMyBox(false);
            // 「MY本箱に入れる」ボタン（<i class="bi bi-book"></i>を含むform）があるか
            Element chattyBoxForm = doc.selectFirst("form button.ChattyBoxBtn");
            if (chattyBoxForm != null) {
                // MY本箱に入れるボタンがある → 読む権限あり
                builder.readable(true);
                Element tokenInput = doc.selectFirst("form input[name=_token]");
                if (tokenInput != null) {
                    builder.token(tokenInput.attr("value"));
                }
            } else {
                // MY本箱に入れるボタンがない → 読む権限なし
                builder.readable(false);
            }
        }

        return builder.build();
    }


    // ========== MY本箱操作 ==========

    /**
     * 図書をMY本箱に入れる。
     * 事前にgetBookDetailでトークンを取得している必要がある。
     *
     * @param bookId 図書ID
     * @param token  CSRFトークン（BookDetail.getToken()で取得）
     * @throws ChattyLibraryException 操作失敗時
     */
    public synchronized void addToMyBox(int bookId, String token) throws ChattyLibraryException {
        ensureLoggedIn();
        logger.info("MY本箱に入れます: bookId={}", bookId);

        try {
            String url = BOOKS_URL + "/" + bookId + "/read";

            FormBody formBody = new FormBody.Builder()
                    .add("_method", "POST")
                    .add("_token", token)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(formBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new ChattyLibraryException("MY本箱への追加に失敗しました: HTTP " + response.code());
                }
            }
            logger.info("MY本箱への追加が完了しました: bookId={}", bookId);
        } catch (IOException e) {
            throw new ChattyLibraryException("MY本箱追加中にネットワークエラーが発生しました", e);
        }
    }

    /**
     * MY本箱から図書を削除する。
     *
     * @param myBoxBookId MY本箱での図書ID
     * @param token       CSRFトークン
     * @throws ChattyLibraryException 操作失敗時
     */
    public synchronized void removeFromMyBox(int myBoxBookId, String token) throws ChattyLibraryException {
        ensureLoggedIn();
        logger.info("MY本箱から削除します: myBoxBookId={}", myBoxBookId);

        try {
            String url = BASE_URL + "/chattybox/books/delete/" + myBoxBookId;

            FormBody formBody = new FormBody.Builder()
                    .add("_token", token)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(formBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new ChattyLibraryException("MY本箱からの削除に失敗しました: HTTP " + response.code());
                }
            }
            logger.info("MY本箱からの削除が完了しました: myBoxBookId={}", myBoxBookId);
        } catch (IOException e) {
            throw new ChattyLibraryException("MY本箱削除中にネットワークエラーが発生しました", e);
        }
    }


    // ========== ダウンロード処理 ==========

    /**
     * 図書をダウンロードしてzipファイルとして保存する。
     * MY本箱に入れる → ダウンロード → zip圧縮 → MY本箱から削除 の一連の処理を行う。
     *
     * @param bookId    図書ID
     * @param outputDir 出力先ディレクトリ
     * @return 生成されたzipファイル
     * @throws ChattyLibraryException ダウンロード失敗時
     */
    public synchronized File download(int bookId, File outputDir) throws ChattyLibraryException {
        ensureLoggedIn();
        logger.info("ダウンロード処理を開始します: bookId={}", bookId);

        // 1. 図書詳細を取得してMY本箱に入れる
        BookDetail detail = getBookDetail(bookId);
        if (!detail.isReadable()) {
            throw new ChattyLibraryException("この本は読むことができません: bookId=" + bookId);
        }
        boolean wasAlreadyInMyBox = detail.isInMyBox();
        if (!wasAlreadyInMyBox) {
            if (detail.getToken() == null) {
                throw new ChattyLibraryException("MY本箱に追加するためのトークンが取得できません");
            }
            addToMyBox(bookId, detail.getToken());
        }

        try {
            // 2. MY本箱ページを取得してダウンロードURLとトークンを取得
            Request myBoxRequest = new Request.Builder().url(CHATTYBOX_URL).get().build();
            String myBoxHtml;
            try (Response response = httpClient.newCall(myBoxRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new ChattyLibraryException("MY本箱ページの取得に失敗しました: HTTP " + response.code());
                }
                ResponseBody body = response.body();
                myBoxHtml = body != null ? body.string() : "";
            }

            MyBoxEntry entry = findMyBoxEntry(myBoxHtml, bookId);
            if (entry == null) {
                throw new ChattyLibraryException("MY本箱に図書が見つかりません: bookId=" + bookId);
            }

            // 3. ダウンロードURLからメインHTMLを取得（UUIDをクエリパラメータに付与）
            String readUuid = java.util.UUID.randomUUID().toString();
            String readUrlWithUuid = entry.readUrl + "?read=" + readUuid;
            logger.info("図書コンテンツをダウンロードします: {}", readUrlWithUuid);
            Request readRequest = new Request.Builder()
                    .url(readUrlWithUuid)
                    .get()
                    .build();
            String contentHtml;
            try (Response response = httpClient.newCall(readRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new ChattyLibraryException("図書コンテンツの取得に失敗しました: HTTP " + response.code());
                }
                ResponseBody body = response.body();
                contentHtml = body != null ? body.string() : "";
            }

            // 以降のリクエストで使用する Referer URL
            String refererUrl = readUrlWithUuid;

            // 4. 一時ディレクトリにファイルをダウンロード
            File tempDir = new File(outputDir, "chattylib_temp_" + bookId);
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            // メインHTMLを保存
            writeStringToFile(new File(tempDir, "index.html"), contentHtml);

            // リンクされたリソースをすべてダウンロード
            // リソースのベースURLは /library/storage/book/{bookId}/reflow/
            String resourceBaseUrl = BASE_URL + "/library/storage/book/" + bookId + "/reflow/";
            downloadLinkedResources(contentHtml, resourceBaseUrl, tempDir, refererUrl);

            // scripts/smil.js で参照されている音声ファイル(mp3)をダウンロード
            downloadSmilAudio(resourceBaseUrl, tempDir, refererUrl);

            // 5. EPUB に変換
            File epubFile = new File(outputDir, bookId + ".epub");
            try {
                org.androiddaisyreader.chattyconvert.converter.ChattyEpubConverter converter =
                        new org.androiddaisyreader.chattyconvert.converter.ChattyEpubConverter();
                converter.convert(tempDir, epubFile);
                logger.info("EPUBファイルを作成しました: {}", epubFile.getAbsolutePath());
            } catch (org.androiddaisyreader.chattyconvert.exception.ChattyConvertException e) {
                // EPUB変換失敗時はzipにフォールバック
                logger.warn("EPUB変換に失敗しました。zipで保存します: {}", e.getMessage());
                epubFile = new File(outputDir, bookId + ".zip");
                createZip(tempDir, epubFile);
                logger.info("zipファイルを作成しました: {}", epubFile.getAbsolutePath());
            }

            // 6. 一時ディレクトリを削除
            deleteDirectory(tempDir);

            // 7. MY本箱から削除（もともと入っていた本は削除しない）
            if (!wasAlreadyInMyBox) {
                removeFromMyBox(entry.myBoxBookId, entry.deleteToken);
            }

            return epubFile;
        } catch (IOException e) {
            throw new ChattyLibraryException("ダウンロード中にエラーが発生しました", e);
        }
    }


    // ========== 内部ヘルパー ==========

    /**
     * MY本箱内のエントリ情報。
     */
    static class MyBoxEntry {
        final int myBoxBookId;
        final String readUrl;
        final String deleteToken;

        MyBoxEntry(int myBoxBookId, String readUrl, String deleteToken) {
            this.myBoxBookId = myBoxBookId;
            this.readUrl = readUrl;
            this.deleteToken = deleteToken;
        }
    }

    /**
     * MY本箱HTMLから指定図書IDに対応するエントリを見つける。
     * 表紙画像のURL（/library/storage/cover/{図書ID}/cover.png）で本来の図書IDと照合する。
     */
    MyBoxEntry findMyBoxEntry(String html, int bookId) {
        Document doc = Jsoup.parse(html);
        Elements books = doc.select("div.eachBook");

        for (Element book : books) {
            // 表紙画像のURLから図書IDを判定
            Element img = book.selectFirst("div.bookImg img[src]");
            if (img != null) {
                String src = img.attr("src");
                // /library/storage/cover/{図書ID}/cover.png のパターン
                if (src.contains("/cover/" + bookId + "/")) {
                    Element button = book.selectFirst("button.app_img[data-book-id]");
                    if (button == null) {
                        continue;
                    }
                    String readUrl = button.attr("data-book-read-url");
                    int myBoxBookId = extractMyBoxBookId(readUrl);

                    // 削除フォームのトークンを取得
                    Element deleteForm = book.selectFirst(
                            "form[action$=/chattybox/books/delete/" + myBoxBookId + "] input[name=_token]");
                    String deleteToken = deleteForm != null ? deleteForm.attr("value") : "";

                    return new MyBoxEntry(myBoxBookId, readUrl, deleteToken);
                }
            }
        }
        return null;
    }

    private int extractMyBoxBookId(String readUrl) {
        // URL: https://chattylib.com/chattybox/books/read/24529
        String[] parts = readUrl.split("/");
        return Integer.parseInt(parts[parts.length - 1]);
    }


    /**
     * HTML内のリンクされたリソースをすべてダウンロードする。
     * css, js, a href, img src, audio src を対象とする。
     * リソースのベースURLはHTMLの&lt;base&gt;タグまたはリンクの絶対パスから判定する。
     */
    private void downloadLinkedResources(String html, String baseUrl, File outputDir, String refererUrl) throws IOException {
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

        // <link href="..."> (CSS等)
        for (Element el : doc.select("link[href]")) {
            urls.add(el.absUrl("href"));
        }
        // <script src="...">
        for (Element el : doc.select("script[src]")) {
            urls.add(el.absUrl("src"));
        }
        // <a href="...">
        for (Element el : doc.select("a[href]")) {
            String href = el.absUrl("href");
            if (!href.isEmpty() && !href.startsWith("javascript:") && !href.startsWith("#")) {
                urls.add(href);
            }
        }
        // <img src="...">
        for (Element el : doc.select("img[src]")) {
            urls.add(el.absUrl("src"));
        }
        // <audio src="...">
        for (Element el : doc.select("audio[src]")) {
            urls.add(el.absUrl("src"));
        }
        // <audio> <source src="...">
        for (Element el : doc.select("audio source[src]")) {
            urls.add(el.absUrl("src"));
        }

        logger.info("ダウンロード対象リソース: {}件", urls.size());
        for (String resourceUrl : urls) {
            if (resourceUrl.isEmpty()) {
                continue;
            }
            try {
                downloadFile(resourceUrl, baseUrl, outputDir, refererUrl);
            } catch (IOException e) {
                logger.warn("リソースのダウンロードに失敗しました: {} - {}", resourceUrl, e.getMessage());
            }
        }
    }

    /**
     * ダウンロード済みのscripts/smil.jsを解析し、参照されている音声ファイル(mp3)をダウンロードする。
     * smil.jsが存在しない場合は何もしない。音声ファイルの取得に失敗した場合は警告をログに出力する。
     *
     * @param baseUrl リソースのベースURL（/library/storage/book/{bookId}/reflow/）
     * @param tempDir 一時ダウンロードディレクトリ
     * @throws IOException 入出力エラー時
     */
    private void downloadSmilAudio(String baseUrl, File tempDir, String refererUrl) throws IOException {
        File smilFile = new File(tempDir, "scripts/smil.js");
        if (!smilFile.exists()) {
            logger.warn("smil.jsが見つからないため音声ファイルを取得しません: {}", smilFile.getAbsolutePath());
            return;
        }
        String smilContent = new String(Files.readAllBytes(smilFile.toPath()), StandardCharsets.UTF_8);
        Set<String> audioSrcs = extractAudioSrcs(smilContent);
        if (audioSrcs.isEmpty()) {
            logger.info("smil.jsに音声ファイルの参照がありません");
            return;
        }
        logger.info("smil.jsから音声ファイルを取得します: {}件", audioSrcs.size());
        for (String src : audioSrcs) {
            String resolved = resolveUrl(baseUrl, src);
            if (resolved == null || resolved.isEmpty()) {
                continue;
            }
            try {
                downloadFile(resolved, baseUrl, tempDir, refererUrl);
            } catch (IOException e) {
                logger.warn("音声ファイルのダウンロードに失敗しました: {} - {}", resolved, e.getMessage());
            }
        }
    }

    /**
     * smil.jsのcAudioItemsから音声ファイルのパス（src）を抽出する。
     * refエントリは音声ファイルを持たないため対象外。重複は除去する。
     *
     * @param smilJsContent scripts/smil.jsの内容
     * @return 音声ファイルのパスの集合（例: ./sounds/sound00001.mp3）
     */
    Set<String> extractAudioSrcs(String smilJsContent) {
        Set<String> result = new HashSet<>();
        if (smilJsContent == null) {
            return result;
        }
        Pattern pattern = Pattern.compile("src:\\s*'([^']+)'");
        Matcher matcher = pattern.matcher(smilJsContent);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    /**
     * ベースURLに相対パスを解決して絶対URLを返す。
     *
     * @param baseUrl      ベースURL
     * @param relativePath 相対パス（例: ./sounds/sound00001.mp3）
     * @return 解決された絶対URL。解決できない場合はnull
     */
    private String resolveUrl(String baseUrl, String relativePath) {
        HttpUrl base = HttpUrl.parse(baseUrl);
        HttpUrl resolved = base != null ? base.resolve(relativePath) : null;
        return resolved != null ? resolved.toString() : null;
    }

    /**
     * 指定URLからファイルをダウンロードして出力先に保存する。
     * ベースURLからの相対パスを維持してディレクトリ構造を保つ。
     */
    private void downloadFile(String url, String baseUrl, File outputDir, String refererUrl) throws IOException {
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
     * ダウンロードURLからベースURLを基準にした相対パスを抽出する。
     * 例: baseUrl=https://example.com/storage/book/381/reflow/
     *     url=https://example.com/storage/book/381/reflow/css/style.css
     *     → css/style.css
     */
    private String extractRelativePath(String url, String baseUrl) {
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
    private String extractFileName(String url) {
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
     * ディレクトリ内のファイルをzipに圧縮する。
     */
    private void createZip(File sourceDir, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zipDirectory(sourceDir, sourceDir, zos);
        }
    }

    private void zipDirectory(File rootDir, File currentDir, ZipOutputStream zos) throws IOException {
        File[] files = currentDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                zipDirectory(rootDir, file, zos);
            } else {
                String entryName = rootDir.toPath().relativize(file.toPath()).toString()
                        .replace("\\", "/");
                zos.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private void writeStringToFile(File file, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * ディレクトリを再帰的に削除する。
     */
    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}
