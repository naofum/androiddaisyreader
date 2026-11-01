package org.androiddaisyreader.apps;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.library.dodp.model.Announcements;
import com.github.library.dodp.model.ContentItem;
import com.github.library.dodp.model.ContentList;
import com.github.library.dodp.model.ContentMetadata;
import com.github.library.dodp.model.Resource;
import com.github.library.dodp.model.Resources;
import com.github.naofum.androiddaisyreader.R;

import org.androiddaisyreader.base.DaisyEbookReaderBaseActivity;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.sapie.SapieDodpClient;
import org.androiddaisyreader.sapie.menu.Choice;
import org.androiddaisyreader.sapie.menu.MenuController;
import org.androiddaisyreader.sapie.menu.QuestionResult;
import org.androiddaisyreader.sqlite.SQLiteBookmarkHelper;
import org.androiddaisyreader.sqlite.SQLiteCurrentInformationHelper;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.CacheHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;
import org.androiddaisyreader.utils.SapiePreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * サピエ図書館 DODP 専用メニュー画面。
 *
 * <p>BROWSE 方式（動的メニュー）を汎用ダイアログエンジンで辿り、検索結果一覧・ネット閲覧室から
 * 貸出（ダウンロード）・返却を行う。実際のメニュー階層・文言はサーバーが動的に返すため、
 * レスポンス種別（{@link QuestionResult}）に応じてビューを切り替える。</p>
 */
@SuppressLint("NewApi")
public class DaisyReaderSapieDodpActivity extends DaisyEbookReaderBaseActivity {

    private static final String TAG = "SapieDodp";

    // ボタンのダブルタップ判定に使う位置キー（リスト項目の position と衝突しない大きな値）
    private static final int CLICK_ID_ANNOUNCEMENTS = 100000;
    private static final int CLICK_ID_ISSUED_LIST = 100001;
    private static final int CLICK_ID_INPUT_SUBMIT = 100002;
    private static final int CLICK_ID_BACK = 100003;

    /** お知らせのプレビューの最大文字数。 */
    private static final int ANNOUNCEMENT_PREVIEW_MAX = 40;

    private enum ViewState { MENU, INPUT, CONTENT_LIST, ISSUED_LIST, ANNOUNCEMENTS }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SapieDodpClient client;
    private MenuController menu;
    private SQLiteDaisyBookHelper mSql;

    // UI
    private LinearLayout root;
    private TextView headerText;
    private ListView listView;
    private EditText inputField;
    private Button inputSubmit;
    private Button announcementsButton;
    private Button issuedListButton;
    private Button backButton;

