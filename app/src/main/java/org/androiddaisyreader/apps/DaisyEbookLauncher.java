package org.androiddaisyreader.apps;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.github.naofum.androiddaisyreader.R;

import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.model.ZippedBookInfo;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.CacheHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 外部Intent（ファイルマネージャー、通知クリック等）から書籍を開くランチャーActivity。
 * キャッシュ処理をバックグラウンドで実行し、ProgressBar付きの画面を表示する。
 */
public class DaisyEbookLauncher extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private TextToSpeech mTts;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isFinished = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        // TTS初期化して「書籍を開いています」を読み上げ
        mTts = new TextToSpeech(getApplicationContext(), this);

        // バックグラウンドでキャッシュ処理を実行
        String uri = getIntent().getDataString();
        if (uri == null) {
            finish();
            return;
        }
        startBackgroundLoading(uri);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            Locale locale = Locale.getDefault();
            if (mTts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                mTts.setLanguage(locale);
            } else {
                mTts.setLanguage(Locale.US);
            }
            // 「書籍を開いています」を読み上げ
            mTts.speak(getString(R.string.loading_book), TextToSpeech.QUEUE_FLUSH, null, "loading");
        }
    }

    private void startBackgroundLoading(String uri) {
        executor.execute(() -> {
            String pathToOpen = uri;
            try {
                if (uri.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
                    // content:// URI はキャッシュにコピーし、キャッシュのローカルパスで渡す
                    File cachedFile = CacheHelper.copyToCache(getApplicationContext(), uri);
                    pathToOpen = cachedFile.getAbsolutePath();
                } else if (uri.startsWith("file://")) {
                    // file:// URI はローカルパスに変換
                    pathToOpen = android.net.Uri.parse(uri).getPath();
                }
                // それ以外はローカルパスとしてそのまま使用

                // 書籍メタデータを読み取り、最近の書籍としてDBに登録
                registerBookMetadata(pathToOpen);

            } catch (Exception e) {
                PrivateException ex = new PrivateException(e, getApplicationContext(), uri);
                ex.writeLogException();
            }

            final String finalPath = pathToOpen;
            mainHandler.post(() -> openBook(finalPath));
        });
    }

    private void openBook(String pathToOpen) {
        if (isFinished || isFinishing()) return;
        isFinished = true;

        // 書籍を開く
        IntentController intentController = new IntentController(DaisyEbookLauncher.this);
        intentController.pushToDaisyEbookReaderIntent(pathToOpen);
        finish();
    }

    /**
     * 書籍メタデータをZIPから読み取り、最近の書籍としてDBに登録する。
     */
    private void registerBookMetadata(String path) {
        try {
            DaisyBookInfo bookInfo = null;

            // ZIPストリームからメタデータを読み取り
            if (path.endsWith(Constants.SUFFIX_ZIP_FILE) || path.endsWith(Constants.SUFFIX_EPUB_FILE)) {
                try (InputStream input = new BufferedInputStream(new FileInputStream(path))) {
                    bookInfo = ZippedBookInfo.readFromZipStream(input, Charset.forName("MS932"));
                } catch (IllegalArgumentException iae) {
                    try (InputStream input = new BufferedInputStream(new FileInputStream(path))) {
                        bookInfo = ZippedBookInfo.readFromZipStream(input, Charset.defaultCharset());
                    }
                }
            }

            if (bookInfo != null) {
                bookInfo.setPath(path);

                // 最近の書籍に登録
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                int numberOfRecentBooks = prefs.getInt(Constants.NUMBER_OF_RECENT_BOOKS,
                        Constants.NUMBER_OF_RECENTBOOK_DEFAULT);
                SQLiteDaisyBookHelper sql = SQLiteDaisyBookHelper.getInstance(this);
                DaisyBookUtil.addRecentBookToSQLite(bookInfo, numberOfRecentBooks, sql);
            }
        } catch (Exception e) {
            // メタデータ登録失敗は致命的ではない - 書籍は開ける
            PrivateException ex = new PrivateException(e, getApplicationContext(), path);
            ex.writeLogException();
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
            mTts = null;
        }
        super.onDestroy();
    }
}
