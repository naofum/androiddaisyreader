package com.github.library.dodp.lending;

import java.time.ZonedDateTime;

/**
 * A content item that is currently on loan to the user. Tracks the return
 * deadline advertised by the server ({@code resources.returnBy}).
 */
public final class ContentLoan {

    private final String contentId;
    private final ZonedDateTime returnBy;

    public ContentLoan(String contentId, ZonedDateTime returnBy) {
        this.contentId = contentId;
        this.returnBy = returnBy;
    }

    public String getContentId() {
        return contentId;
    }

    /** Deadline by which the content must be returned, or null if not set. */
    public ZonedDateTime getReturnBy() {
        return returnBy;
    }

    public boolean isOverdue(ZonedDateTime now) {
        return returnBy != null && now != null && now.isAfter(returnBy);
    }
}
