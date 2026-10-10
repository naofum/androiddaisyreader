package com.github.library.dodp.download;

import com.github.library.dodp.model.Resource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Manages the download of a single resource file, tracking its state and
 * progress. Intended to be submitted to an executor for background download.
 */
public final class DownloadTask implements Runnable {

    public enum State {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }

    private final String contentId;
    private final Resource resource;
    private final Path destination;
    private final ContentDownloader downloader;
    private final DownloadProgressListener listener;

    private volatile State state = State.PENDING;
    private volatile long downloadedBytes;
    private volatile long totalBytes = -1;
    private volatile IOException error;

    public DownloadTask(String contentId, Resource resource, Path destination,
                        ContentDownloader downloader, DownloadProgressListener listener) {
        this.contentId = Objects.requireNonNull(contentId, "contentId");
        this.resource = Objects.requireNonNull(resource, "resource");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.downloader = downloader;
        this.listener = listener;
    }

    public String getContentId() {
        return contentId;
    }

    public Resource getResource() {
        return resource;
    }

    public Path getDestination() {
        return destination;
    }

    public State getState() {
        return state;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public IOException getError() {
        return error;
    }

    public boolean isDone() {
        return state == State.COMPLETED || state == State.FAILED || state == State.CANCELLED;
    }

    @Override
    public void run() {
        state = State.RUNNING;
        try {
            downloader.download(resource, destination, true, new DownloadProgressListener() {
                @Override
                public void onProgress(long downloaded, long total) {
                    downloadedBytes = downloaded;
                    totalBytes = total;
                    if (listener != null) {
                        listener.onProgress(downloaded, total);
                    }
                }

                @Override
                public void onCompleted(Path destination) {
                    state = State.COMPLETED;
                    if (listener != null) {
                        listener.onCompleted(destination);
                    }
                }

                @Override
                public void onFailed(IOException error) {
                    DownloadTask.this.error = error;
                    state = State.FAILED;
                    if (listener != null) {
                        listener.onFailed(error);
                    }
                }
            });
            if (state == State.RUNNING) {
                state = State.COMPLETED;
            }
        } catch (IOException e) {
            this.error = e;
            state = State.FAILED;
            if (listener != null) {
                listener.onFailed(this.error);
            }
        }
    }

    public void cancel() {
        state = State.CANCELLED;
    }
}
