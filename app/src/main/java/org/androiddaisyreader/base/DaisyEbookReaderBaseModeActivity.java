package org.androiddaisyreader.base;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.androiddaisyreader.apps.PrivateException;
import org.androiddaisyreader.model.CurrentInformation;
import org.androiddaisyreader.model.Navigator;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sqlite.SQLiteCurrentInformationHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * VisualModeActivity / SimpleModeActivity の共通基底Activity。
 * ReaderPresenter とのバインディング、ライフサイクル管理、共通UI操作を提供する。
 * サブクラスは ReaderView を実装し、モード固有のUI処理を担当する。
 */
public abstract class DaisyEbookReaderBaseModeActivity extends DaisyEbookReaderBaseActivity
        implements ReaderView {

    protected ReaderPresenter presenter;
    protected IntentController mIntentController;
    protected SQLiteCurrentInformationHelper mSql;
    protected SafeHandler mHandler;
    protected Runnable mRunnable;
    protected String mPath;
    protected boolean isFormat202;
    protected DaisyEbookReaderBaseMode baseMode;

    /** バックグラウンドDB操作用のExecutorService */
    protected final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    /** メインスレッドに結果を返すHandler */
    protected final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSql = SQLiteCurrentInformationHelper.getInstance(this);
        mIntentController = new IntentController(this);
        mHandler = new SafeHandler(this);
    }

    @Override
    protected void onTtsReady() {
        super.onTtsReady();
        initReadAloudListener();
    }

    @Override
    protected void onSetScreenNameCallback(java.util.concurrent.atomic.AtomicBoolean callbackExecuted, Runnable onComplete) {
        mScreenNameCallbackExecuted = callbackExecuted;
        mScreenNameCallback = onComplete;
    }

    /**
     * Presenterを初期化する。サブクラスのonCreateで呼び出す。
     */
    protected void initPresenter() {
        mPath = getIntent().getStringExtra(Constants.DAISY_PATH);
        if (!validatePath(mPath)) {
            return;
        }
        // content:// URI はキャッシュのローカルパスに変換する
        if (mPath.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
            try {
                java.io.File cachedFile = org.androiddaisyreader.utils.CacheHelper
                        .copyToCache(getApplicationContext(), mPath);
                mPath = cachedFile.getAbsolutePath();
            } catch (java.io.IOException e) {
                showErrorDialog(e);
                finish();
                return;
            }
        }
        isFormat202 = DaisyBookUtil.findDaisyFormat(mPath, getApplicationContext()) == Constants.DAISY_202_FORMAT;
        baseMode = new DaisyEbookReaderBaseMode(mPath, this);
        presenter = new ReaderPresenter(this, baseMode, mSql, mPath, isFormat202, dbExecutor);
    }

    // ========================================================================
    // Intent入力バリデーション
    // ========================================================================

    /**
     * パスが有効かバリデーションする。
     * nullまたは空の場合はエラーを表示してActivityを終了する。
     */
    protected boolean validatePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            showErrorDialog(new IllegalArgumentException("Book path is not specified"));
            finish();
            return false;
        }
        // パストラバーサル検出
        if (path.contains("..")) {
            showErrorDialog(new SecurityException("Invalid path: contains traversal"));
            finish();
            return false;
        }
        return true;
    }

    /**
     * セクション番号文字列を安全にint変換する。
     * 変換失敗時は0を返す。
     */
    protected int parseSectionSafely(String section) {
        if (section == null || section.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(section.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Intent文字列Extraを安全に取得する（nullの場合はデフォルト値を返す）。
     */
    protected String getStringExtraSafely(String key, String defaultValue) {
        String value = getIntent().getStringExtra(key);
        return value != null ? value : defaultValue;
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        dbExecutor.shutdown();
        if (presenter != null) {
            presenter.destroy();
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (presenter != null) {
            if (presenter.getBook() != null) {
                presenter.refreshNavigator();
            }
            // 設定から再生速度を読み取り適用
            android.content.SharedPreferences prefs = android.preference.PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext());
            float speed = prefs.getFloat(Constants.TTS_READ_ALOUD_SPEED, Constants.TTS_SPEED_DEFAULT);
            presenter.setPlaybackSpeed(speed);
        }
    }

    @Override
    protected void onRestart() {
        // 再生状態の復元は onResume の画面名読み上げ完了後に行う
        super.onRestart();
    }

    @Override
    protected boolean onBackPressedHandled() {
        if (presenter.getBook() != null) {
            presenter.setPlaying(presenter.getPlayer() != null && presenter.getPlayer().isPlaying());
            if (presenter.isPlaying()) {
                presenter.setMediaPause();
            }
            dbExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    presenter.handleCurrentInformation();
                }
            });
            finish();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return false;
    }

    // ========================================================================
    // ReaderView デフォルト実装
    // ========================================================================

    @Override
    public boolean isActivityFinishing() {
        return isFinishing();
    }

    @Override
    public void showErrorDialog(Exception e) {
        if (e instanceof PrivateException) {
            ((PrivateException) e).showDialogException(mIntentController);
        } else {
            PrivateException ex = new PrivateException(e, this, mPath);
            ex.showDialogException(mIntentController);
        }
    }

    @Override
    public void speak(String text) {
        speakText(text);
    }

    @Override
    public void stopSpeaking() {
        if (mTts != null && mTtsReady && mTts.isSpeaking()) {
            mTts.stop();
        }
    }

    @Override
    public void displayContent(String content) {
        // デフォルトは何もしない。VisualModeでオーバーライド。
    }

    @Override
    public void displayImage(String imageSrc) {
        // デフォルトは何もしない。SimpleModeでオーバーライド。
    }

    private volatile int mExpectedSentenceIndex = -1;
    private volatile String mExpectedScreenNameId = null;
    private volatile java.util.concurrent.atomic.AtomicBoolean mScreenNameCallbackExecuted = null;
    private volatile Runnable mScreenNameCallback = null;

    @Override
    public void speakSentence(String text, int sentenceIndex) {
        android.util.Log.d("TTS_DEBUG", "speakSentence called: index=" + sentenceIndex + " text=\"" + text.substring(0, Math.min(text.length(), 20)) + "...\" from " + getClass().getSimpleName());
        if (mTts == null || !mTtsReady) return;
        mExpectedSentenceIndex = sentenceIndex;
        android.os.Bundle params = new android.os.Bundle();
        mTts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params,
                "sentence_" + sentenceIndex);
    }

    /**
     * TTS読み上げ用の統一UtteranceProgressListenerを設定する。
     * onTtsReadyで1回だけ呼ぶ。sentence_とscreen_name_の両方を処理する。
     */
    private void initReadAloudListener() {
        if (mTts == null) return;
        mTts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                android.util.Log.d("TTS_DEBUG", "onStart: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                android.util.Log.d("TTS_DEBUG", "onDone: " + utteranceId);
                if (utteranceId == null) return;
                if (utteranceId.startsWith("sentence_")) {
                    int index = Integer.parseInt(utteranceId.substring("sentence_".length()));
                    if (index == mExpectedSentenceIndex) {
                        runOnUiThread(() -> {
                            if (presenter != null && !isFinishing()) {
                                presenter.onUtteranceCompleted(index);
                            }
                        });
                    }
                } else if (utteranceId.startsWith("screen_name_")) {
                    if (mScreenNameCallbackExecuted != null
                            && mScreenNameCallbackExecuted.compareAndSet(false, true)) {
                        mSpeakHandler.removeCallbacksAndMessages(null);
                        if (mScreenNameCallback != null) {
                            Runnable cb = mScreenNameCallback;
                            mScreenNameCallback = null;
                            runOnUiThread(cb);
                        }
                    }
                }
            }

            @Override
            public void onError(String utteranceId) {
                android.util.Log.d("TTS_DEBUG", "onError: " + utteranceId);
                if (utteranceId != null && utteranceId.startsWith("screen_name_")) {
                    if (mScreenNameCallbackExecuted != null
                            && mScreenNameCallbackExecuted.compareAndSet(false, true)) {
                        mSpeakHandler.removeCallbacksAndMessages(null);
                        if (mScreenNameCallback != null) {
                            Runnable cb = mScreenNameCallback;
                            mScreenNameCallback = null;
                            runOnUiThread(cb);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void stopReadAloud() {
        if (mTts != null && mTtsReady && mTts.isSpeaking()) {
            mTts.stop();
        }
    }

    @Override
    public void applyReadAloudSettings() {
        if (mTts == null || !mTtsReady) return;
        android.content.SharedPreferences prefs = android.preference.PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        // 言語設定
        String langTag = prefs.getString(Constants.TTS_READ_ALOUD_LANGUAGE, "");
        if (langTag.isEmpty()) {
            mTts.setLanguage(checkTTSSupportLanguage()
                    ? java.util.Locale.getDefault() : java.util.Locale.US);
        } else {
            String[] parts = langTag.split("_");
            java.util.Locale locale;
            if (parts.length == 1) {
                locale = new java.util.Locale(parts[0]);
            } else if (parts.length == 2) {
                locale = new java.util.Locale(parts[0], parts[1]);
            } else {
                locale = new java.util.Locale(parts[0], parts[1], parts[2]);
            }
            mTts.setLanguage(locale);
        }
        // 速度設定
        float speed = prefs.getFloat(Constants.TTS_READ_ALOUD_SPEED, Constants.TTS_SPEED_DEFAULT);
        mTts.setSpeechRate(speed);
    }

    @Override
    public void highlightSentence(int sentenceIndex) {
        // デフォルトは何もしない。VisualModeでオーバーライド。
    }

    // ========================================================================
    // DB操作ヘルパー（UIスレッドでのDB操作排除）
    // ========================================================================

    /**
     * バックグラウンドでCurrentInformationを読み込み、コールバックで結果を返す。
     */
    protected void loadCurrentInformationAsync(final OnCurrentInfoLoadedListener listener) {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final CurrentInformation current = mSql.getCurrentInformation();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing() && listener != null) {
                            listener.onLoaded(current);
                        }
                    }
                });
            }
        });
    }

    /**
     * バックグラウンドでCurrentInformationを保存する。
     */
    protected void saveCurrentInformationAsync(final CurrentInformation current) {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (current != null) {
                    mSql.updateCurrentInformation(current);
                }
            }
        });
    }

    /**
     * CurrentInformation読み込み完了コールバック。
     */
    public interface OnCurrentInfoLoadedListener {
        void onLoaded(CurrentInformation current);
    }

    // ========================================================================
    // SafeHandler（メモリリーク防止）
    // ========================================================================

    protected static class SafeHandler extends Handler {
        private final WeakReference<DaisyEbookReaderBaseModeActivity> mActivity;

        public SafeHandler(DaisyEbookReaderBaseModeActivity activity) {
            super(Looper.getMainLooper());
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            DaisyEbookReaderBaseModeActivity activity = mActivity.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }
            super.handleMessage(msg);
        }
    }
}
