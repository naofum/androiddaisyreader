package org.androiddaisyreader.kohoconvert.exception;

/**
 * 自治体広報（音声版）サイトへのアクセス時に発生する例外。
 * トップページの取得失敗やネットワークエラー時にスローされる。
 */
public class KohoException extends Exception {

    public KohoException(String message) {
        super(message);
    }

    public KohoException(String message, Throwable cause) {
        super(message, cause);
    }
}
