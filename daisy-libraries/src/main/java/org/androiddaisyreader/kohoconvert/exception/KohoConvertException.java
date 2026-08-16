package org.androiddaisyreader.kohoconvert.exception;

/**
 * 音声版広報からEPUBへの変換時に発生する例外。
 * index.htmlの欠如、解析失敗、出力失敗などでスローされる。
 */
public class KohoConvertException extends Exception {

    public KohoConvertException(String message) {
        super(message);
    }

    public KohoConvertException(String message, Throwable cause) {
        super(message, cause);
    }
}
