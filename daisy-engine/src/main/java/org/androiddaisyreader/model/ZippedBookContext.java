package org.androiddaisyreader.model;

import android.os.Build;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Represents the BookContext for a zipped book.
 * 
 * Note: Currently this doesn't check the contents of the zip file contain a
 * valid book. We can consider adding checks e.g. by passing in a 'check' method
 * in the constructor. For now we'll start simple :)
 * 
 * @author Julian Harty
 * 
 */
public class ZippedBookContext implements BookContext {
    private static final String TAG = "ZippedBookContext";
    private ZipFile zipContents;

    protected ZippedBookContext() {
        // Do nothing.
    }

    public ZippedBookContext(String zipFilename) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || Build.VERSION.SDK_INT == 0) {
            // UTF-8 を優先し、日本語ロケール時は MS932 にフォールバック（epub/zip 共通）
            try {
                zipContents = new ZipFile(zipFilename);
            } catch (ZipException e) {
                if (java.util.Locale.getDefault().getLanguage().equals("ja")) {
                    zipContents = new ZipFile(zipFilename, Charset.forName("MS932"));
                } else {
                    throw e;
                }
            }
        } else {
            zipContents = new ZipFile(zipFilename);
        }
    }

    public void reopen(Charset charset) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || Build.VERSION.SDK_INT == 0) {
            zipContents = new ZipFile(zipContents.getName(), charset);
        }
    }

    public InputStream getResource(String uri) throws IOException {
        ZipSecurity.validateResourceUri(uri);

        // "../" を含む相対パスを正規化（例: "../chapter_0001.xhtml" → "chapter_0001.xhtml"）
        String normalizedUri = normalizeUri(uri);
        android.util.Log.d(TAG, "getResource: 検索対象エントリ \"" + uri + "\" (正規化: \""
                + normalizedUri + "\") in \"" + zipContents.getName() + "\"");

        ZipEntry entry;
        Enumeration<? extends ZipEntry> e = zipContents.entries();
        while (e.hasMoreElements()) {
            entry = (ZipEntry) e.nextElement();
            String entryName = ZipSecurity.validateEntryName(entry.getName());

            // ファイル名の完全一致またはパス末尾一致で検索
            if (entryName.equalsIgnoreCase(normalizedUri)
                    || entryName.toLowerCase().endsWith("/" + normalizedUri.toLowerCase())) {
                android.util.Log.i(TAG, "getResource: 見つかりました \"" + uri + "\" -> \""
                        + entryName + "\"");
                return new BufferedInputStream(zipContents.getInputStream(entry), ModelConsts.BUFFER_SIZE);
            }
        }
        android.util.Log.w(TAG, "getResource: 見つかりません \"" + uri + "\" (正規化: \""
                + normalizedUri + "\")");
        return null;
    }

    /**
     * 相対パスを正規化する。"../" セグメントを除去する。
     */
    private String normalizeUri(String uri) {
        // "../" を除去
        String result = uri;
        while (result.startsWith("../")) {
            result = result.substring(3);
        }
        // 中間の "foo/../" パターンも除去
        while (result.contains("/../")) {
            int idx = result.indexOf("/../");
            int prev = result.lastIndexOf('/', idx - 1);
            if (prev >= 0) {
                result = result.substring(0, prev) + result.substring(idx + 3);
            } else {
                result = result.substring(idx + 4);
            }
        }
        return result;
    }

    public String getBaseUri() {
        return zipContents.getName();
    }

    /**
     * デバッグ用: zip内のエントリ数を返す。
     */
    public int getEntryCount() {
        return zipContents.size();
    }

}
