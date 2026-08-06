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
     * 
     * @param uri リソースURI
     * @return 安全なURI
     * @throws SecurityException パストラバーサルが検出された場合
     */
    public static String validateResourceUri(String uri) {
        if (uri == null) {
            throw new SecurityException("Resource URI is null");
        }
        // 正規化後に親ディレクトリ参照が残る場合は拒否
        String normalized = uri.replace("\\", "/");
        // "../" で始まる相対パスを複数回除去した後に残る ".." を検出
        String[] parts = normalized.split("/");
        int depth = 0;
        for (String part : parts) {
            if ("..".equals(part)) {
                depth--;
                if (depth < 0) {
                    throw new SecurityException(
                            "Resource URI contains path traversal: " + uri);
                }
            } else if (!".".equals(part) && !part.isEmpty()) {
                depth++;
            }
        }
        // 絶対パスを拒否する
        if (normalized.startsWith("/")) {
            throw new SecurityException(
                    "Resource URI contains absolute path: " + uri);
        }
        return uri;
    }
}
