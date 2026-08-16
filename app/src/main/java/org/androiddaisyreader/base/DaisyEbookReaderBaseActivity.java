package org.androiddaisyreader.base;

import java.util.Locale;

import org.androiddaisyreader.apps.DaisyReaderLibraryActivity;
import org.androiddaisyreader.apps.PrivateException;
import org.androiddaisyreader.model.CurrentInformation;
import org.androiddaisyreader.sqlite.SQLiteCurrentInformationHelper;
import org.androiddaisyreader.utils.Constants;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings.System;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager.LayoutParams;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.github.naofum.androiddaisyreader.R;

//import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * 
 * @author LogiGear
 * @date Jul 19, 2013
 */

public class DaisyEbookReaderBaseActivity extends AppCompatActivity implements OnClickListener {
    protected TextToSpeech mTts;
    private static final long DOUBLE_PRESS_INTERVAL = 1000;
    private static final long DELAY_MILLIS = DOUBLE_PRESS_INTERVAL;
    private long lastPressTime;
    private int lastPositionClick = -1;
    private boolean mHasDoubleClicked = false;
    private String mPendingSpeakText = null;
    private Runnable mPendingSpeakCallback = null;
    protected final Handler mSpeakHandler = new Handler(Looper.getMainLooper());
    protected volatile boolean mTtsReady = false;
//    protected FirebaseAnalytics mFirebaseAnalytics;


    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (onBackPressedHandled()) {
                    return;
                }

                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        // Obtain the FirebaseAnalytics instance.
//        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        // initial TTS
        startTts();

        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    1);
        }

        SharedPreferences mPreferences = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        Constants.folderRoot = mPreferences.getString(Constants.STORAGE_ROOT,
                Environment.getExternalStorageDirectory().getAbsolutePath());
