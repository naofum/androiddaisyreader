package com.github.library.dodp.model;

import com.github.library.dodp.util.DateTimes;
import com.github.library.dodp.util.XmlUtil;
import org.w3c.dom.Element;

import java.time.ZonedDateTime;

/**
 * A single downloadable file belonging to a content item.
 */
public final class Resource {

    private final String uri;
    private final String mimeType;
    private final long size;
    private final String localUri;
    private final ZonedDateTime lastModifiedDate;

    public Resource(String uri, String mimeType, long size, String localUri,
                    ZonedDateTime lastModifiedDate) {
        this.uri = uri;
        this.mimeType = mimeType;
        this.size = size;
        this.localUri = localUri;
        this.lastModifiedDate = lastModifiedDate;
    }

    /** Actual download location (may route through a script). */
    public String getUri() {
        return uri;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSize() {
        return size;
    }

    /** Relative URI used to resolve links within the content. */
    public String getLocalUri() {
        return localUri;
    }

    public ZonedDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public static Resource parse(Element element) {
        return new Resource(
                XmlUtil.attribute(element, "uri"),
                XmlUtil.attribute(element, "mimeType"),
                XmlUtil.parseLong(XmlUtil.attribute(element, "size")) == null
                        ? 0 : XmlUtil.parseLong(XmlUtil.attribute(element, "size")),
                XmlUtil.attribute(element, "localURI"),
                DateTimes.parse(XmlUtil.attribute(element, "lastModifiedDate")));
    }
}
