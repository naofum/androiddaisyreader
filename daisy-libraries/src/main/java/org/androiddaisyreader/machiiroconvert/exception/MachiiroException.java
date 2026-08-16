package org.androiddaisyreader.machiiroconvert.exception;

/**
 * マチイロ（広報誌）へのアクセス時に発生する例外。
 * ネットワークエラー、自治体・発行物が見つからない場合などにスローされる。
 */
public class MachiiroException extends Exception {

    public MachiiroException(String message) {
        super(message);
    }

    public MachiiroException(String message, Throwable cause) {
        super(message, cause);
    }
}
