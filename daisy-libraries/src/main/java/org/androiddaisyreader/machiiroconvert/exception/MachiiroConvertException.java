package org.androiddaisyreader.machiiroconvert.exception;

/**
 * マチイロの広報誌からEPUBへの変換時に発生する例外。
 * JSONの解析失敗、ページ画像の抽出失敗、EPUB生成失敗などでスローされる。
 */
public class MachiiroConvertException extends Exception {

    public MachiiroConvertException(String message) {
        super(message);
    }

    public MachiiroConvertException(String message, Throwable cause) {
        super(message, cause);
    }
}