//        Constants.folderContainMetadata = Constants.folderRoot
//                + "/" + Constants.FOLDER_NAME + "/";
        Constants.folderContainMetadata = getFilesDir().getAbsolutePath() + "/";
    }

    @Override
    protected void onResume() {
        super.onResume();
        final int numberToConvert = 255;
        Window window = getWindow();
        ContentResolver cResolver = getContentResolver();
        int valueScreen = 0;
        try {
            SharedPreferences mPreferences = PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext());
            valueScreen = mPreferences.getInt(Constants.BRIGHTNESS,
                    System.getInt(cResolver, System.SCREEN_BRIGHTNESS));
            LayoutParams layoutpars = window.getAttributes();
            layoutpars.screenBrightness = valueScreen / (float) numberToConvert;
            // apply attribute changes to this window
            window.setAttributes(layoutpars);
            Constants.folderRoot = mPreferences.getString(Constants.STORAGE_ROOT,
                    Environment.getExternalStorageDirectory().getAbsolutePath());
//            Constants.folderContainMetadata = Constants.folderRoot
//                    + "/" + Constants.FOLDER_NAME + "/";
            Constants.folderContainMetadata = getFilesDir().getAbsolutePath() + "/";
            if (!mTtsReady) {
                startTts();
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, getApplicationContext());
            ex.writeLogException();
        }
    }

    @Override
    protected void onDestroy() {
        mSpeakHandler.removeCallbacksAndMessages(null);
        mTtsReady = false;
        super.onDestroy();
    }

    /**
     * Make sure TTS installed on your device.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onClick(View arg0) {

    }

    /**
     * Application スコープの共有TTSインスタンスを取得する。
     */
    private void startTts() {
        android.util.Log.d("TTS_DEBUG", "startTts called from " + getClass().getSimpleName() + " mTtsReady=" + mTtsReady);
        org.androiddaisyreader.apps.DaisyReaderApplication app =
                org.androiddaisyreader.apps.DaisyReaderApplication.getInstance();
        if (app != null) {
            mTts = app.getTts();
            if (app.isTtsReady()) {
                mTtsReady = true;
                onTtsReady();
            } else {
                app.setOnTtsReadyListener(() -> {
                    mTtsReady = true;
                    onTtsReady();
                });
            }
        }
    }

    /**
     * TTS初期化完了時のコールバック。Application経由で呼ばれる。
     */
    protected void onTtsReady() {
        android.util.Log.d("TTS_DEBUG", "onTtsReady from " + getClass().getSimpleName() + " pendingText=" + mPendingSpeakText + " pendingCallback=" + (mPendingSpeakCallback != null));
        // フォールバックタイマーをキャンセル
        mSpeakHandler.removeCallbacksAndMessages(null);
        // ペンディングがあれば処理
        if (mPendingSpeakText != null && mPendingSpeakCallback != null) {
            String text = mPendingSpeakText;
            Runnable callback = mPendingSpeakCallback;
            mPendingSpeakText = null;
            mPendingSpeakCallback = null;
            speakTextWithCallback(text, callback);
        } else if (mPendingSpeakText != null) {
            speakText(mPendingSpeakText);
            mPendingSpeakText = null;
        }
    }

    /**
     * Check TTS support language.
     * 
     * @return true, if locale is available and supported
     */
    public boolean checkTTSSupportLanguage() {
        Locale currentLocale = Locale.getDefault();
        return mTts.isLanguageAvailable(currentLocale) == TextToSpeech.LANG_MISSING_DATA
                || mTts.isLanguageAvailable(currentLocale) == TextToSpeech.LANG_NOT_SUPPORTED ? false
                : true;
    }

    /**
     * Check keyguard screen is showing or in restricted key input mode .
     * 
     * @return true, if in keyguard restricted input mode
     */
    public boolean checkKeyguardMode() {
        getApplicationContext();
        KeyguardManager kgMgr = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return kgMgr.inKeyguardRestrictedInputMode();
    }

    /**
     * Interrupts the current utterance if speaking and speak new text
     * 
     * @param textToSpeech the text to speech
     */
    private String mLastSpokenText = null;
    private long mLastSpokenTime = 0;
    private static final long DUPLICATE_SPEAK_THRESHOLD = 500;

    public void speakText(String textToSpeech) {
//        Exception e = new Exception("Current StackTrace");
//        android.util.Log.e("TTS_DEBUG", "(duplicate)", e);
        android.util.Log.d("TTS_DEBUG", "speakText called: \"" + textToSpeech + "\" from " + getClass().getSimpleName() + " mTtsReady=" + mTtsReady);
        // 短時間内の同一テキスト重複発話を防止
        long now = java.lang.System.currentTimeMillis();
        if (textToSpeech.equals(mLastSpokenText) && (now - mLastSpokenTime) < DUPLICATE_SPEAK_THRESHOLD) {
            android.util.Log.d("TTS_DEBUG", "speakText SKIPPED (duplicate): \"" + textToSpeech + "\"");
            return;
        }
        if (mTts != null && mTtsReady && checkTTSSupportLanguage()) {
            if (!checkKeyguardMode()) {
                android.util.Log.d("TTS_DEBUG", "speakText QUEUE_FLUSH: \"" + textToSpeech + "\"");
                mLastSpokenText = textToSpeech;
                mLastSpokenTime = now;
                mTts.speak(textToSpeech, TextToSpeech.QUEUE_FLUSH, null);
            }
        } else {
            android.util.Log.d("TTS_DEBUG", "speakText pending: \"" + textToSpeech + "\"");
            // TTS未初期化の場合はpendingに保存し、onInit完了後に読み上げる
            mPendingSpeakText = textToSpeech;
            if (mTts == null) {
                startTts();
            }
        }
    }

    public void speakText(String textToSpeech, int queue) {
        android.util.Log.d("TTS_DEBUG", "speakText(queue=" + queue + ") called: \"" + textToSpeech + "\" from " + getClass().getSimpleName());
        if (mTts != null && mTtsReady) {
            if (checkTTSSupportLanguage() && !checkKeyguardMode()) {
                android.util.Log.d("TTS_DEBUG", "speakText queue=" + queue + ": \"" + textToSpeech + "\"");
                mTts.speak(textToSpeech, queue, null);
            }
        } else {
            // TTS未初期化の場合はpendingに保存
            mPendingSpeakText = textToSpeech;
            if (mTts == null) {
                startTts();
            }
        }
    }

    /**
     * テキストを読み上げ、完了後にコールバックを実行する。
     * TTS未初期化の場合はコールバックを即座に実行する。
     *
     * @param textToSpeech 読み上げテキスト
     * @param onComplete   読み上げ完了後に実行するRunnable
     */
    public void speakTextWithCallback(String textToSpeech, final Runnable onComplete) {
        android.util.Log.d("TTS_DEBUG", "speakTextWithCallback called: \"" + textToSpeech + "\" from " + getClass().getSimpleName() + " mTtsReady=" + mTtsReady);
        // 短時間内の同一テキスト重複呼び出しを防止
        long now = java.lang.System.currentTimeMillis();
        if (textToSpeech.equals(mLastSpokenText) && (now - mLastSpokenTime) < DUPLICATE_SPEAK_THRESHOLD) {
            android.util.Log.d("TTS_DEBUG", "speakTextWithCallback SKIPPED (duplicate): \"" + textToSpeech + "\"");
            return;
        }
        mLastSpokenText = textToSpeech;
        mLastSpokenTime = now;
        if (mTts != null && mTtsReady) {
            if (checkTTSSupportLanguage() && !checkKeyguardMode()) {
                final java.util.concurrent.atomic.AtomicBoolean callbackExecuted =
                        new java.util.concurrent.atomic.AtomicBoolean(false);
                final String utteranceId = "screen_name_" + java.lang.System.currentTimeMillis();

                // サブクラスの統一リスナーで処理するためフィールドに保持
                onSetScreenNameCallback(callbackExecuted, onComplete);

                // 前画面のTTS操作が完了するのを待ってから発話
//                Exception e = new Exception("Current StackTrace");
//                android.util.Log.e("TTS_DEBUG", "(delayed)", e);
                final String text = textToSpeech;
                mSpeakHandler.postDelayed(() -> {
                    android.util.Log.d("TTS_DEBUG", "speakTextWithCallback delayed speak: \"" + text + "\" utteranceId=" + utteranceId);
                    if (!isFinishing() && mTts != null) {
                        android.os.Bundle params = new android.os.Bundle();
                        mTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
                    } else if (callbackExecuted.compareAndSet(false, true) && onComplete != null) {
                        onComplete.run();
                    }
                }, 150);
                // onDoneが来ない場合のフォールバック（5秒）
                mSpeakHandler.postDelayed(() -> {
                    if (callbackExecuted.compareAndSet(false, true)) {
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }
                }, 5000);
            } else {
                // 言語非対応またはロック画面 → 即座にコールバック
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        } else {
            // TTS未初期化の場合はペンディングに保存し、onInit完了後に発話+コールバック実行
            mPendingSpeakText = textToSpeech;
            mPendingSpeakCallback = onComplete;
            if (mTts == null) {
                startTts();
            }
            // TTS初期化が3秒以内に完了しなければコールバックを実行するフォールバック
            final Runnable fallback = () -> {
                if (mPendingSpeakCallback != null) {
                    Runnable cb = mPendingSpeakCallback;
                    mPendingSpeakText = null;
                    mPendingSpeakCallback = null;
                    cb.run();
                }
            };
            mSpeakHandler.postDelayed(fallback, 3000);
        }
    }

    /**
     * screen_name_ utterance完了時のコールバックを設定する。
     * BaseModeActivityでオーバーライドして統一リスナーに連携する。
     * BaseActivityを直接継承する画面（Library等）ではデフォルト実装（何もしない）を使う。
     */
    protected void onSetScreenNameCallback(java.util.concurrent.atomic.AtomicBoolean callbackExecuted, Runnable onComplete) {
        // デフォルト: BaseModeActivity以外の画面ではリスナーが別途設定されないため
        // フォールバックタイマーに任せる
    }

    /**
     * Speak text on handler.
     * ダブルタップ判定の待機後にシングルタップと確定した場合のみ発話する。
     * 前回の待機中メッセージはキャンセルされるため二重発話しない。
     * 
     * @param textToSpeech the text to speech
     */
    public void speakTextOnHandler(final String textToSpeech) {
        android.util.Log.d("TTS_DEBUG", "speakTextOnHandler queued: \"" + textToSpeech + "\" from " + getClass().getSimpleName());
        mSpeakHandler.removeCallbacksAndMessages(null);
        mSpeakHandler.postDelayed(() -> {
            android.util.Log.d("TTS_DEBUG", "speakTextOnHandler executing: \"" + textToSpeech + "\" mHasDoubleClicked=" + mHasDoubleClicked);
            if (!mHasDoubleClicked) {
                speakText(textToSpeech);
            }
        }, DELAY_MILLIS);
    }

    /**
     * Back to top screen.
     */
    public void backToTopScreen() {
        Intent intent = new Intent(this, DaisyReaderLibraryActivity.class);
        // Removes other Activities from stack
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /**
     * Delete current information.
     */
    public void deleteCurrentInformation() {
        new Thread(() -> {
            SQLiteCurrentInformationHelper sql = SQLiteCurrentInformationHelper.getInstance(getApplicationContext());
            CurrentInformation current = sql.getCurrentInformation();
            if (current != null) {
                sql.deleteCurrentInformation(current.getId());
            }
        }).start();
    }

    /**
     * Restart activity when changing configuration.
     */
    private void restartActivity() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        restartActivity();
    }

    /**
     * Handle click item is double tap or single tap
     * 
     * @param position the position
     * @return true, if double tap on item
     */
    public boolean handleClickItem(final int position) {
        // Get current time in nano seconds.
        long pressTime = java.lang.System.currentTimeMillis();

        // If double click...
        if (pressTime - lastPressTime <= DOUBLE_PRESS_INTERVAL && lastPositionClick == position) {
            mHasDoubleClicked = true;
            android.util.Log.d("TTS_DEBUG", "handleClickItem: DOUBLE TAP - stop TTS, remove callbacks");
            // ダブルタップ確定: 待機中の発話をキャンセルし、現在の発話も停止
            mSpeakHandler.removeCallbacksAndMessages(null);
            if (mTts != null && mTtsReady) {
                mTts.stop();
            }
            // If not double click....
        } else {
            mHasDoubleClicked = false;
            android.util.Log.d("TTS_DEBUG", "handleClickItem: SINGLE TAP");
        }
        // record the last time the menu button was pressed.
        lastPressTime = pressTime;
        lastPositionClick = position;
        return mHasDoubleClicked;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    /**
     * エラーダイアログを表示する。「ログを送信」ボタンを備える。
     * UIスレッドから呼ぶこと。
     *
     * @param messageResId 表示するメッセージの文字列リソースID
     */
    protected void showErrorWithLogSend(int messageResId) {
        showErrorWithLogSend(getString(messageResId));
    }

    protected void showErrorWithLogSend(String message) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.error_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton(R.string.send_log,
                        (dialog, which) -> org.androiddaisyreader.utils.LogSender.shareLog(this))
                .show();
    }

    /**
     * default onBackPressedHandler
     */
    protected boolean onBackPressedHandled() {
        return false;
    }
}
