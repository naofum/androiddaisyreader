package com.github.library.dodp.download;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Callback interface for download progress.
 */
public interface DownloadProgressListener {

    /**
     * @param downloadedBytes bytes received so far
     * @param totalBytes      total expected bytes, or -1 if unknown
     */
    default void onProgress(long downloadedBytes, long totalBytes) {
    }

    default void onCompleted(Path destination) {
    }

    default void onFailed(IOException error) {
    }
}