    // 動的メニュー用の現在の選択肢
    private final List<Choice> currentChoices = new ArrayList<>();
    // 現在表示中メニューの質問ID（multipleChoiceQuestion の id）。選択時に value とともに送る。
    private String currentMenuQuestionId;
    // 一覧用の現在アイテム
    private final List<ContentItem> currentItems = new ArrayList<>();
    // お知らせ一覧用の現在アイテム
    private final List<Announcements.Announcement> currentAnnouncements = new ArrayList<>();
    // 入力質問の questionId
    private String currentInputQuestionId;
    // 現在表示中の検索結果一覧の listId（返却後の再取得用）
    private String currentContentListRef;
    private ViewState state = ViewState.MENU;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SapiePreferences.hasCredentials(this)) {
            Toast.makeText(this, getString(R.string.sapie_login_required), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        buildUi();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.sapie_books);

        mSql = SQLiteDaisyBookHelper.getInstance(this);

        String loginId = SapiePreferences.getLoginId(this);
        String password = SapiePreferences.getPassword(this);
        client = new SapieDodpClient(loginId, password);
        menu = new MenuController(client);

        openRootMenu();
    }

    /**
     * 画面をプログラマティックに構築する（ヘッダ＋リスト＋入力欄＋戻る）。
     */
    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        headerText = new TextView(this);
        headerText.setTextSize(18);
        headerText.setPadding(0, 0, 0, pad);
        root.addView(headerText);

        announcementsButton = new Button(this);
        announcementsButton.setText(R.string.sapie_announcements);
        setDoubleClickButton(announcementsButton, CLICK_ID_ANNOUNCEMENTS, this::loadAnnouncements);
        announcementsButton.setLayoutParams(createButtonLayoutParams());
        root.addView(announcementsButton);

        issuedListButton = new Button(this);
        issuedListButton.setText(R.string.sapie_list_issued);
        setDoubleClickButton(issuedListButton, CLICK_ID_ISSUED_LIST, this::loadIssuedList);
        issuedListButton.setLayoutParams(createButtonLayoutParams());
        root.addView(issuedListButton);

        // 入力欄（INPUT 状態でのみ表示）
        inputField = new EditText(this);
        inputField.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        inputField.setVisibility(View.GONE);
        // 設定画面の入力欄と同じ高さ・文字サイズに合わせる（アクセシビリティ）
        int inputHeight = getResources().getDimensionPixelSize(R.dimen.input_field_height);
        inputField.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, inputHeight));
        inputField.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.input_field_text_size));
        inputField.setGravity(Gravity.CENTER_VERTICAL);
        // IME の「検索」アクションで確定できるようにする
        inputField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        inputField.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSearch = actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN);
            if (isSearch) {
                submitInput();
                return true;
            }
            return false;
        });
        root.addView(inputField);

        inputSubmit = new Button(this);
        inputSubmit.setText(R.string.sapie_input_submit);
        inputSubmit.setVisibility(View.GONE);
        setDoubleClickButton(inputSubmit, CLICK_ID_INPUT_SUBMIT, this::submitInput);
        inputSubmit.setLayoutParams(createButtonLayoutParams());
        root.addView(inputSubmit);

        listView = new ListView(this);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listView.setLayoutParams(listParams);
        listView.setOnItemClickListener((parent, view, position, id) -> onListItemClicked(position));
        root.addView(listView);

        backButton = new Button(this);
        backButton.setText(R.string.sapie_menu_back);
        setDoubleClickButton(backButton, CLICK_ID_BACK, this::goBack);
        backButton.setLayoutParams(createButtonLayoutParams());
        root.addView(backButton);

        setContentView(root);
    }

    /**
     * ボタンをダブルタップで実行、シングルタップでラベル読み上げにする。
     * アプリ全体の操作感（ダブルタップで実行・シングルで読み上げ）と合わせる。
     *
     * @param button  対象ボタン
     * @param clickId ダブルタップ判定に使う一意の位置キー（リスト position と衝突しない値）
     * @param action  ダブルタップで実行する処理
     */
    private void setDoubleClickButton(final Button button, final int clickId,
                                      final Runnable action) {
        button.setOnClickListener(v -> {
            if (handleClickItem(clickId)) {
                action.run();
            } else {
                speakTextOnHandler(button.getText().toString());
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------
    // メニュー（動的ダイアログエンジン）
    // ------------------------------------------------------------------

    private void openRootMenu() {
        showLoading();
        executor.execute(() -> {
            try {
                QuestionResult result = menu.openRoot();
                runOnUiThread(() -> renderQuestion(result));
            } catch (Exception e) {
                reportError(e);
            }
        });
    }

    private void selectChoice(String questionId, String choiceId) {
        showLoading();
        executor.execute(() -> {
            try {
                QuestionResult result = menu.select(questionId, choiceId);
                runOnUiThread(() -> renderQuestion(result));
            } catch (Exception e) {
                reportError(e);
            }
        });
    }

    private void submitInput() {
        final String value = inputField.getText().toString().trim();
        final String questionId = currentInputQuestionId;
        if (questionId == null) {
            return;
        }
        showLoading();
        executor.execute(() -> {
            try {
                QuestionResult result = menu.submitInput(questionId, value);
                runOnUiThread(() -> renderQuestion(result));
            } catch (Exception e) {
                reportError(e);
            }
        });
    }

    private void goBack() {
        if (state == ViewState.ISSUED_LIST || state == ViewState.ANNOUNCEMENTS) {
            openRootMenu();
            return;
        }
        showLoading();
        executor.execute(() -> {
            try {
                QuestionResult result = menu.back();
                if (result == null) {
                    // これ以上戻れない → 画面を閉じる
                    runOnUiThread(this::finish);
                } else {
                    runOnUiThread(() -> renderQuestion(result));
                }
            } catch (Exception e) {
                reportError(e);
            }
        });
    }

    /**
     * QuestionResult の種別に応じてビューを切り替える（ステートマシンの描画側）。
     */
    private void renderQuestion(QuestionResult result) {
        switch (result.getType()) {
            case MULTIPLE_CHOICE:
                renderMultipleChoice(result);
                break;
            case INPUT:
                renderInput(result);
                break;
            case CONTENT_LIST:
                loadContentList(result.getContentListRef());
                break;
            case TEXT:
            default:
                renderText(result);
                break;
        }
    }

    private void renderMultipleChoice(QuestionResult result) {
        state = ViewState.MENU;
        currentChoices.clear();
        currentChoices.addAll(result.getChoices());
        currentMenuQuestionId = result.getQuestionId();
        showHeader(result.getText());
        setInputVisible(false);

        List<String> labels = new ArrayList<>();
        for (Choice c : result.getChoices()) {
            labels.add(c.getText() != null ? c.getText() : c.getId());
        }
        listView.setAdapter(new ArrayAdapter<>(this,
                R.layout.sapie_list_item, R.id.text1, labels));
        speakText(result.getText());
    }

    private void renderInput(QuestionResult result) {
        state = ViewState.INPUT;
        currentInputQuestionId = result.getQuestionId();
        showHeader(result.getText());
        setInputVisible(true);
        inputField.setText("");
        // 入力欄にフォーカスする（キーボードは自動表示しない）。GONE→VISIBLE 直後は
        // requestFocus が効かないことがあるため post でレイアウト後に実行する。
        inputField.post(() -> inputField.requestFocus());
        listView.setAdapter(new ArrayAdapter<>(this,
                R.layout.sapie_list_item, R.id.text1, new ArrayList<String>()));
        speakText(result.getText());
    }

    private void renderText(QuestionResult result) {
        state = ViewState.MENU;
        currentChoices.clear();
        setInputVisible(false);
        String text = result.getText() != null ? result.getText()
                : getString(R.string.sapie_no_items);
        showHeader(text);
        listView.setAdapter(new ArrayAdapter<>(this,
                R.layout.sapie_list_item, R.id.text1, new ArrayList<String>()));
        speakText(text);
    }

    // ------------------------------------------------------------------
    // 検索結果一覧・ネット閲覧室
    // ------------------------------------------------------------------

    private void loadContentList(final String listId) {
        currentContentListRef = listId;
        showLoading();
        executor.execute(() -> {
            try {
                ContentList list = client.getContentList(listId, 0, -1);
                runOnUiThread(() -> renderContentList(list));
            } catch (Exception e) {
                reportError(e);
            }
        });
    }

    private void renderContentList(ContentList list) {
        state = ViewState.CONTENT_LIST;
        setInputVisible(false);
        currentItems.clear();
        List<String> labels = new ArrayList<>();
        if (list != null && list.getItems() != null) {
            for (ContentItem item : list.getItems()) {
                currentItems.add(item);
                String label = item.getLabel() != null ? item.getLabel().getText() : null;
                labels.add(label != null ? label : item.getId());
            }
        }
        String header = (list != null && list.getLabel() != null && list.getLabel().getText() != null)
                ? list.getLabel().getText()
                : getString(R.string.sapie_books);
        showHeader(header);
        if (labels.isEmpty()) {
            labels.add(getString(R.string.sapie_no_items));
        }
        listView.setAdapter(new ArrayAdapter<>(this,
                R.layout.sapie_list_item, R.id.text1, labels));
        speakText(header);
    }

    private void loadIssuedList() {
        showLoading();
        executor.execute(() -> {
            try {
                ContentList list = client.getIssued();
                runOnUiThread(() -> renderIssuedList(list));
            } catch (Exception e) {
                reportError(e);
            }
        });
    }

    private void renderIssuedList(ContentList list) {
        state = ViewState.ISSUED_LIST;
        setInputVisible(false);
        currentItems.clear();
        List<String> labels = new ArrayList<>();
        if (list != null && list.getItems() != null) {
            for (ContentItem item : list.getItems()) {
                currentItems.add(item);
                String label = item.getLabel() != null ? item.getLabel().getText() : null;
                labels.add(label != null ? label : item.getId());
            }
        }
        String header = (list != null && list.getLabel() != null && list.getLabel().getText() != null)
                ? list.getLabel().getText()
                : getString(R.string.sapie_list_issued);
        showHeader(header);
        if (labels.isEmpty()) {
            labels.add(getString(R.string.sapie_no_items));
        }
        listView.setAdapter(new ArrayAdapter<>(this,
                R.layout.sapie_list_item, R.id.text1, labels));
        speakText(header);
    }

    // ------------------------------------------------------------------
    // お知らせ（Announcement）
    // ------------------------------------------------------------------

    private void loadAnnouncements() {
        showLoading();
        executor.execute(() -> {
            try {
                Announcements announcements = client.getServiceAnnouncements();
                runOnUiThread(() -> renderAnnouncements(announcements));
            } catch (Exception e) {
                // お知らせは非対応/取得失敗でも致命的ではないので空表示にする
                org.androiddaisyreader.utils.LogFile.e(TAG, "Sapie DODP announcements failed", e);
                runOnUiThread(() -> renderAnnouncements(
                        new Announcements(Collections.<Announcements.Announcement>emptyList())));
            }
        });
    }

    private void renderAnnouncements(Announcements announcements) {
        state = ViewState.ANNOUNCEMENTS;
        setInputVisible(false);
        currentItems.clear();
        currentAnnouncements.clear();
        List<String> labels = new ArrayList<>();
        if (announcements != null && announcements.getAnnouncements() != null) {
            // 重要（priority が小さいほど重要）が上に来るように並べる
            List<Announcements.Announcement> list =
                    new ArrayList<>(announcements.getAnnouncements());
            Collections.sort(list, (a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
            for (Announcements.Announcement a : list) {
                currentAnnouncements.add(a);
                labels.add(previewOf(a));
            }
        }
        // ヘッダーを「n件のお知らせがあります」/「新しいお知らせはありません」にし、
        // speakText(header) でそのまま読み上げる。
        final String header;
        if (labels.isEmpty()) {
            header = getString(R.string.sapie_announcements_none);
            labels.add(header);
        } else {
            header = getString(R.string.sapie_announcements_count, labels.size());
        }
        showHeader(header);
        listView.setAdapter(new ArrayAdapter<>(this,
                R.layout.sapie_list_item, R.id.text1, labels));
        speakText(header);
    }

    /**
     * お知らせのプレビュー（先頭1行、長ければ最大 {@value #ANNOUNCEMENT_PREVIEW_MAX} 文字＋…）を作る。
     * priority==1 は重要として接頭辞を付ける。
     */
    private String previewOf(Announcements.Announcement a) {
        String text = (a.getLabel() != null && a.getLabel().getText() != null)
                ? a.getLabel().getText() : "";
        String firstLine = text;
        int nl = firstLine.indexOf('\n');
        if (nl >= 0) {
            firstLine = firstLine.substring(0, nl);
        }
        firstLine = firstLine.replaceAll("\\s+", " ").trim();
        if (firstLine.length() > ANNOUNCEMENT_PREVIEW_MAX) {
            firstLine = firstLine.substring(0, ANNOUNCEMENT_PREVIEW_MAX) + "…";
        }
        if (firstLine.isEmpty()) {
            firstLine = a.getId() != null ? a.getId() : "";
        }
        String prefix = (a.getPriority() == 1) ? getString(R.string.sapie_important_prefix) : "";
        return prefix + firstLine;
    }

    /**
     * お知らせの全文をダイアログ表示＋TTSで読み上げ、既読にする。
     */
    private void showAnnouncement(final Announcements.Announcement a) {
        final String text = (a.getLabel() != null && a.getLabel().getText() != null)
                ? a.getLabel().getText() : "";
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.sapie_announcements)
                .setMessage(text)
                .setPositiveButton(R.string.ok, null)
                .show();
        speakText(text);
        // 既読マーク（失敗は無視）
        final String id = a.getId();
        if (id != null && !id.isEmpty()) {
            executor.execute(() -> {
                try {
                    client.markAnnouncementsAsRead(Collections.singletonList(id));
                } catch (Exception e) {
                    org.androiddaisyreader.utils.LogFile.e(TAG, "markAnnouncementsAsRead failed", e);
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // リスト項目クリック
    // ------------------------------------------------------------------

    private void onListItemClicked(int position) {
        boolean isDoubleTap = handleClickItem(position);
        if (state == ViewState.MENU) {
            if (position < 0 || position >= currentChoices.size()) {
                return;
            }
            Choice choice = currentChoices.get(position);
            if (isDoubleTap) {
                selectChoice(currentMenuQuestionId, choice.getId());
            } else {
                speakTextOnHandler(choice.getText());
            }
        } else if (state == ViewState.CONTENT_LIST) {
            if (position < 0 || position >= currentItems.size()) {
                return;
            }
            ContentItem item = currentItems.get(position);
            String label = item.getLabel() != null ? item.getLabel().getText() : item.getId();
            if (isDoubleTap) {
                confirmItemAction(item, label);
            } else {
                speakTextOnHandler(label);
            }
        } else if (state == ViewState.ISSUED_LIST) {
            if (position < 0 || position >= currentItems.size()) {
                return;
            }
            ContentItem item = currentItems.get(position);
            String label = item.getLabel() != null ? item.getLabel().getText() : item.getId();
            if (isDoubleTap) {
                confirmReturnAction(item, label);
            } else {
                speakTextOnHandler(label);
            }
        } else if (state == ViewState.ANNOUNCEMENTS) {
            if (position < 0 || position >= currentAnnouncements.size()) {
                return;
            }
            Announcements.Announcement announcement = currentAnnouncements.get(position);
            if (isDoubleTap) {
                showAnnouncement(announcement);
            } else {
                speakTextOnHandler(previewOf(announcement));
            }
        }
    }

    /**
     * 一覧項目のアクション選択（貸出）。
     * 返却は閲覧リスト（貸出中一覧）からのみ行う。
     */
    private void confirmItemAction(final ContentItem item, final String label) {
        CharSequence[] actions = new CharSequence[]{
                getString(R.string.sapie_borrow)
        };
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(this,
                R.layout.sapie_dialog_item, R.id.dialogItemText, actions);
        new android.app.AlertDialog.Builder(this)
                .setTitle(label)
                .setAdapter(adapter, (dialog, which) -> {
                    if (which == 0) {
                        borrowAndDownload(item, label);
                    }
                })
                .setNegativeButton(R.string.cancel_bookmark, null)
                .show();
    }

    /**
     * 貸出中一覧（閲覧リスト）項目の返却確認。
     */
    private void confirmReturnAction(final ContentItem item, final String label) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(label)
                .setPositiveButton(getString(R.string.sapie_return), (dialog, which) -> returnContent(item, label))
                .setNegativeButton(R.string.cancel_bookmark, null)
                .show();
    }

    // ------------------------------------------------------------------
    // 貸出（ダウンロード）
    // ------------------------------------------------------------------

    private void borrowAndDownload(final ContentItem item, final String label) {
        boolean isConnected = DaisyBookUtil.getConnectivityStatus(this)
                != Constants.CONNECT_TYPE_NOT_CONNECTED;
        if (!isConnected) {
            new org.androiddaisyreader.player.IntentController(this).pushToDialog(
                    getString(R.string.error_connect_internet),
                    getString(R.string.error_title), R.raw.error, false, false, null);
            return;
        }

        Toast.makeText(this, getString(R.string.message_downloading_file), Toast.LENGTH_SHORT).show();
        speakText(getString(R.string.message_downloading_file));

        final String contentId = item.getId();
        executor.execute(() -> {
            try {
                // 貸出登録
                boolean issued = client.issueContent(contentId);
                if (!issued) {
                    runOnUiThread(() -> {
                        speakText(getString(R.string.sapie_limit_reached));
                        Toast.makeText(DaisyReaderSapieDodpActivity.this,
                                getString(R.string.sapie_limit_reached), Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                ContentMetadata metadata = client.getContentMetadata(contentId);
                String title = resolveTitle(metadata, label);

                Resources resources = client.getContentResources(contentId);
                File downloadedFile = downloadResources(contentId, resources);
                if (downloadedFile == null) {
                    throw new java.io.IOException("No downloadable resource for " + contentId);
                }

                String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
                String fileName = safeTitle + fileExtensionOf(downloadedFile);
                String mimeType = fileName.endsWith(".zip") ? "application/zip" : "application/octet-stream";
                Uri savedUri = saveToDownloadsAndMediaStore(downloadedFile, fileName, mimeType);
                String savedPath = (savedUri != null) ? savedUri.toString() : downloadedFile.getAbsolutePath();

                // 図書IDとして Sapie の content ID を _id に採用する（返却時に厳密に特定できるように）。
                DaisyBookInfo info = new DaisyBookInfo(
                        "sapie:" + contentId,
                        title,
                        savedPath,
                        resolveAuthor(metadata),
                        "",
                        "",
                        0);
                mSql.addOrReplaceDaisyBookWithId(info, Constants.TYPE_DOWNLOADED_BOOK);
                DaisyBookUtil.addRecentBookToSQLite(info,
                        Constants.NUMBER_OF_RECENTBOOK_DEFAULT, mSql);

                downloadedFile.delete();
                showDownloadCompleteNotification(fileName, savedUri);

                runOnUiThread(() -> {
                    Toast.makeText(DaisyReaderSapieDodpActivity.this,
                            getString(R.string.message_download_complete), Toast.LENGTH_SHORT).show();
                    Intent downloaded = new Intent(DaisyReaderSapieDodpActivity.this,
                            DaisyReaderDownloadedBooks.class);
                    startActivity(downloaded);
                });
            } catch (Exception e) {
                org.androiddaisyreader.utils.LogFile.e(TAG, "Sapie DODP download failed", e);
                runOnUiThread(() -> {
                    speakText(getString(R.string.error_cannot_dowload));
                    showErrorWithLogSend(R.string.error_cannot_dowload, e,
                            createDodpErrorExtras("borrow"));
                });
            }
        });
    }

    /**
     * リソース一覧からすべてのファイルをダウンロードし、localURI の相対パス構造を保って
     * 1 つの zip に圧縮して返す。1 件の場合も zip にまとめる。
     */
    private File downloadResources(String contentId, Resources resources) throws java.io.IOException {
        if (resources == null || resources.getResources() == null || resources.getResources().isEmpty()) {
            return null;
        }

        org.androiddaisyreader.utils.LogFile.d(TAG, "Downloading " + resources.getResources().size()
                + " resource(s) for contentId=" + contentId);
        for (Resource r : resources.getResources()) {
            org.androiddaisyreader.utils.LogFile.d(TAG, "Resource: uri="
                    + org.androiddaisyreader.utils.LogFile.sanitizeUrl(r.getUri())
                    + " localURI=" + r.getLocalUri()
                    + " mimeType=" + r.getMimeType()
                    + " size=" + r.getSize());
        }

        File tempDir = new File(getCacheDir(), "sapie");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        String safeContentId = sanitizeFileName(contentId);
        File contentDir = new File(tempDir, safeContentId);
        if (contentDir.exists()) {
            deleteDirectory(contentDir);
        }
        contentDir.mkdirs();

        com.github.library.dodp.download.ContentDownloader downloader =
                new com.github.library.dodp.download.ContentDownloader();

        // 全リソースを localURI の相対パス構造を保ってダウンロード
        for (Resource resource : resources.getResources()) {
            String localName = resource.getLocalUri();
            if (localName == null || localName.isEmpty()) {
                localName = fallbackResourceName(resource);
            }
            File dest = new File(contentDir, sanitizePath(localName));
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            java.nio.file.Path result = downloader.download(resource, dest.toPath());
            if (result == null || !result.toFile().exists()) {
                throw new java.io.IOException("Failed to download resource: "
                        + org.androiddaisyreader.utils.LogFile.sanitizeUrl(resource.getUri()));
            }
        }

        // ダウンロードしたファイルを 1 つの zip に圧縮
        File zipFile = new File(tempDir, safeContentId + ".zip");
        createZipFromDirectory(contentDir, zipFile);

        // 一時ディレクトリを削除
        deleteDirectory(contentDir);

        return zipFile;
    }

    private String fallbackResourceName(Resource resource) {
        String uri = resource.getUri();
        if (uri != null && !uri.isEmpty()) {
            int slash = Math.max(uri.lastIndexOf('/'), uri.lastIndexOf('\\'));
            String name = slash >= 0 ? uri.substring(slash + 1) : uri;
            if (!name.isEmpty()) {
                return sanitizeFileName(name);
            }
        }
        return "resource";
    }

    private String sanitizeFileName(String name) {
        String base = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        return slash >= 0 ? base.substring(slash + 1) : base;
    }

    private String sanitizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.replaceAll("[\\\\*?\"<>|]", "_");
    }

    /**
     * ディレクトリ内のファイルを再帰的に zip に圧縮する。
     */
    private void createZipFromDirectory(File sourceDir, File zipFile) throws IOException {
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

    private void deleteDirectory(File dir) {
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

    private String resolveTitle(ContentMetadata metadata, String fallback) {
        if (metadata != null && metadata.getMetadata() != null) {
            String title = metadata.getMetadata().getTitle();
            if (title != null && !title.isEmpty()) {
                return title;
            }
        }
        return fallback != null ? fallback : "sapie";
    }

    private String resolveAuthor(ContentMetadata metadata) {
        if (metadata != null && metadata.getMetadata() != null) {
            List<String> creators = metadata.getMetadata().getCreators();
            if (creators != null && !creators.isEmpty()) {
                return creators.get(0);
            }
        }
        return "";
    }

    private String fileExtensionOf(File file) {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(idx) : ".zip";
    }

    // ------------------------------------------------------------------
    // 返却
    // ------------------------------------------------------------------

    private void returnContent(final ContentItem item, final String label) {
        executor.execute(() -> {
            try {
                boolean ok = client.returnContent(item.getId());
                if (ok) {
                    // 返却成功: ローカルのファイル・ダウンロード済一覧・最近の本・
                    // ブックマーク・現在位置・キャッシュを後始末する（失敗は無視）。
                    cleanupReturnedBook(item.getId());
                }
                runOnUiThread(() -> {
                    String msg = ok ? getString(R.string.sapie_returned)
                            : getString(R.string.sapie_return_failure);
                    Toast.makeText(DaisyReaderSapieDodpActivity.this, msg, Toast.LENGTH_SHORT).show();
                    speakText(msg);
                    if (ok) {
                        refreshCurrentList();
                    }
                });
            } catch (Exception e) {
                org.androiddaisyreader.utils.LogFile.e(TAG, "Sapie DODP return failed", e);
                runOnUiThread(() -> {
                    Toast.makeText(DaisyReaderSapieDodpActivity.this,
                            getString(R.string.sapie_return_failure), Toast.LENGTH_LONG).show();
                    showErrorWithLogSend(R.string.sapie_return_failure, e,
                            createDodpErrorExtras("return"));
                });
            }
        });
    }

    /**
     * 返却された書籍のローカルデータを削除する。
     *
     * <p>対象: ファイル（MediaStore エントリ or ローカルファイル）、ダウンロード済一覧、
     * 最近の本、ブックマーク、現在位置、アプリキャッシュ。</p>
     *
     * <p>各処理はベストエフォート。失敗しても返却自体は成功として扱い、ログのみ残す。</p>
     *
     * @param contentId 返却した書籍の Sapie content ID
     */
    private void cleanupReturnedBook(String contentId) {
        final String bookId = "sapie:" + contentId;
        String path = null;
        String title = null;

        // ダウンロード済エントリを content ID で特定
        try {
            DaisyBookInfo dl = mSql.getDaisyBookById(bookId);
            if (dl != null) {
                path = dl.getPath();
                title = dl.getTitle();
            }
        } catch (Exception e) {
            org.androiddaisyreader.utils.LogFile.e(TAG, "return cleanup: get book failed", e);
        }

        // ブックマーク/現在位置の照合に使うパス集合（元パス + キャッシュパス）を先に確定する
        final List<String> paths = new ArrayList<>();
        if (path != null && !path.isEmpty()) {
            paths.add(path);
            if (path.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
                try {
                    String cached = CacheHelper.getCachedPath(this, path);
                    if (cached != null && !cached.isEmpty()) {
                        paths.add(cached);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        // ファイル削除（MediaStore エントリなら実体も削除される）
        if (path != null && !path.isEmpty()) {
            try {
                if (path.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
                    getContentResolver().delete(Uri.parse(path), null, null);
                } else {
                    new File(path).delete();
                }
            } catch (Exception e) {
                org.androiddaisyreader.utils.LogFile.e(TAG, "return cleanup: delete file failed", e);
            }

            // アプリキャッシュ削除
            try {
                CacheHelper.deleteCache(this, path);
            } catch (Exception e) {
                org.androiddaisyreader.utils.LogFile.e(TAG, "return cleanup: delete cache failed", e);
            }
        }

        // DB: ダウンロード済 + 最近の本
        try {
            mSql.deleteDaisyBook(bookId);
        } catch (Exception e) {
            org.androiddaisyreader.utils.LogFile.e(TAG, "return cleanup: delete downloaded failed", e);
        }
        if (title != null && !title.isEmpty()) {
            try {
                mSql.deleteDaisyBookByTitle(title, Constants.TYPE_RECENT_BOOK);
            } catch (Exception e) {
                org.androiddaisyreader.utils.LogFile.e(TAG, "return cleanup: delete recent failed", e);
            }
        }

        // ブックマーク・現在位置
        for (String p : paths) {
            try {
                SQLiteBookmarkHelper.getInstance(this).deleteBookmarksByPath(p);
            } catch (Exception e) {
                org.androiddaisyreader.utils.LogFile.e(TAG, "return cleanup: delete bookmarks failed", e);
            }
            try {
                SQLiteCurrentInformationHelper.getInstance(this)
                        .deleteCurrentInformationByPath(p);
            } catch (Exception e) {
                org.androiddaisyreader.utils.LogFile.e(TAG, "return cleanup: delete current info failed", e);
            }
        }
    }

    /**
     * 返却成功後に現在表示中の一覧を再取得する。
     */
    private void refreshCurrentList() {
        if (state == ViewState.ISSUED_LIST) {
            loadIssuedList();
        } else if (state == ViewState.CONTENT_LIST && currentContentListRef != null) {
            loadContentList(currentContentListRef);
        }
    }

    // ------------------------------------------------------------------
    // ダウンロード保存・通知（既存 SapieBooks 版から流用）
    // ------------------------------------------------------------------

    private Uri saveToDownloadsAndMediaStore(File sourceFile, String fileName, String mimeType) {
        android.content.ContentResolver resolver = getContentResolver();
        String selection = android.provider.MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                + android.provider.MediaStore.Downloads.RELATIVE_PATH + "=?";
        String[] selectionArgs = new String[]{
                fileName,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/DaisyReader/"
        };
        try {
            resolver.delete(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    selection, selectionArgs);
        } catch (Exception e) {
            // ignore
        }

        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/DaisyReader");

        Uri uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            return null;
        }
        try (java.io.InputStream in = new java.io.FileInputStream(sourceFile);
             java.io.OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) {
                resolver.delete(uri, null, null);
                return null;
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } catch (java.io.IOException e) {
            resolver.delete(uri, null, null);
            return null;
        }
        return uri;
    }

    private void showDownloadCompleteNotification(String fileName, Uri fileUri) {
        String channelId = "sapie_download";
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId,
                    getString(R.string.sapie_settings_title),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Sapie download notifications");
            notificationManager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(this, DaisyEbookReaderModeChoiceActivity.class);
        openIntent.putExtra(Constants.DAISY_PATH, fileUri != null ? fileUri.toString() : "");
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this, bookIdFromUri(fileUri),
                openIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder builder =
                new androidx.core.app.NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(getString(R.string.message_download_complete))
                        .setContentText(fileName)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        notificationManager.notify(bookIdFromUri(fileUri), builder.build());
    }

    private int bookIdFromUri(Uri uri) {
        if (uri == null) {
            return 0;
        }
        try {
            String lastSegment = uri.getLastPathSegment();
            return lastSegment != null ? lastSegment.hashCode() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // UI ヘルパー
    // ------------------------------------------------------------------

    private void showLoading() {
        showHeader(getString(R.string.sapie_loading));
    }

    private void showHeader(String text) {
        headerText.setText(text != null ? text : "");
    }

    private Map<String, String> createDodpErrorExtras(String operation) {
        Map<String, String> extras = new HashMap<>();
        extras.put("operation", operation != null ? operation : "unknown");
        extras.put("state", state != null ? state.name() : "null");
        if (currentMenuQuestionId != null) {
            extras.put("questionId", currentMenuQuestionId);
        }
        if (currentInputQuestionId != null) {
            extras.put("inputQuestionId", currentInputQuestionId);
        }
        return extras;
    }

    private void setInputVisible(boolean visible) {
        inputField.setVisibility(visible ? View.VISIBLE : View.GONE);
        inputSubmit.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private LinearLayout.LayoutParams createButtonLayoutParams() {
        int height = getResources().getDimensionPixelSize(R.dimen.sapie_button_height);
        int margin = getResources().getDimensionPixelSize(R.dimen.sapie_button_margin);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.setMargins(0, margin, 0, margin);
        return params;
    }

    private void reportError(Exception e) {
        org.androiddaisyreader.utils.LogFile.e(TAG, "Sapie DODP error", e);
        runOnUiThread(() -> {
            Toast.makeText(DaisyReaderSapieDodpActivity.this,
                    getString(R.string.error_cannot_dowload), Toast.LENGTH_LONG).show();
            showErrorWithLogSend(R.string.error_cannot_dowload, e, createDodpErrorExtras(null));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        speakText(getString(R.string.sapie_books));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // client.close() は OkHttp のコネクションプール破棄でソケットを閉じるため、
        // メインスレッドで実行すると NetworkOnMainThreadException になる。
        // バックグラウンドスレッドでクローズしてから executor を停止する。
        final SapieDodpClient toClose = client;
        client = null;
        if (toClose != null) {
            Thread closer = new Thread(() -> {
                try {
                    toClose.close();
                } catch (Exception ignored) {
                    // クローズ失敗は無視（アプリ終了時のため）
                }
            }, "SapieDodpClient-close");
            closer.setDaemon(true);
            closer.start();
        }
        executor.shutdown();
    }
}
