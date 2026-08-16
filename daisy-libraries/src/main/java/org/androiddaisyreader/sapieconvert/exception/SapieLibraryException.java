package org.androiddaisyreader.sapieconvert.exception;

/**
 * サピエ図書館アクセス時に発生する例外。
 * ログイン失敗、ネットワークエラー、図書が見つからない場合などにスローされる。
 */
public class SapieLibraryException extends Exception {

    public SapieLibraryException(String message) {
        super(message);
    }

    public SapieLibraryException(String message, Throwable cause) {
        super(message, cause);
    }
}
