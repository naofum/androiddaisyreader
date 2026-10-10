package com.github.library.dodp.lending;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Abstraction over the reading system's local content database, used by
 * {@link LendingService#sync} to reconcile local state with the server.
 */
public interface LocalContentStore {

    /** Content that was returned while offline and still needs to be sent. */
    List<ContentLoan> getPendingReturns();

    /** Removes a content item and its local files. */
    void delete(String contentId);

    /**
     * Whether the locally stored copy of the content is older than the server
     * copy and therefore needs updating.
     */
    boolean isUpdated(String contentId, ZonedDateTime serverLastModifiedDate);

    /** Records a newly issued loan. */
    void recordLoan(ContentLoan loan);
}
