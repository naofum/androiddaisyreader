package com.github.library.dodp.model;

import com.github.library.dodp.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static com.github.library.dodp.DodpNamespaces.DAISY_ONLINE;

/**
 * A localized text label, optionally with an associated audio clip.
 */
public final class Label {

    public static final String XML_NAMESPACE = "http://www.w3.org/XML/1998/namespace";

    private final String language;
    private final String direction;
    private final String text;
    private final Audio audio;

    public Label(String language, String direction, String text, Audio audio) {
        this.language = language;
        this.direction = direction;
        this.text = text;
        this.audio = audio;
    }

    /** xml:lang value. */
    public String getLanguage() {
        return language;
    }

    /** dir attribute: {@code ltr} or {@code rtl}, may be null. */
    public String getDirection() {
        return direction;
    }

    public String getText() {
        return text;
    }

    public Audio getAudio() {
        return audio;
    }

    public static Label parse(Element element) {
        String language = element.getAttributeNS(XML_NAMESPACE, "lang");
        String direction = XmlUtil.attribute(element, "dir");
        String text = XmlUtil.childText(element, DAISY_ONLINE, "text");
        Element audioElement = XmlUtil.firstChildElement(element, DAISY_ONLINE, "audio");
        Audio audio = audioElement == null ? null : Audio.parse(audioElement);
        return new Label(language, direction, text, audio);
    }

    public Element toElement(Document document) {
        Element label = document.createElementNS(DAISY_ONLINE, "label");
        if (language != null) {
            label.setAttributeNS(XML_NAMESPACE, "xml:lang", language);
        }
        if (direction != null) {
            label.setAttribute("dir", direction);
        }
        XmlUtil.appendChildText(label, DAISY_ONLINE, "text", text);
        if (audio != null) {
            audio.appendTo(label);
        }
        return label;
    }

    /**
     * An audio clip associated with a label.
     */
    public static final class Audio {

        private final String uri;
        private final Long rangeBegin;
        private final Long rangeEnd;
        private final Long size;

        public Audio(String uri, Long rangeBegin, Long rangeEnd, Long size) {
            this.uri = uri;
            this.rangeBegin = rangeBegin;
            this.rangeEnd = rangeEnd;
            this.size = size;
        }

        public String getUri() {
            return uri;
        }

        public Long getRangeBegin() {
            return rangeBegin;
        }

        public Long getRangeEnd() {
            return rangeEnd;
        }

        public Long getSize() {
            return size;
        }

        public static Audio parse(Element element) {
            return new Audio(
                    XmlUtil.attribute(element, "uri"),
                    XmlUtil.parseLong(XmlUtil.attribute(element, "rangeBegin")),
                    XmlUtil.parseLong(XmlUtil.attribute(element, "rangeEnd")),
                    XmlUtil.parseLong(XmlUtil.attribute(element, "size")));
        }

        public void appendTo(Element parent) {
            Element audio = parent.getOwnerDocument().createElementNS(DAISY_ONLINE, "audio");
            if (uri != null) {
                audio.setAttribute("uri", uri);
            }
            if (rangeBegin != null) {
                audio.setAttribute("rangeBegin", String.valueOf(rangeBegin));
            }
            if (rangeEnd != null) {
                audio.setAttribute("rangeEnd", String.valueOf(rangeEnd));
            }
            if (size != null) {
                audio.setAttribute("size", String.valueOf(size));
            }
            parent.appendChild(audio);
        }
    }
}
