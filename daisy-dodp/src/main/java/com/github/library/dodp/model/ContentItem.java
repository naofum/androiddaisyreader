package com.github.library.dodp.model;

import com.github.library.dodp.util.DateTimes;
import com.github.library.dodp.util.XmlUtil;
import org.w3c.dom.Element;

import java.time.ZonedDateTime;

import static com.github.library.dodp.DodpNamespaces.DAISY_ONLINE;

/**
 * A single entry in a {@link ContentList}.
 */
public final class ContentItem {

    private final String id;
    private final ZonedDateTime lastModifiedDate;
    private final Label label;

    public ContentItem(String id, ZonedDateTime lastModifiedDate, Label label) {
        this.id = id;
        this.lastModifiedDate = lastModifiedDate;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public ZonedDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public Label getLabel() {
        return label;
    }

    public static ContentItem parse(Element element) {
        String id = XmlUtil.attribute(element, "id");
        ZonedDateTime lastModified = DateTimes.parse(XmlUtil.attribute(element, "lastModifiedDate"));
        Element labelElement = XmlUtil.firstChildElement(element, DAISY_ONLINE, "label");
        Label label = labelElement == null ? null : Label.parse(labelElement);
        return new ContentItem(id, lastModified, label);
    }
}
