package org.androiddaisyreader.model;

/**
 * スキッパブル構造（ページ番号など）の読み飛ばし設定を保持するグローバル設定クラス。
 * アプリ層から設定値をセットし、ReaderPresenter 等から参照する。
 */
public class SkippableConfig {

    /** 読み飛ばしを有効にするかどうか。デフォルトは有効（読み飛ばす）。 */
    private static volatile boolean skipEnabled = true;

    /**
     * 読み飛ばし設定をセットする。
     * @param enabled true でスキッパブル構造を読み飛ばす。
     */
    public static void setSkipEnabled(boolean enabled) {
        skipEnabled = enabled;
    }

    /**
     * 読み飛ばし設定が有効かどうか。
     */
    public static boolean isSkipEnabled() {
        return skipEnabled;
    }

    /**
     * 指定された customTest の値がページ番号かどうか。
     * DAISY3 では DTBook の page 属性値（normal/front/special）に対応する
     * customTest 値がページ番号を表す。
     *
     * @param customTest SMIL の customTest 属性値
     * @return ページ番号なら true
     */
    public static boolean isPageNumberCustomTest(String customTest) {
        if (customTest == null) {
            return false;
        }
        return "normal".equals(customTest)
                || "front".equals(customTest)
                || "special".equals(customTest);
    }
}
