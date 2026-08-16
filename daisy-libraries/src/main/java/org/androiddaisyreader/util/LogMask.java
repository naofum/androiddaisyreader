package org.androiddaisyreader.util;

/**
 * ログに出力する個人情報（ID等）をマスクするユーティリティ。
 */
public final class LogMask {

    private LogMask() {
    }

    /**
     * ログ出力用にIDをマスクする（先頭2文字のみ残す）。
     * 例: "abc12345" → "ab***"
     *
     * @param value マスク対象の文字列
     * @return マスク済み文字列。null/空は空文字、2文字以下は "***"
     */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= 2) {
            return "***";
        }
        return value.substring(0, 2) + "***";
    }
}
