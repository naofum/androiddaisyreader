package com.github.library.dodp.lending;

import com.github.library.dodp.client.DodpClient;
import com.github.library.dodp.exception.DodpException;
import com.github.library.dodp.model.ContentItem;
import com.github.library.dodp.model.ContentList;
import com.github.library.dodp.model.ContentMetadata;
import com.github.library.dodp.model.Resources;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Higher-level lending operations built on {@link DodpClient}: issuing,
 * returning, and the full out-of-band synchronization sequence described in
 * the DODP specification.
 */
public final class LendingService {

    private final DodpClient client;

    public LendingService(DodpClient client) {
        this.client = client;
    }

    public DodpClient getClient() {
        return client;
    }

    /**
     * Issues a content item (starts the loan) and returns the resulting loan,
     * including the server-advertised return deadline.
     */
    public ContentLoan issue(String contentId) throws DodpException {
        client.issueContent(contentId);
        Resources resources = client.getContentResources(contentId);
        ZonedDateTime returnBy = resources == null ? null : resources.getReturnBy();
        return new ContentLoan(contentId, returnBy);
    }

    /** Returns a content item. Local files should be deleted beforehand. */
    public boolean returnContent(String contentId) throws DodpException {
        return client.returnContent(contentId);
    }

    public ContentList getContentList(String id) throws DodpException {
        return client.getContentList(id);
    }

    public ContentList getExpired() throws DodpException {
        return client.getContentList(ContentList.LIST_EXPIRED);
    }

    public ContentList getNew() throws DodpException {
        return client.getContentList(ContentList.LIST_NEW);
    }

    public ContentList getIssued() throws DodpException {
        return client.getContentList(ContentList.LIST_ISSUED);
    }

    /**
     * Runs the out-of-band synchronization sequence:
     * <ol>
     *   <li>send any offline returns</li>
     *   <li>delete expired content and return it</li>
     *   <li>issue and queue new content for download</li>
     *   <li>update issued content whose resources changed</li>
     * </ol>
     *
     * @return the number of newly issued items
     */
    public int sync(LocalContentStore store, DownloadHandler downloadHandler) throws DodpException {
        for (ContentLoan pending : store.getPendingReturns()) {
            client.returnContent(pending.getContentId());
            store.delete(pending.getContentId());
        }

        ContentList expired = client.getContentList(ContentList.LIST_EXPIRED);
        for (ContentItem item : safeItems(expired)) {
            store.delete(item.getId());
            client.returnContent(item.getId());
        }

        int issuedCount = 0;
        ContentList newItems = client.getContentList(ContentList.LIST_NEW);
        for (ContentItem item : safeItems(newItems)) {
            ContentMetadata metadata = client.getContentMetadata(item.getId());
            if (metadata != null && client.issueContent(item.getId())) {
                Resources resources = client.getContentResources(item.getId());
                if (resources != null) {
                    store.recordLoan(new ContentLoan(item.getId(), resources.getReturnBy()));
                    if (downloadHandler != null) {
                        downloadHandler.queue(item.getId(), resources);
                    }
                    issuedCount++;
                }
            }
        }

        ContentList issued = client.getContentList(ContentList.LIST_ISSUED);
        for (ContentItem item : safeItems(issued)) {
            Resources resources = client.getContentResources(item.getId());
            if (resources != null && store.isUpdated(item.getId(), resources.getLastModifiedDate())) {
                if (downloadHandler != null) {
                    downloadHandler.update(item.getId(), resources);
                }
            }
        }

        return issuedCount;
    }

    private List<ContentItem> safeItems(ContentList list) {
        if (list == null || list.getItems() == null) {
            return Collections.emptyList();
        }
        return list.getItems();
    }
}
