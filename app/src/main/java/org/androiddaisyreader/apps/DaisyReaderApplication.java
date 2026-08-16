package org.androiddaisyreader.apps;

import android.app.Application;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;

import org.androiddaisyreader.model.RubyConfig;
import org.androiddaisyreader.utils.Constants;

import java.util.Locale;

/**
 * アプリケーションスコープでTTSインスタンスを管理する。
 * Activity毎の初期化を不要にし、画面遷移時のTTS遅延を解消する。
 */
public class DaisyReaderApplication extends Application implements TextToSpeech.OnInitListener {

    private static DaisyReaderApplication instance;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private OnTtsReadyListener pendingListener;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        tts = new TextToSpeech(this, this);

        // PDFBox（pdfbox2-android）の初期化
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
        } catch (Exception e) {
            android.util.Log.e("DaisyReaderApplication", "PDFBoxResourceLoader.init failed", e);
        }

        // ファイルログの初期化
        org.androiddaisyreader.utils.LogFile.init(getApplicationContext());

        // ルビ表示モードを設定から読み込み
        String rubyMode = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(Constants.RUBY_DISPLAY_MODE, Constants.RUBY_MODE_RUBY);
        RubyConfig.setMode(rubyMode);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            Locale locale = Locale.getDefault();
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                tts.setLanguage(locale);
            } else {
                tts.setLanguage(Locale.US);
            }
            // ペンディングリスナーがあれば通知
            if (pendingListener != null) {
                pendingListener.onTtsReady();
                pendingListener = null;
            }
        }
    }

    public static DaisyReaderApplication getInstance() {
        return instance;
    }

    /**
     * 共有TTSインスタンスを取得する。
     */
    public TextToSpeech getTts() {
        return tts;
    }

    /**
     * TTSが初期化完了しているか。
     */
    public boolean isTtsReady() {
        return ttsReady;
    }

    /**
     * TTS初期化完了時にコールバックを受け取る。
     * 既に初期化完了していれば即座にコールバックを呼ぶ。
     */
    public void setOnTtsReadyListener(OnTtsReadyListener listener) {
        if (ttsReady) {
            listener.onTtsReady();
        } else {
            pendingListener = listener;
        }
    }

    public interface OnTtsReadyListener {
        void onTtsReady();
    }

    @Override
    public void onTerminate() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ttsReady = false;
        super.onTerminate();
    }
}
