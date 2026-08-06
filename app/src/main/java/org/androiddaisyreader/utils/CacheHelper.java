package org.androiddaisyreader.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * content:// URI のファイルをアプリキャッシュディレクトリにコピーするユーティリティ。
 *
 * content:// URI は ZipFile で直接開けないため、一度ローカルにコピーしてから
 * ZippedBookContext で開く方式に統一する。
 */
public class CacheHelper {

    private static final String TAG = "CacheHelper";
    private static final String CACHE_SUBDIR = "books";
    private static final int BUFFER_SIZE = 8192;
    /** キャッシュ合計サイズ上限（200MB） */
    private static final long MAX_CACHE_SIZE = 200L * 1024 * 1024;

    /**
     * content:// URI のファイルをキャッシュにコピーし、ローカルファイルパスを返す。
     * 既にキャッシュ済みの場合は既存ファイルのパスを返す。
     *
     * @param context アプリケーションコンテキスト
     * @param contentUri content:// URI文字列
     * @return キャッシュされたローカルファイル
     * @throws IOException コピー失敗時
     */
    public static File copyToCache(Context context, String contentUri) throws IOException {
        return copyToCache(context, Uri.parse(contentUri));
    }

    /**
     * content:// URI のファイルをキャッシュにコピーし、ローカルファイルパスを返す。
     * 既にキャッシュ済みの場合は既存ファイルのパスを返す。
     *
     * @param context アプリケーションコンテキスト
     * @param contentUri content:// URI
     * @return キャッシュされたローカルファイル
     * @throws IOException コピー失敗時
     */
    public static File copyToCache(Context context, Uri contentUri) throws IOException {
        File cacheDir = getCacheDir(context);
        String cacheFileName = generateCacheFileName(context, contentUri);
        File cacheFile = new File(cacheDir, cacheFileName);

        if (cacheFile.exists() && cacheFile.length() > 0) {
            Log.d(TAG, "Cache hit: " + cacheFile.getAbsolutePath());
            return cacheFile;
        }

        // キャッシュ容量管理
        evictIfNeeded(cacheDir);

        // content:// URI からキャッシュへコピー
        try (InputStream in = context.getContentResolver().openInputStream(contentUri)) {
            if (in == null) {
                throw new IOException("Cannot open input stream for: " + contentUri);
            }
            try (FileOutputStream out = new FileOutputStream(cacheFile)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }
        } catch (IOException e) {
            // コピー失敗時は不完全ファイルを削除
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            throw e;
        }

        Log.d(TAG, "Cached: " + contentUri + " -> " + cacheFile.getAbsolutePath());
        return cacheFile;
    }

    /**
     * 指定された content:// URI に対応するキャッシュファイルが存在するか確認する。
     *
     * @param context アプリケーションコンテキスト
     * @param contentUri content:// URI文字列
     * @return キャッシュファイルが存在すればtrue
     */
    public static boolean isCached(Context context, String contentUri) {
        File cacheDir = getCacheDir(context);
        String cacheFileName = generateCacheFileName(context, Uri.parse(contentUri));
        File cacheFile = new File(cacheDir, cacheFileName);
        return cacheFile.exists() && cacheFile.length() > 0;
    }

    /**
     * 指定された content:// URI に対応するキャッシュファイルのパスを返す。
     * キャッシュが存在しない場合は null を返す。
     *
     * @param context アプリケーションコンテキスト
     * @param contentUri content:// URI文字列
     * @return キャッシュファイルパス、またはnull
     */
    public static String getCachedPath(Context context, String contentUri) {
        File cacheDir = getCacheDir(context);
        String cacheFileName = generateCacheFileName(context, Uri.parse(contentUri));
        File cacheFile = new File(cacheDir, cacheFileName);
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile.getAbsolutePath();
        }
        return null;
    }

    /**
     * 書籍キャッシュディレクトリ内の全ファイルを削除する。
     *
     * @param context アプリケーションコンテキスト
     */
    public static void clearCache(Context context) {
        File cacheDir = getCacheDir(context);
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        Log.d(TAG, "Cache cleared");
    }

    /**
     * キャッシュディレクトリを取得（存在しない場合は作成）。
     */
    private static File getCacheDir(Context context) {
        File cacheDir = new File(context.getCacheDir(), CACHE_SUBDIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        return cacheDir;
    }

    /**
     * URI からキャッシュファイル名を生成する。
     * ContentResolver からMIMEタイプを取得し、正しい拡張子を決定する。
     */
    private static String generateCacheFileName(Context context, Uri uri) {
        String uriString = uri.toString();

        // MIMEタイプから拡張子を判定
        String extension = ".zip"; // デフォルト
        try {
            String mimeType = context.getContentResolver().getType(uri);
            if (mimeType != null) {
                if (mimeType.contains("epub")) {
                    extension = ".epub";
                }
            } else {
                // MIMEタイプが取れない場合はURI文字列で推定
                if (uriString.toLowerCase().contains("epub")) {
                    extension = ".epub";
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get MIME type for: " + uri + ", falling back to URI inspection", e);
            // ContentResolver失敗時はURI文字列で推定
            if (uriString.toLowerCase().contains("epub")) {
                extension = ".epub";
            }
        }

        // ハッシュ値でファイル名を一意にする
        int hash = uriString.hashCode();
        return "book_" + Integer.toHexString(hash) + extension;
    }

    /**
     * キャッシュ容量が上限を超えている場合、古いファイルから削除する。
     */
    private static void evictIfNeeded(File cacheDir) {
        File[] files = cacheDir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }

        if (totalSize <= MAX_CACHE_SIZE) {
            return;
        }

        // 最終更新日が古い順にソートして削除
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));

        for (File file : files) {
            if (totalSize <= MAX_CACHE_SIZE) {
                break;
            }
            long fileSize = file.length();
            if (file.delete()) {
                totalSize -= fileSize;
                Log.d(TAG, "Evicted: " + file.getName());
            }
        }
    }
}
