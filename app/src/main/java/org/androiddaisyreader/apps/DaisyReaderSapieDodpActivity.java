package org.androiddaisyreader.apps;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;
import org.androiddaisyreader.utils.SapiePreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private enum ViewState { MENU, INPUT, CONTENT_LIST }

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
    private Button backButton;

    // 動的メニュー用の現在の選択肢
    private final List<Choice> currentChoices = new ArrayList<>();
    // 現在表示中メニューの質問ID（multipleChoiceQuestion の id）。選択時に value とともに送る。
    private String currentMenuQuestionId;
    // 一覧用の現在アイテム
    private final List<ContentItem> currentItems = new ArrayList<>();
    // 入力質問の questionId
    private String currentInputQuestionId;
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
        root.addView(inputField);

        inputSubmit = new Button(this);
        inputSubmit.setText(R.string.sapie_input_submit);
        inputSubmit.setVisibility(View.GONE);
        inputSubmit.setOnClickListener(v -> submitInput());
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
        backButton.setOnClickListener(v -> goBack());
        backButton.setLayoutParams(createButtonLayoutParams());
        root.addView(backButton);

        setContentView(root);
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
        }
    }

    /**
     * 一覧項目のアクション選択（貸出 or 返却）。
     */
    private void confirmItemAction(final ContentItem item, final String label) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(new CharSequence[]{
                        getString(R.string.sapie_borrow),
                        getString(R.string.sapie_return)
                }, (dialog, which) -> {
                    if (which == 0) {
                        borrowAndDownload(item, label);
                    } else {
                        returnContent(item, label);
                    }
                })
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

                DaisyBookInfo info = new DaisyBookInfo(
                        contentId,
                        title,
                        savedPath,
                        resolveAuthor(metadata),
                        "",
                        "",
                        0);
                mSql.addOrReplaceDaisyBook(info, Constants.TYPE_DOWNLOADED_BOOK);
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
     * リソース一覧から DAISY 本体（zip 等）をキャッシュへダウンロードする。
     * 複数リソースがある場合は最初のダウンロード可能なものを対象とする。
     */
    private File downloadResources(String contentId, Resources resources) throws java.io.IOException {
        if (resources == null || resources.getResources() == null || resources.getResources().isEmpty()) {
            return null;
        }
        File tempDir = new File(getCacheDir(), "sapie");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }

        com.github.library.dodp.download.ContentDownloader downloader =
                new com.github.library.dodp.download.ContentDownloader();

        // localURI があればそれをファイル名に、無ければ contentId ベース。
        Resource target = resources.getResources().get(0);
        String localName = target.getLocalUri();
        if (localName == null || localName.isEmpty()) {
            localName = contentId + ".zip";
        }
        File dest = new File(tempDir, sanitizeFileName(localName));
        java.nio.file.Path result = downloader.download(target, dest.toPath());
        return result != null ? result.toFile() : dest;
    }

    private String sanitizeFileName(String name) {
        String base = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        return slash >= 0 ? base.substring(slash + 1) : base;
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
                runOnUiThread(() -> {
                    String msg = ok ? getString(R.string.sapie_returned)
                            : getString(R.string.sapie_return_failure);
                    Toast.makeText(DaisyReaderSapieDodpActivity.this, msg, Toast.LENGTH_SHORT).show();
                    speakText(msg);
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
