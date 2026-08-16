package org.androiddaisyreader.model;

/**
 * ZIPエントリ名のセキュリティバリデーションユーティリティ。
 * Zip Slip（ZIPパストラバーサル）攻撃を防止する。
 */
public class ZipSecurity {

    private ZipSecurity() {
        // ユーティリティクラスのためインスタンス化を防止
    }

    /**
     * ZIPエントリ名にパストラバーサルが含まれていないか検証する。
     * 
     * @param entryName ZIPエントリ名
     * @return 安全なエントリ名
     * @throws SecurityException パストラバーサルが検出された場合
     */
    public static String validateEntryName(String entryName) {
        if (entryName == null) {
            throw new SecurityException("ZIP entry name is null");
        }
        // パストラバーサル文字列を検出して拒否する
        if (entryName.contains("..")) {
            throw new SecurityException(
                    "ZIP entry contains path traversal: " + entryName);
        }
        // 絶対パスを拒否する
        if (entryName.startsWith("/") || entryName.startsWith("\\")) {
            throw new SecurityException(
                    "ZIP entry contains absolute path: " + entryName);
        }
        return entryName;
    }

    /**
     * URI（リソースパス）にパストラバーサルが含まれていないか検証する。
     * EPUB/DAISY 内では "../" で始まる相対パス参照は正当なため、
     * ルート（ZIPルートまたはベースディレクトリ）を超える脱出のみ拒否する。
     * 
     * 例: "OEBPS/../content.opf" → OK（depth >= 0）
     *     "../images/cover.png" → OK（呼び出し元で基準パスからの相対解決を行うため）
     *     "../../etc/passwd" → NG（意図的な脱出パターン）
     * 
     * @param uri リソースURI
     * @return 安全なURI
     * @throws SecurityException 明らかな攻撃パターンが検出された場合
     */
    public static String validateResourceUri(String uri) {
        if (uri == null) {
            throw new SecurityException("Resource URI is null");
        }
        // 絶対パスを拒否する
        String normalized = uri.replace("\\", "/");
        if (normalized.startsWith("/")) {
            throw new SecurityException(
                    "Resource URI contains absolute path: " + uri);
        }
        // 連続した ".." による明示的な脱出パターンを検出
        // "../../" のように2段以上の親ディレクトリ参照は攻撃と見なす
        if (normalized.contains("../../")) {
            throw new SecurityException(
                    "Resource URI contains path traversal: " + uri);
        }
        return uri;
    }
}
