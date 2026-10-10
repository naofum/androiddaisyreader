package com.github.library.dodp.model;

import com.github.library.dodp.util.XmlUtil;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.library.dodp.DodpNamespaces.DAISY_ONLINE;

/**
 * Service announcements returned by {@code getServiceAnnouncements}.
 */
public final class Announcements {

    private final List<Announcement> announcements;

    public Announcements(List<Announcement> announcements) {
        this.announcements = announcements == null ? Collections.emptyList() : announcements;
    }

    public List<Announcement> getAnnouncements() {
        return announcements;
    }

    public static Announcements parse(Element element) {
        List<Announcement> result = new ArrayList<>();
        for (Element announcementElement : XmlUtil.childElements(element, DAISY_ONLINE, "announcement")) {
            result.add(Announcement.parse(announcementElement));
        }
        return new Announcements(result);
    }

    public static final class Announcement {

        private final String id;
        private final AnnouncementType type;
        private final int priority;
        private final Label label;

        public Announcement(String id, AnnouncementType type, int priority, Label label) {
            this.id = id;
            this.type = type;
            this.priority = priority;
            this.label = label;
        }

        public String getId() {
            return id;
        }

        public AnnouncementType getType() {
            return type;
        }

        public int getPriority() {
            return priority;
        }

        public Label getLabel() {
            return label;
        }

        public static Announcement parse(Element element) {
            String id = XmlUtil.attribute(element, "id");
            AnnouncementType type = AnnouncementType.fromString(XmlUtil.attribute(element, "type"));
            int priority = XmlUtil.parseInt(XmlUtil.attribute(element, "priority")) == null
                    ? 3 : XmlUtil.parseInt(XmlUtil.attribute(element, "priority"));
            Element labelElement = XmlUtil.firstChildElement(element, DAISY_ONLINE, "label");
            Label label = labelElement == null ? null : Label.parse(labelElement);
            return new Announcement(id, type, priority, label);
        }
    }

    public enum AnnouncementType {
        WARNING, ERROR, INFORMATION, SYSTEM;

        public static AnnouncementType fromString(String value) {
            if (value == null) {
                return INFORMATION;
            }
            try {
                return valueOf(value.trim());
            } catch (IllegalArgumentException e) {
                return INFORMATION;
            }
        }
    }
}
