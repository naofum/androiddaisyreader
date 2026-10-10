package com.github.library.dodp.lending;

import com.github.library.dodp.model.Resources;

/**
 * Receives download requests discovered during {@link LendingService#sync}.
 * Implementations queue resources for {@code ContentDownloader} or trigger
 * updates of already-downloaded content.
 */
public interface DownloadHandler {

    /** Queue a newly issued content item for download. */
    void queue(String contentId, Resources resources);

    /** Update an already-downloaded content item whose resources changed. */
    void update(String contentId, Resources resources);
}
