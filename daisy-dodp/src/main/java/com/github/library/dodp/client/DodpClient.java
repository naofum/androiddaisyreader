package com.github.library.dodp.client;

import com.github.library.dodp.DodpNamespaces;
import com.github.library.dodp.exception.DodpException;
import com.github.library.dodp.exception.NoActiveSessionException;
import com.github.library.dodp.model.Announcements;
import com.github.library.dodp.model.BookmarkSet;
import com.github.library.dodp.model.ContentList;
import com.github.library.dodp.model.ContentMetadata;
import com.github.library.dodp.model.ReadingSystemAttributes;
import com.github.library.dodp.model.Resources;
import com.github.library.dodp.model.ServiceAttributes;
import com.github.library.dodp.model.UserResponses;
import com.github.library.dodp.soap.SoapClient;
import com.github.library.dodp.util.XmlUtil;
import org.w3c.dom.Element;

import java.util.List;

import static com.github.library.dodp.DodpNamespaces.DAISY_ONLINE;

/**
 * Main entry point for the DODP client. Provides the full set of DODP v1
 * operations and manages the session lifecycle:
 *
 * <pre>
 * logOn → getServiceAttributes → setReadingSystemAttributes → ... → logOff
 * </pre>
 *
 * When {@code autoRelogin} is enabled (the default) and credentials were
 * supplied to {@link #logOn}, a {@link NoActiveSessionException} is
 * transparently recovered by re-running the initialization sequence and
 * retrying the operation once.
 */
public final class DodpClient implements AutoCloseable {

    private final DodpConfiguration config;
    private final SoapClient soapClient;
    private final DodpSession session;

    private String username;
    private String password;
    private ReadingSystemAttributes readingSystemAttributes;

    public DodpClient(DodpConfiguration config) {
        this(config, new SoapClient(config));
    }

    DodpClient(DodpConfiguration config, SoapClient soapClient) {
        this.config = config;
        this.soapClient = soapClient;
        this.session = new DodpSession(soapClient);
    }

    public DodpConfiguration getConfiguration() {
        return config;
    }

    public DodpSession getSession() {
        return session;
    }

    // ------------------------------------------------------------------
    // Session management
    // ------------------------------------------------------------------

    /**
     * Authenticates against the service. On success a session cookie is issued
     * and automatically attached to all subsequent requests.
     */
    public boolean logOn(String username, String password) throws DodpException {
        Element response = soapClient.invoke("logOn", op -> {
            XmlUtil.appendChildText(op, DAISY_ONLINE, "username", username);
            XmlUtil.appendChildText(op, DAISY_ONLINE, "password", password);
        });
        boolean result = parseBooleanResult(response, "logOnResult");
        if (result) {
            this.username = username;
            if (config.isAutoRelogin()) {
                this.password = password;
            }
            session.markLoggedIn(username);
        } else {
            session.clear();
        }
        return result;
    }

    /**
     * Ends the current session and discards the session cookie.
     */
    public boolean logOff() throws DodpException {
        Element response = soapClient.invoke("logOff", null);
        boolean result = parseBooleanResult(response, "logOffResult");
        this.username = null;
        this.password = null;
        this.readingSystemAttributes = null;
        session.invalidate();
        return result;
    }

    /**
     * Runs the initialization sequence ({@code getServiceAttributes} +
     * {@code setReadingSystemAttributes}) for a logged-in session.
     */
    public ServiceAttributes initialize(ReadingSystemAttributes attributes) throws DodpException {
        ServiceAttributes serviceAttributes = getServiceAttributes();
        setReadingSystemAttributes(attributes);
        return serviceAttributes;
    }

    // ------------------------------------------------------------------
    // Core operations
    // ------------------------------------------------------------------

    public ServiceAttributes getServiceAttributes() throws DodpException {
        Element response = soapClient.invoke("getServiceAttributes", null);
        Element attributes = XmlUtil.firstChildElement(response, DAISY_ONLINE, "serviceAttributes");
        return attributes == null ? null : ServiceAttributes.parse(attributes);
    }

    public boolean setReadingSystemAttributes(ReadingSystemAttributes attributes) throws DodpException {
        Element response = soapClient.invoke("setReadingSystemAttributes", op -> {
            op.appendChild(attributes.toElement(op.getOwnerDocument()));
        });
        boolean result = parseBooleanResult(response, "setReadingSystemAttributesResult");
        if (result) {
            this.readingSystemAttributes = attributes;
            session.markReadingSystemAttributesSent();
        }
        return result;
    }

    public ContentList getContentList(String id, int firstItem, int lastItem) throws DodpException {
        return execute(() -> {
            Element response = soapClient.invoke("getContentList", op -> {
                XmlUtil.appendChildText(op, DAISY_ONLINE, "id", id);
                XmlUtil.appendChildText(op, DAISY_ONLINE, "firstItem", String.valueOf(firstItem));
                XmlUtil.appendChildText(op, DAISY_ONLINE, "lastItem", String.valueOf(lastItem));
            });
            Element list = XmlUtil.firstChildElement(response, DAISY_ONLINE, "contentList");
            return list == null ? null : ContentList.parse(list);
        });
    }

    /** Convenience overload returning the whole list ({@code firstItem=0}, {@code lastItem=-1}). */
    public ContentList getContentList(String id) throws DodpException {
        return getContentList(id, 0, -1);
    }

