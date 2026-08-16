package org.androiddaisyreader.model;

/**
 * ルビ表示設定を保持するグローバル設定クラス。
 * アプリ層から設定値をセットし、DaisySnippet 等のライブラリ層から参照する。
 */
public class RubyConfig {

    /** ルビを使用する（底字を除去） */
    public static final String MODE_RUBY = "ruby";
    /** 底字を使用する（ルビを除去） */
    public static final String MODE_BASE = "base";

    private static volatile String mode = MODE_RUBY;

    /**
     * ルビ表示モードを設定する。
     * @param rubyMode MODE_RUBY または MODE_BASE
     */
    public static void setMode(String rubyMode) {
        if (MODE_RUBY.equals(rubyMode) || MODE_BASE.equals(rubyMode)) {
            mode = rubyMode;
        }
    }

    /**
     * 現在のルビ表示モードを取得する。
     */
    public static String getMode() {
        return mode;
    }

    /**
     * ルビモードかどうか（底字を除去してルビを表示）。
     */
    public static boolean isRubyMode() {
        return MODE_RUBY.equals(mode);
    }

    /**
     * 底字モードかどうか（ルビを除去して底字を表示）。
     */
    public static boolean isBaseMode() {
        return MODE_BASE.equals(mode);
    }
}
