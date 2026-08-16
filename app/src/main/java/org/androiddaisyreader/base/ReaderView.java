package org.androiddaisyreader.base;

import org.androiddaisyreader.model.CurrentInformation;

/**
 * MVP パターンの View インターフェース。
 * VisualModeActivity / SimpleModeActivity が実装する。
 * Presenter から View への UI 更新コールバックを定義する。
 */
public interface ReaderView {

    /** Activity名を返す（CurrentInformation保存用） */
    String getActivityName();

    /** Activity が終了中か */
    boolean isActivityFinishing();

    /** 再生中UIに切り替え */
    void showPlayingState();

    /** 一時停止UIに切り替え */
    void showPausedState();

    /** 末尾到達時のフィードバック */
    void onReachedEndOfBook();

    /** 先頭到達時のフィードバック */
    void onReachedBeginOfBook();

    /** セクション変更完了後のUI更新（再生位置追跡の開始など） */
    void onSectionLoaded();

    /** センテンス移動後のUI更新 */
    void onSentenceChanged(int positionSentence);

    /** エラーダイアログを表示 */
    void showErrorDialog(Exception e);

    /** テキストコンテンツの表示（VisualModeで実装、SimpleModeでは空実装可） */
    void displayContent(String content);

    /** 画像表示（SimpleModeで実装、VisualModeでは空実装可） */
    void displayImage(String imageSrc);

    /** テキスト読み上げ */
    void speak(String text);

    /** 読み上げ停止 */
    void stopSpeaking();

    /**
     * 指定されたセンテンスをTTSで読み上げる（書籍コンテンツ読み上げ用）。
     * 読み上げ完了時は onUtteranceCompleted をPresenterに通知する。
     * @param text 読み上げテキスト
     * @param sentenceIndex センテンス位置（utteranceId用）
     */
    void speakSentence(String text, int sentenceIndex);

    /** TTS読み上げを停止する（書籍コンテンツ読み上げ用） */
    void stopReadAloud();

    /** 書籍コンテンツの読み上げ言語/速度を設定に基づいて適用する */
    void applyReadAloudSettings();

    /** 読み上げ中のハイライト表示（VisualMode用。SimpleModeでは空実装可） */
    void highlightSentence(int sentenceIndex);
}