    public ContentMetadata getContentMetadata(String contentId) throws DodpException {
        return execute(() -> {
            Element response = soapClient.invoke("getContentMetadata", op ->
                    XmlUtil.appendChildText(op, DAISY_ONLINE, "contentID", contentId));
            Element metadata = XmlUtil.firstChildElement(response, DAISY_ONLINE, "contentMetadata");
            return metadata == null ? null : ContentMetadata.parse(metadata);
        });
    }

    public Resources getContentResources(String contentId) throws DodpException {
        return execute(() -> {
            Element response = soapClient.invoke("getContentResources", op ->
                    XmlUtil.appendChildText(op, DAISY_ONLINE, "contentID", contentId));
            Element resources = XmlUtil.firstChildElement(response, DAISY_ONLINE, "resources");
            return resources == null ? null : Resources.parse(resources);
        });
    }

    public boolean issueContent(String contentId) throws DodpException {
        return execute(() -> {
            Element response = soapClient.invoke("issueContent", op ->
                    XmlUtil.appendChildText(op, DAISY_ONLINE, "contentID", contentId));
            return parseBooleanResult(response, "issueContentResult");
        });
    }

    public boolean returnContent(String contentId) throws DodpException {
        return execute(() -> {
            Element response = soapClient.invoke("returnContent", op ->
                    XmlUtil.appendChildText(op, DAISY_ONLINE, "contentID", contentId));
            return parseBooleanResult(response, "returnContentResult");
        });
    }

    // ------------------------------------------------------------------
    // Bookmarks (optional)
    // ------------------------------------------------------------------

    public boolean setBookmarks(String contentId, BookmarkSet bookmarkSet) throws DodpException {
        return execute(() -> {
            Element response = soapClient.invoke("setBookmarks", op -> {
                XmlUtil.appendChildText(op, DAISY_ONLINE, "contentID", contentId);
                op.appendChild(bookmarkSet.toElement(op.getOwnerDocument()));
            });
            return parseBooleanResult(response, "setBookmarksResult");
        });
    }

    public BookmarkSet getBookmarks(String contentId) throws DodpException {
        return execute(() -> {
            Element response = soapClient.invoke("getBookmarks", op ->
                    XmlUtil.appendChildText(op, DAISY_ONLINE, "contentID", contentId));
            Element bookmarkSet = XmlUtil.firstChildElement(response, DodpNamespaces.BOOKMARK, "bookmarkSet");
            return bookmarkSet == null ? null : BookmarkSet.parse(bookmarkSet);
        });
    }

    // ------------------------------------------------------------------
    // Announcements (optional)
    // ------------------------------------------------------------------

    public Announcements getServiceAnnouncements() throws DodpException {
        return execute(() -> {
            Element response = soapClient.invoke("getServiceAnnouncements", null);
            Element announcements = XmlUtil.firstChildElement(response, DAISY_ONLINE, "announcements");
            return announcements == null ? null : Announcements.parse(announcements);
        });
    }

    public boolean markAnnouncementsAsRead(List<String> announcementIds) throws DodpException {
        return execute(() -> {
            Element response = soapClient.invoke("markAnnouncementsAsRead", op -> {
                Element read = XmlUtil.appendChild(op, DAISY_ONLINE, "read");
                if (announcementIds != null) {
                    for (String id : announcementIds) {
                        XmlUtil.appendChildText(read, DAISY_ONLINE, "item", id);
                    }
                }
            });
            return parseBooleanResult(response, "markAnnouncementsAsReadResult");
        });
    }

    // ------------------------------------------------------------------
    // Optional / advanced operations
    // ------------------------------------------------------------------

    /**
     * Dynamic Menus. Returns the raw {@code questions} element; use the DOM
     * directly for the rarely used dynamic menu model.
     */
    public Element getQuestions(UserResponses userResponses) throws DodpException {
        return execute(() -> {
            Element response = soapClient.invoke("getQuestions", op ->
                    op.appendChild(userResponses.toElement(op.getOwnerDocument())));
            return XmlUtil.firstChildElement(response, DAISY_ONLINE, "questions");
        });
    }

    /**
     * PDTB2 key exchange. Returns the raw {@code KeyExchange} element.
     */
    public Element getKeyExchangeObject(String requestedKeyName) throws DodpException {
        return execute(() -> {
            Element response = soapClient.invoke("getKeyExchangeObject", op ->
                    XmlUtil.appendChildText(op, DAISY_ONLINE, "requestedKeyName", requestedKeyName));
            return XmlUtil.firstChildElement(response, DodpNamespaces.PDTB2, "KeyExchange");
        });
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private interface Operation<T> {
        T run() throws DodpException;
    }

    private <T> T execute(Operation<T> operation) throws DodpException {
        try {
            return operation.run();
        } catch (NoActiveSessionException e) {
            if (config.isAutoRelogin() && username != null && password != null) {
                reinitialize();
                return operation.run();
            }
            throw e;
        }
    }

    private synchronized void reinitialize() throws DodpException {
        session.invalidate();
        ReadingSystemAttributes attributes = this.readingSystemAttributes;
        this.readingSystemAttributes = null;
        logOn(this.username, this.password);
        if (attributes != null) {
            setReadingSystemAttributes(attributes);
        }
    }

    private boolean parseBooleanResult(Element response, String resultName) {
        String value = XmlUtil.childText(response, resultName);
        return XmlUtil.parseBoolean(value);
    }

    @Override
    public void close() {
        soapClient.close();
    }
}
