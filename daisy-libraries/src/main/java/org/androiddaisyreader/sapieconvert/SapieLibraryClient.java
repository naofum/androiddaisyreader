package org.androiddaisyreader.sapieconvert;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.androiddaisyreader.sapieconvert.exception.SapieLibraryException;
import org.androiddaisyreader.sapieconvert.model.Book;
import org.androiddaisyreader.sapieconvert.model.SearchResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * サピエ図書館（https://library.sapie.or.jp）にアクセスするためのファサードクラス。
 * ログイン、デイジーデータ検索、ダウンロードの機能を提供する。
 *
 * <p>サピエ図書館の検索・一覧・ダウンロードは {@code /cgi-bin/CN1MN1} 一本のCGIで処理される。
 * レスポンスのHTMLは Shift_JIS で返されるため MS932 でデコードする。</p>
 */
public class SapieLibraryClient implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(SapieLibraryClient.class);

    private static final String LIBRARY_BASE_URL = "https://library.sapie.or.jp";
    private static final String MEMBER_BASE_URL = "https://member.sapie.or.jp";
    private static final String CN1MN1_URL = LIBRARY_BASE_URL + "/cgi-bin/CN1MN1";
    private static final String LOGIN_URL = MEMBER_BASE_URL + "/login";
    // ダウンロードフォームの送信先URLをMapに格納する際の予約キー。
    static final String FORM_ACTION_KEY = "__action__";

    // ゲストアクセス用の固定パラメータ（付けないと会員ログイン画面に遷移する）
    private static final String GUEST_S00102 = "dy3v$J$XP17";
    private static final String GUEST_S00103 = "UZQHCU46kd";

    // CN1MN1 のアクションコード
    private static final String ACT_LIST = "J01LST11"; // 検索結果一覧

    private static final int PAGE_SIZE = 50;

    private final String loginId;
    private final String password;
    private final OkHttpClient httpClient;
    private boolean loggedIn;

    public SapieLibraryClient(String loginId, String password) {
        this.loginId = loginId;
        this.password = password;
        this.loggedIn = false;
        this.httpClient = buildHttpClient();
    }

    private OkHttpClient buildHttpClient() {
        CookieJar cookieJar = new CookieJar() {
            private final List<Cookie> cookieStore =
                    Collections.synchronizedList(new ArrayList<>());

            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                cookieStore.addAll(cookies);
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                List<Cookie> result = new ArrayList<>();
                synchronized (cookieStore) {
                    cookieStore.removeIf(c -> c.expiresAt() < System.currentTimeMillis());
                    for (Cookie c : cookieStore) {
                        // www / library のサブドメイン間でセッションCookieを共有する
                        if (url.host().endsWith("sapie.or.jp")
                                && c.domain().endsWith("sapie.or.jp")) {
                            result.add(c);
                        }
                    }
                }
                return result;
            }
        };

        return new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    // ========== ログイン処理 ==========

    /**
     * サピエにログインする。
     * {@code POST https://www.sapie.or.jp/login} に uid / password を送信する。
     * ログイン失敗時は SapieLibraryException をスローする。
     *
     * @throws SapieLibraryException ログイン失敗時
     */
    public synchronized void login() throws SapieLibraryException {
        logger.info("サピエ図書館へのログインを開始します: loginId={}", org.androiddaisyreader.util.LogMask.mask(loginId));

        FormBody formBody = new FormBody.Builder()
                .add("uid", loginId)
                .add("password", password)
                .add("sapie_token", "0")
                .add("commit", "ログイン")
                .build();

        Request request = new Request.Builder()
                .url(LOGIN_URL)
                .post(formBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new SapieLibraryException("ログインに失敗しました: HTTP " + response.code());
            }
            ResponseBody body = response.body();
            String html = body != null ? body.string() : "";

            // ログイン失敗時はログインフォーム（uid入力欄）が再表示される
            Document doc = Jsoup.parse(html);
            Element uidInput = doc.selectFirst("input[name=uid], input#uid");
            if (uidInput != null) {
                throw new SapieLibraryException("ログインに失敗しました: IDまたはパスワードが正しくありません");
            }
        } catch (IOException e) {
            throw new SapieLibraryException("ログイン中にネットワークエラーが発生しました", e);
        }

        loggedIn = true;
        logger.info("ログイン成功");
    }

    private synchronized void ensureLoggedIn() throws SapieLibraryException {
        if (!loggedIn) {
            login();
        }
    }

    // ========== 検索処理 ==========

    /**
     * デイジーデータを検索する。
     *
     * @param title        タイトル検索語（空可）
     * @param author       著者検索語（空可）
     * @param page         取得ページ（1始まり）
     * @param sessionToken 検索セッショントークン（S00221）。初回は null / 空でよい
     * @param rtnme        RTNTME トークン。初回は null / 空でよい
     * @return 検索結果
     * @throws SapieLibraryException 検索失敗時
     */
    public synchronized SearchResult search(String title, String author, int page,
                                            String sessionToken, String rtnme)
            throws SapieLibraryException {
        logger.info("サピエ図書館を検索します: title={}, author={}, page={}", title, author, page);

        String query = buildSearchQuery(title, author, page, sessionToken, rtnme);
        String url = CN1MN1_URL + "?" + query;

        Request request = new Request.Builder().url(url).get().build();
        String html;
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new SapieLibraryException("検索に失敗しました: HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new SapieLibraryException("検索レスポンスが空です");
            }
            html = decodeResponseBody(body);
        } catch (IOException e) {
            throw new SapieLibraryException("検索中にネットワークエラーが発生しました", e);
        }

        return parseSearchResult(html, page);
    }

    private String buildSearchQuery(String title, String author, int page,
                                    String sessionToken, String rtnme) {
        StringBuilder sb = new StringBuilder();
        sb.append("S00101=").append(ACT_LIST);
        sb.append("&S00102=").append(GUEST_S00102);
        sb.append("&S00103=").append(GUEST_S00103);
        if (title != null && !title.trim().isEmpty()) {
            sb.append("&S00251=").append(encodeMs932(title));
        }
        if (author != null && !author.trim().isEmpty()) {
            sb.append("&S00252=").append(encodeMs932(author));
        }
        sb.append("&S00212=O");
        if (page > 1) {
            sb.append("&S00222=").append(page);
        }
        if (sessionToken != null && !sessionToken.isEmpty()) {
            sb.append("&S00221=").append(sessionToken);
        }
        if (rtnme != null && !rtnme.isEmpty()) {
            sb.append("&RTNTME=").append(rtnme);
        }
        return sb.toString();
    }

    /**
     * 検索結果HTMLをパースしてSearchResultに変換する。
     * 一覧テーブル（table.FULL）の各行と、ページング情報を抽出する。
     */
    SearchResult parseSearchResult(String html, int requestedPage) {
        Document doc = Jsoup.parse(html);

        // 該当件数（例: 該当件数：104件）
        int totalCount = 0;
        java.util.regex.Pattern countPattern = java.util.regex.Pattern.compile("(\\d+)\\s*件");
        Elements strongs = doc.select("strong");
        for (Element strong : strongs) {
            java.util.regex.Matcher countMatcher = countPattern.matcher(strong.text());
            if (countMatcher.find()) {
                try {
                    totalCount = Integer.parseInt(countMatcher.group(1));
                } catch (NumberFormatException ignored) {
                }
                break;
            }
        }

        // 図書一覧
        List<Book> books = new ArrayList<>();
        Element table = doc.selectFirst("table.FULL");
        if (table != null) {
            for (Element row : table.select("tbody tr")) {
                Elements cells = row.select("td");
                if (cells.size() < 7) {
                    continue;
                }
                String title = "";
                Element titleLink = cells.get(1).selectFirst("a[href]");
                if (titleLink != null) {
                    title = titleLink.text().trim();
                }

                // 各行のダウンロードボタンのフォーム(hidden input 一式)を抽出する。
                // 会員セッションでは download.aspx、ゲストでは CN1MN1 を指すため、
                // フォームの action も含めてそのまま保持し、ダウンロード時に辿る。
                Map<String, String> downloadForm = extractDownloadForm(row);

                // 図書IDは詳細リンク(J00DTL14)の S00222 から取得する。
                // ダウンロードフォームの S00222/S00224 はセッション状態で意味が変わる
                // （ゲストでは行番号など）ため当てにしない。
                String bookId = null;
                if (titleLink != null) {
                    bookId = extractQueryParam(titleLink.attr("href"), "S00222");
                }
                if (bookId == null || bookId.isEmpty()) {
                    continue;
                }
                String author = cells.get(2).text().trim();
                String type = extractType(cells.get(3));
                String time = cells.get(4).text().trim();
                String publisher = cells.get(5).text().trim();
                String library = cells.get(6).text().trim();
                books.add(new Book(bookId, title, author, type, time, publisher,
                        library, downloadForm));
            }
        }

        // ページングトークン（次ページ取得用）
        String sessionToken = extractInputValue(doc, "S00221");
        String rtnme = extractInputValue(doc, "RTNTME");

        // 次ページ有無: 50件/ページ
        boolean hasNext = requestedPage * PAGE_SIZE < totalCount;

        logger.info("検索結果: {}件（該当 {}件 / {}ページ目）", books.size(), totalCount, requestedPage);
        return new SearchResult(books, totalCount, requestedPage, hasNext, sessionToken, rtnme);
    }

    private String extractType(Element cell) {
        Element br2 = cell.selectFirst("span.BR2");
        if (br2 != null) {
            String text = br2.text().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return cell.text().trim();
    }

    private String extractInputValue(Document doc, String name) {
        Element input = doc.selectFirst("input[name=" + name + "]");
        return input != null ? input.attr("value") : "";
    }

    /**
     * 検索結果の1行から、ダウンロードボタン(「ダウン」submit)を持つフォームの
     * input を全て抽出する。会員セッションでは action=download.aspx(S00101=J31DWN21)、
     * ゲストセッションでは action=CN1MN1(S00101=J01LST31) など、
     * セッション状態によってフォームの内容が変わるため、行のフォームをそのまま保持する。
     *
     * <p>フォームの送信先URLは予約キー {@value #FORM_ACTION_KEY} に解決済み絶対URLとして格納する。</p>
     *
     * @param row テーブルの1行(tr)要素
     * @return input name -> value のマップ（順序保持）。フォームが無ければ空。
     */
    private Map<String, String> extractDownloadForm(Element row) {
        Map<String, String> params = new LinkedHashMap<>();
        // 「ダウン」submit を含むフォームを探す（行には他のフォームが無い前提だが堅牢に）
        Element form = null;
        for (Element f : row.select("form")) {
            if (f.selectFirst("input[type=submit]") != null) {
                form = f;
                break;
            }
        }
        if (form == null) {
            return params;
        }
        // action を絶対URLに解決して保持する
        String action = form.attr("action");
        HttpUrl base = HttpUrl.parse(CN1MN1_URL);
        HttpUrl resolved = (base != null && action != null && !action.isEmpty())
                ? base.resolve(action) : base;
        params.put(FORM_ACTION_KEY, resolved != null ? resolved.toString() : CN1MN1_URL);

        for (Element input : form.select("input")) {
            String type = input.attr("type");
            if ("submit".equalsIgnoreCase(type) || "button".equalsIgnoreCase(type)
                    || "image".equalsIgnoreCase(type)) {
                continue;
            }
            String name = input.attr("name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            params.put(name, input.attr("value"));
        }
        return params;
    }

    private String extractQueryParam(String url, String name) {
        if (url == null) {
            return null;
        }
        HttpUrl httpUrl = HttpUrl.parse(CN1MN1_URL);
        HttpUrl resolved = httpUrl != null ? httpUrl.resolve(url) : null;
        if (resolved == null) {
            return null;
        }
        return resolved.queryParameter(name);
    }

    // ========== ダウンロード処理 ==========

    /**
     * 検索結果の図書のダウンロードフォーム(download.aspx 用)をPOSTして
     * ファイルをダウンロード・保存する。
     *
     * <p>サピエのダウンロードは、検索結果一覧の各行に埋め込まれた
     * {@code <form action="https://cntdwn.sapie.or.jp/download/download.aspx">} を
     * そのままPOSTする仕組みになっている。フォームにはセッション固有の
     * S00102 / S00103 / RTNTME や図書ごとの S00215 / S00224 等が含まれる。
     * 検索時のゲストセッション(Cookie)のまま、そのフォームを送信するだけでよく、
     * 会員ログインや資料詳細ページの経由は不要。</p>
     *
     * @param downloadForm 検索結果から抽出したダウンロードフォームの hidden input 一式
     * @param outputDir    出力先ディレクトリ
     * @return 保存されたファイル
     * @throws SapieLibraryException ダウンロード失敗時
     */
    public synchronized File download(Map<String, String> downloadForm, File outputDir)
            throws SapieLibraryException {
        if (downloadForm == null || downloadForm.isEmpty()) {
            throw new SapieLibraryException("ダウンロードフォームが取得できていません。再検索してください。");
        }
        return submitDownloadForm(downloadForm, outputDir, 0);
    }

    private static final int MAX_DOWNLOAD_HOPS = 4;

    /**
     * ダウンロードフォームをPOSTする。レスポンスがファイルならそれを保存し、
     * HTML（次のダウンロードフォームを含む中間ページ、またはログイン要求）なら
     * 次のフォームを辿る/ログインして再試行する。
     */
    private File submitDownloadForm(Map<String, String> form, File outputDir, int hop)
            throws SapieLibraryException {
        if (hop > MAX_DOWNLOAD_HOPS) {
            throw new SapieLibraryException("ダウンロードの遷移が多すぎます（中断）");
        }

        String actionUrl = form.get(FORM_ACTION_KEY);
        if (actionUrl == null || actionUrl.isEmpty()) {
            actionUrl = CN1MN1_URL;
        }
        String bookId = form.containsKey("S00224") ? form.get("S00224") : "download";

        FormBody.Builder builder = new FormBody.Builder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (FORM_ACTION_KEY.equals(e.getKey())) {
                continue;
            }
            builder.add(e.getKey(), e.getValue() != null ? e.getValue() : "");
        }

        logger.info("ダウンロードフォーム送信(hop={}): url={}, params={}", hop, actionUrl, form.keySet());

        Request request = new Request.Builder()
                .url(actionUrl)
                .header("Referer", CN1MN1_URL)
                .post(builder.build())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new SapieLibraryException("ダウンロードに失敗しました: HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new SapieLibraryException("ダウンロードレスポンスが空です");
            }

            String contentType = response.header("Content-Type", "").toLowerCase();
            String disposition = response.header("Content-Disposition");
            boolean downloadable = contentType.contains("zip")
                    || contentType.contains("octet-stream")
                    || contentType.contains("pdf")
                    || contentType.contains("daisy")
                    || (disposition != null && disposition.toLowerCase().contains("attachment"));

            logger.info("ダウンロードレスポンス(hop={}): HTTP {}, Content-Type={}, Content-Disposition={}",
                    hop, response.code(), contentType, disposition);

            if (downloadable || (contentType.isEmpty() && disposition != null)) {
                return saveDownloadedFile(body, bookId, response, outputDir);
            }

            // HTMLが返った場合: ログイン要求 or 次のダウンロードフォームを含む中間ページ
            String html = decodeResponseBody(body);
            Document doc = Jsoup.parse(html);

            // ログインフォームが返ったらログインして同じフォームを再送信する
            if (doc.selectFirst("input[name=uid], input#uid") != null) {
                if (loggedIn) {
                    throw new SapieLibraryException("ログイン済みですがダウンロードにログインが要求されました");
                }
                logger.info("ダウンロードにログインが必要。ログインして再試行します");
                login();
                return submitDownloadForm(form, outputDir, hop + 1);
            }

            // 次のダウンロードフォーム（「ダウン」submit を持つフォーム）を探して辿る
            Map<String, String> nextForm = extractFormFromDoc(doc);
            if (!nextForm.isEmpty()) {
                logger.info("中間ページから次のダウンロードフォームを取得(hop={}): keys={}",
                        hop, nextForm.keySet());
                return submitDownloadForm(nextForm, outputDir, hop + 1);
            }

            // それ以外はエラー
            String serverMessage = extractErrorMessage(doc);
            String htmlSummary = summarizeHtml(doc);
            logger.error("ダウンロード失敗(hop={})。HTTP {}, CT={}, CD={}, 本文抜粋: {}",
                    hop, response.code(), contentType, disposition, htmlSummary);
            String diag = "[hop=" + hop + ", HTTP " + response.code() + ", CT=" + contentType
                    + ", CD=" + disposition + "] 本文抜粋: " + htmlSummary;
            if (serverMessage != null && !serverMessage.isEmpty()) {
                throw new SapieLibraryException("ダウンロードに失敗しました: " + serverMessage + " / " + diag);
            }
            throw new SapieLibraryException(
                    "ダウンロードに失敗しました（ファイルを取得できません） / " + diag);
        } catch (IOException e) {
            throw new SapieLibraryException("ダウンロード中にネットワークエラーが発生しました", e);
        }
    }

    /**
     * 任意のDocumentから「ダウン」submit を持つフォームを1つ抽出する。
     * 中間ページ上のダウンロードフォーム追跡に使う。
     */
    private Map<String, String> extractFormFromDoc(Document doc) {
        Map<String, String> params = new LinkedHashMap<>();
        Element form = null;
        for (Element f : doc.select("form")) {
            if (f.selectFirst("input[type=submit]") != null) {
                // ダウンロードらしきフォームを優先（action に download を含む、または submit値がダウン）
                Element submit = f.selectFirst("input[type=submit]");
                String submitVal = submit != null ? submit.attr("value") : "";
                String action = f.attr("action");
                if ((action != null && action.contains("download"))
                        || (submitVal != null && submitVal.contains("ダウン"))) {
                    form = f;
                    break;
                }
                if (form == null) {
                    form = f; // フォールバック
                }
            }
        }
        if (form == null) {
            return params;
        }
        String action = form.attr("action");
        HttpUrl base = HttpUrl.parse(CN1MN1_URL);
        HttpUrl resolved = (base != null && action != null && !action.isEmpty())
                ? base.resolve(action) : base;
        params.put(FORM_ACTION_KEY, resolved != null ? resolved.toString() : CN1MN1_URL);
        for (Element input : form.select("input")) {
            String type = input.attr("type");
            if ("submit".equalsIgnoreCase(type) || "button".equalsIgnoreCase(type)
                    || "image".equalsIgnoreCase(type)) {
                continue;
            }
            String name = input.attr("name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            params.put(name, input.attr("value"));
        }
        return params;
    }

    private File saveDownloadedFile(ResponseBody body, String bookId, Response response,
                                    File outputDir) throws IOException, SapieLibraryException {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new SapieLibraryException("出力ディレクトリを作成できません: " + outputDir);
        }

        String fileName = extractFileName(response, bookId);
        File outFile = new File(outputDir, fileName);

        try (InputStream in = body.byteStream();
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        logger.info("ダウンロード完了: {}", outFile.getAbsolutePath());
        return outFile;
    }

    private String extractFileName(Response response, String bookId) {
        String disposition = response.header("Content-Disposition");
        if (disposition != null) {
            // filename="xxx.zip" または filename*=UTF-8''xxx に対応
            String lower = disposition.toLowerCase();
            int idx = lower.indexOf("filename=");
            if (idx >= 0) {
                String value = disposition.substring(idx + "filename=".length()).trim();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (value.contains("''")) {
                    value = value.substring(value.indexOf("''") + 2);
                }
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return bookId + ".zip";
    }

    // ========== 内部ヘルパー ==========

    /**
     * サピエのエラーHTMLからメッセージらしきテキストを抽出する。
     * よくあるパターン（font color=red、class=error、em、strong 等）を順に探す。
     */
    private String extractErrorMessage(Document doc) {
        String[] selectors = {
                "font[color=red]", "font[color=#ff0000]",
                ".error", ".err", ".message", ".msg",
                "em", "p.caution"
        };
        for (String sel : selectors) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                String text = el.text().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        // 「ダウンロード」「エラー」「できません」等を含む行を探す
        for (Element el : doc.select("td, p, div, span")) {
            String text = el.ownText().trim();
            if (!text.isEmpty() && text.length() <= 120
                    && (text.contains("エラー") || text.contains("できません")
                        || text.contains("ありません") || text.contains("失敗"))) {
                return text;
            }
        }
        return null;
    }

    /**
     * HTML本文を短く要約してログ出力用に整形する（最大500文字）。
     */
    private String summarizeHtml(Document doc) {
        String text = doc.body() != null ? doc.body().text() : doc.text();
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }

    /**
     * レスポンスボディをShift_JIS(MS932)でデコードする。
     * 図書館ページはShift_JISで返される。
     */
    private String decodeResponseBody(ResponseBody body) throws IOException {
        byte[] bytes = body.bytes();
        return new String(bytes, "MS932");
    }

    /**
     * 文字列をShift_JISバイト列でURLエンコードする。
     * サピエ図書館の検索CGIはShift_JISのクエリパラメータを期待する。
     */
    private String encodeMs932(String value) {
        try {
            return URLEncoder.encode(value, "MS932");
        } catch (UnsupportedEncodingException e) {
            // MS932 は常に利用可能だが、念のためUTF-8にフォールバック
            try {
                return URLEncoder.encode(value, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                return value;
            }
        }
    }
}
