package org.androiddaisyreader.sapie;

import com.github.library.dodp.client.DodpClient;
import com.github.library.dodp.client.DodpConfiguration;
import com.github.library.dodp.exception.DodpException;
import com.github.library.dodp.model.ContentList;
import com.github.library.dodp.model.ContentMetadata;
import com.github.library.dodp.model.InputType;
import com.github.library.dodp.model.ReadingSystemAttributes;
import com.github.library.dodp.model.Resources;
import com.github.library.dodp.model.ServiceAttributes;

import org.androiddaisyreader.utils.Constants;
import org.w3c.dom.Element;

/**
 * サピエ DAISY Online サービス（DODP）へのアプリ側ファサード。
 *
 * <p>{@link DodpClient} をラップし、サピエ固有の初期化（日本語必須の端末情報送信、
 * 対応フォーマット指定）とエンドポイント設定をまとめる。10分のセッションタイムアウトは
 * DODPの autoRelogin（既定有効）＋資格情報保持で自動回復される。</p>
 *
 * <p>すべてのメソッドはネットワークI/Oを伴うため、呼び出しは必ずバックグラウンドスレッドで行うこと。</p>
 */
public final class SapieDodpClient implements AutoCloseable {

    // 端末情報（setReadingSystemAttributes）。サピエは日本語のみ対応。
    private static final String RS_MANUFACTURER = "AndroidDaisyReader";
    private static final String RS_MODEL = "AndroidDaisyReader";
    private static final String RS_VERSION = "1.0";
    private static final String UI_LANGUAGE = "ja";

    private final DodpClient client;
    private final String username;
    private final String password;
    private boolean initialized;

    public SapieDodpClient(String username, String password) {
        this(username, password, resolveEndpoint());
    }

    public SapieDodpClient(String username, String password, String endpoint) {
        this.username = username;
        this.password = password;
        DodpConfiguration config = DodpConfiguration.builder(endpoint).build();
        this.client = new DodpClient(config);
    }

    /**
     * 使用するエンドポイントを決定する。
     * STAGING（BuildConfig 経由、local.properties/環境変数由来）が設定されていればそれを、
     * 無ければ本番を使う。値はソース・Git履歴に含めず、ビルド時に注入される。
     */
    private static String resolveEndpoint() {
        return Constants.SAPIE_DODP_ENDPOINT;
    }

    /**
     * サピエの端末情報属性を構築する。日本語UI必須、対応DAISYフォーマットを指定。
     */
    private static ReadingSystemAttributes buildReadingSystemAttributes() {
        return ReadingSystemAttributes.builder(RS_MANUFACTURER, RS_MODEL, RS_VERSION)
                .preferredUILanguage(UI_LANGUAGE)
                .addContentFormat("Daisy 2.02")
                .addContentFormat("ANSI/NISO Z39.86-2002")
                .addContentFormat("ANSI/NISO Z39.86-2005")
                .addInputType(InputType.TEXT_ALPHANUMERIC)
                .build();
    }

    /**
     * ログオンのみを行う（設定画面のログイン確認用）。
     *
     * @return 認証成功なら true
     */
    public boolean logOn() throws DodpException {
        return client.logOn(username, password);
    }

    /**
     * ログオン＋初期化シーケンス（getServiceAttributes → setReadingSystemAttributes）を実行する。
     * 各操作の前提となる初期化を一度だけ行う。
     *
     * @return サービス能力
     */
    public ServiceAttributes login() throws DodpException {
        if (!client.getSession().isLoggedIn()) {
            boolean ok = client.logOn(username, password);
            if (!ok) {
                return null;
            }
        }
        ServiceAttributes attributes = client.initialize(buildReadingSystemAttributes());
        initialized = true;
        return attributes;
    }

    private void ensureInitialized() throws DodpException {
        if (!initialized || !client.getSession().isInitialized()) {
            login();
        }
    }

    public boolean logOff() throws DodpException {
        try {
            return client.logOff();
        } finally {
            initialized = false;
        }
    }

    // ------------------------------------------------------------------
    // メニュー（BROWSE / 動的メニュー）
    // ------------------------------------------------------------------

    /**
     * 動的メニューを取得する。raw な {@code questions} 要素を返す
     * （{@link org.androiddaisyreader.sapie.menu.QuestionParser} で解析する）。
     *
     * @param userResponses getQuestions のパラメータ（questionID / value を含む）
     */
    public Element getQuestions(com.github.library.dodp.model.UserResponses userResponses)
            throws DodpException {
        ensureInitialized();
        return client.getQuestions(userResponses);
    }

    // ------------------------------------------------------------------
    // 一覧・詳細・リソース
    // ------------------------------------------------------------------

    public ContentList getContentList(String id, int firstItem, int lastItem) throws DodpException {
        ensureInitialized();
        return client.getContentList(id, firstItem, lastItem);
    }

    public ContentList getContentList(String id) throws DodpException {
        ensureInitialized();
        return client.getContentList(id);
    }

    public ContentMetadata getContentMetadata(String contentId) throws DodpException {
        ensureInitialized();
        return client.getContentMetadata(contentId);
    }

    public Resources getContentResources(String contentId) throws DodpException {
        ensureInitialized();
        return client.getContentResources(contentId);
    }

    // ------------------------------------------------------------------
    // 貸出・返却
    // ------------------------------------------------------------------

    public boolean issueContent(String contentId) throws DodpException {
        ensureInitialized();
        return client.issueContent(contentId);
    }

    public boolean returnContent(String contentId) throws DodpException {
        ensureInitialized();
        return client.returnContent(contentId);
    }

    /**
     * 下位の {@link DodpClient} を取得する（高度な用途向け）。
     */
    public DodpClient getDodpClient() {
        return client;
    }

    @Override
    public void close() {
        client.close();
    }
}
