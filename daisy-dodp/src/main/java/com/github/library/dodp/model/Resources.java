package com.github.library.dodp.model;

import com.github.library.dodp.util.DateTimes;
import com.github.library.dodp.util.XmlUtil;
import org.w3c.dom.Element;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static com.github.library.dodp.DodpNamespaces.DAISY_ONLINE;

/**
 * The collection of downloadable resources for a content item, returned by
 * {@code getContentResources}. The {@code returnBy} attribute is the deadline
 * by which the content must be returned for lending-model content.
 */
public final class Resources {

    private final ZonedDateTime returnBy;
    private final ZonedDateTime lastModifiedDate;
    private final List<Resource> resources;

    public Resources(ZonedDateTime returnBy, ZonedDateTime lastModifiedDate, List<Resource> resources) {
        this.returnBy = returnBy;
        this.lastModifiedDate = lastModifiedDate;
        this.resources = resources;
    }

    /** Deadline for returning the content, or null when not applicable. */
    public ZonedDateTime getReturnBy() {
        return returnBy;
    }

    public ZonedDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public static Resources parse(Element element) {
        ZonedDateTime returnBy = DateTimes.parse(XmlUtil.attribute(element, "returnBy"));
        ZonedDateTime lastModified = DateTimes.parse(XmlUtil.attribute(element, "lastModifiedDate"));

        List<Element> resourceElements = XmlUtil.childElements(element, DAISY_ONLINE, "resource");
        List<Resource> resources;
        if (resourceElements.isEmpty()) {
            resources = Collections.emptyList();
        } else {
            resources = new java.util.ArrayList<>(resourceElements.size());
            for (Element resourceElement : resourceElements) {
                resources.add(Resource.parse(resourceElement));
            }
        }
        return new Resources(returnBy, lastModified, resources);
    }
}
