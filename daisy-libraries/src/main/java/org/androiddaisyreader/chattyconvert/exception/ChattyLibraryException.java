package org.androiddaisyreader.chattyconvert.exception;

/**
 * Chatty Libraryアクセス時に発生する例外。
 * ログイン失敗、ネットワークエラー、図書が見つからない場合などにスローされる。
 */
public class ChattyLibraryException extends Exception {

    public ChattyLibraryException(String message) {
        super(message);
    }

    public ChattyLibraryException(String message, Throwable cause) {
        super(message, cause);
    }
}
