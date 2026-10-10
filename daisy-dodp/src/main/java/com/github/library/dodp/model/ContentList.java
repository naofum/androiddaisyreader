package com.github.library.dodp.model;

import com.github.library.dodp.util.XmlUtil;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

import static com.github.library.dodp.DodpNamespaces.DAISY_ONLINE;

/**
 * A list of content items returned by {@code getContentList}. The {@code id}
 * attribute identifies the list type, e.g. {@code new}, {@code issued} or
 * {@code expired}.
 */
public final class ContentList {

    public static final String LIST_NEW = "new";
    public static final String LIST_ISSUED = "issued";
    public static final String LIST_EXPIRED = "expired";

    private final String id;
    private final int totalItems;
    private final Integer firstItem;
    private final Integer lastItem;
    private final Label label;
    private final List<ContentItem> items;

    public ContentList(String id, int totalItems, Integer firstItem, Integer lastItem,
                       Label label, List<ContentItem> items) {
        this.id = id;
        this.totalItems = totalItems;
        this.firstItem = firstItem;
        this.lastItem = lastItem;
        this.label = label;
        this.items = items;
    }

    public String getId() {
        return id;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public Integer getFirstItem() {
        return firstItem;
    }

    public Integer getLastItem() {
        return lastItem;
    }

    public Label getLabel() {
        return label;
    }

    public List<ContentItem> getItems() {
        return items;
    }

    public static ContentList parse(Element element) {
        String id = XmlUtil.attribute(element, "id");
        int totalItems = XmlUtil.parseInt(XmlUtil.attribute(element, "totalItems")) == null
                ? 0 : XmlUtil.parseInt(XmlUtil.attribute(element, "totalItems"));
        Integer firstItem = XmlUtil.parseInt(XmlUtil.attribute(element, "firstItem"));
        Integer lastItem = XmlUtil.parseInt(XmlUtil.attribute(element, "lastItem"));

        Element labelElement = XmlUtil.firstChildElement(element, DAISY_ONLINE, "label");
        Label label = labelElement == null ? null : Label.parse(labelElement);

        List<Element> itemElements = XmlUtil.childElements(element, DAISY_ONLINE, "contentItem");
        List<ContentItem> items;
        if (itemElements.isEmpty()) {
            items = Collections.emptyList();
        } else {
            items = new java.util.ArrayList<>(itemElements.size());
            for (Element itemElement : itemElements) {
                items.add(ContentItem.parse(itemElement));
            }
        }
        return new ContentList(id, totalItems, firstItem, lastItem, label, items);
    }
}
