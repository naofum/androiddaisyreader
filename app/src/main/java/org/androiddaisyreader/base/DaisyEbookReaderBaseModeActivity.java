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
    protected Runnable mRunnalbe;
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

    /**
     * Presenterを初期化する。サブクラスのonCreateで呼び出す。
     */
    protected void initPresenter() {
        mPath = getIntent().getStringExtra(Constants.DAISY_PATH);
        if (!validatePath(mPath)) {
            return;
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
        if (presenter.getBook() != null) {
            presenter.refreshNavigator();
        }
    }

    @Override
    protected void onRestart() {
        dbExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final CurrentInformation current = mSql.getCurrentInformation();
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        presenter.setCurrent(current);
                        if (current != null) {
                            if (current.getPlaying()) {
                                presenter.setMediaPlay();
                            } else {
                                presenter.setMediaPause();
                            }
                            if (!current.getActivity().equals(getActivityName())) {
                                presenter.readBook();
                            }
                        }
                    }
                });
            }
        });
        super.onRestart();
    }

    @Override
    public void onBackPressed() {
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
        } else {
            super.onBackPressed();
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
        if (mTts != null && mTts.isSpeaking()) {
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
