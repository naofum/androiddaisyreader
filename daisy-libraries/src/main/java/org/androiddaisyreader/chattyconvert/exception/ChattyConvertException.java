package org.androiddaisyreader.chattyconvert.exception;

/**
 * Chatty Library形式からEPUBへの変換時に発生する例外。
 * 入力ファイルの欠如、解析失敗、生成失敗などでスローされる。
 */
public class ChattyConvertException extends Exception {

    public ChattyConvertException(String message) {
        super(message);
    }

    public ChattyConvertException(String message, Throwable cause) {
        super(message, cause);
    }
}
