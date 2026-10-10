package com.github.library.dodp.model;

import com.github.library.dodp.util.XmlUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.library.dodp.DodpNamespaces.BOOKMARK;

/**
 * Z39.86-2005 Portable Bookmarks set, used by {@code setBookmarks} and
 * {@code getBookmarks} for reading-position synchronization. Carries the book
 * title/uid, the last reading position ({@code lastMark}) and zero or more
 * bookmarks and highlights.
 */
public final class BookmarkSet {

    private final String title;
    private final String uid;
    private final Bookmark lastMark;
    private final List<Bookmark> bookmarks;
    private final List<Hilite> hilites;

    public BookmarkSet(String title, String uid, Bookmark lastMark,
                       List<Bookmark> bookmarks, List<Hilite> hilites) {
        this.title = title;
        this.uid = uid;
        this.lastMark = lastMark;
        this.bookmarks = bookmarks == null ? Collections.emptyList() : bookmarks;
        this.hilites = hilites == null ? Collections.emptyList() : hilites;
    }

    public String getTitle() {
        return title;
    }

    public String getUid() {
        return uid;
    }

    public Bookmark getLastMark() {
        return lastMark;
    }

    public List<Bookmark> getBookmarks() {
        return bookmarks;
    }

    public List<Hilite> getHilites() {
        return hilites;
    }

    public static BookmarkSet parse(Element element) {
        String title = null;
        Element titleElement = XmlUtil.firstChildElement(element, BOOKMARK, "title");
        if (titleElement != null) {
            title = XmlUtil.childText(titleElement, BOOKMARK, "text");
        }
        String uid = XmlUtil.childText(element, BOOKMARK, "uid");

        Bookmark lastMark = null;
        Element lastMarkElement = XmlUtil.firstChildElement(element, BOOKMARK, "lastmark");
        if (lastMarkElement != null) {
            lastMark = Bookmark.parseContent(lastMarkElement);
        }

        List<Bookmark> bookmarks = new ArrayList<>();
        List<Hilite> hilites = new ArrayList<>();
        for (Element child : XmlUtil.childElements(element)) {
            String localName = child.getLocalName();
            if ("bookmark".equals(localName)) {
                bookmarks.add(Bookmark.parse(child));
            } else if ("hilite".equals(localName)) {
                hilites.add(Hilite.parse(child));
            }
        }
        return new BookmarkSet(title, uid, lastMark, bookmarks, hilites);
    }

    public Element toElement(Document document) {
        Element root = document.createElementNS(BOOKMARK, "bookmarkSet");
        Element titleElement = XmlUtil.appendChild(root, BOOKMARK, "title");
        XmlUtil.appendChildText(titleElement, BOOKMARK, "text", title);
        XmlUtil.appendChildText(root, BOOKMARK, "uid", uid);
        if (lastMark != null) {
            lastMark.appendContentTo(XmlUtil.appendChild(root, BOOKMARK, "lastmark"));
        }
        for (Bookmark bookmark : bookmarks) {
            bookmark.appendTo(root);
        }
        for (Hilite hilite : hilites) {
            hilite.appendTo(root);
        }
        return root;
    }

    public static Builder builder(String uid) {
        return new Builder(uid);
    }

    public static final class Builder {

        private final String uid;
        private String title;
        private Bookmark lastMark;
        private final List<Bookmark> bookmarks = new ArrayList<>();
        private final List<Hilite> hilites = new ArrayList<>();

        private Builder(String uid) {
            this.uid = uid;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder lastMark(Bookmark lastMark) {
            this.lastMark = lastMark;
            return this;
        }

        public Builder addBookmark(Bookmark bookmark) {
            bookmarks.add(bookmark);
            return this;
        }

        public Builder addHilite(Hilite hilite) {
            hilites.add(hilite);
            return this;
        }

        public BookmarkSet build() {
            return new BookmarkSet(title, uid, lastMark, bookmarks, hilites);
        }
    }

    /**
     * A reading position: a pointer into the SMIL/NCX content plus a time or
     * character offset.
     */
    public static final class Location {

        private final String ncxRef;
        private final String uri;
        private final String timeOffset;
        private final Long charOffset;

        public Location(String ncxRef, String uri, String timeOffset, Long charOffset) {
            this.ncxRef = ncxRef;
            this.uri = uri;
            this.timeOffset = timeOffset;
            this.charOffset = charOffset;
        }

        public String getNcxRef() {
            return ncxRef;
        }

        public String getUri() {
            return uri;
        }

        public String getTimeOffset() {
            return timeOffset;
        }

        public Long getCharOffset() {
            return charOffset;
        }

        static Location parse(Element element) {
            String ncxRef = XmlUtil.childText(element, BOOKMARK, "ncxRef");
            String uri = XmlUtil.childText(element, BOOKMARK, "URI");
            String timeOffset = XmlUtil.childText(element, BOOKMARK, "timeOffset");
            Long charOffset = XmlUtil.parseLong(XmlUtil.childText(element, BOOKMARK, "charOffset"));
            return new Location(ncxRef, uri, timeOffset, charOffset);
        }

        void appendTo(Element parent) {
            XmlUtil.appendChildText(parent, BOOKMARK, "ncxRef", ncxRef);
            XmlUtil.appendChildText(parent, BOOKMARK, "URI", uri);
            if (timeOffset != null) {
                XmlUtil.appendChildText(parent, BOOKMARK, "timeOffset", timeOffset);
            }
            if (charOffset != null) {
                XmlUtil.appendChildText(parent, BOOKMARK, "charOffset", String.valueOf(charOffset));
            }
        }
    }

    /**
     * A single bookmark: a location plus an optional note.
     */
    public static final class Bookmark {

        private final Location location;
        private final Note note;
        private final String label;
        private final String lang;

        public Bookmark(Location location, Note note, String label, String lang) {
            this.location = location;
            this.note = note;
            this.label = label;
            this.lang = lang;
        }

        public Location getLocation() {
            return location;
        }

        public Note getNote() {
            return note;
        }

        public String getLabel() {
            return label;
        }

        public String getLang() {
            return lang;
        }

        public static Bookmark parse(Element element) {
            Location location = Location.parse(element);
            Element noteElement = XmlUtil.firstChildElement(element, BOOKMARK, "note");
            Note note = noteElement == null ? null : Note.parse(noteElement);
            String label = XmlUtil.attribute(element, "label");
            String lang = element.getAttributeNS(Label.XML_NAMESPACE, "lang");
            return new Bookmark(location, note, label, lang);
        }

        static Bookmark parseContent(Element element) {
            Location location = Location.parse(element);
            Element noteElement = XmlUtil.firstChildElement(element, BOOKMARK, "note");
            Note note = noteElement == null ? null : Note.parse(noteElement);
            return new Bookmark(location, note, null, null);
        }

        void appendContentTo(Element parent) {
            location.appendTo(parent);
        }

        void appendTo(Element parent) {
            Element bookmark = parent.getOwnerDocument().createElementNS(BOOKMARK, "bookmark");
            if (label != null) {
                bookmark.setAttribute("label", label);
            }
            if (lang != null) {
                bookmark.setAttributeNS(Label.XML_NAMESPACE, "xml:lang", lang);
            }
            location.appendTo(bookmark);
            if (note != null) {
                note.appendTo(bookmark);
            }
            parent.appendChild(bookmark);
        }
    }

    /**
     * A highlighted block of text.
     */
    public static final class Hilite {

        private final Location hiliteStart;
        private final Location hiliteEnd;
        private final Note note;
        private final String label;

        public Hilite(Location hiliteStart, Location hiliteEnd, Note note, String label) {
            this.hiliteStart = hiliteStart;
            this.hiliteEnd = hiliteEnd;
            this.note = note;
            this.label = label;
        }

        public Location getHiliteStart() {
            return hiliteStart;
        }

        public Location getHiliteEnd() {
            return hiliteEnd;
        }

        public Note getNote() {
            return note;
        }

        public String getLabel() {
            return label;
        }

        public static Hilite parse(Element element) {
            Location start = null;
            Element startElement = XmlUtil.firstChildElement(element, BOOKMARK, "hiliteStart");
            if (startElement != null) {
                start = Location.parse(startElement);
            }
            Location end = null;
            Element endElement = XmlUtil.firstChildElement(element, BOOKMARK, "hiliteEnd");
            if (endElement != null) {
                end = Location.parse(endElement);
            }
            Element noteElement = XmlUtil.firstChildElement(element, BOOKMARK, "note");
            Note note = noteElement == null ? null : Note.parse(noteElement);
            String label = XmlUtil.attribute(element, "label");
            return new Hilite(start, end, note, label);
        }

        void appendTo(Element parent) {
            Element hilite = parent.getOwnerDocument().createElementNS(BOOKMARK, "hilite");
            if (label != null) {
                hilite.setAttribute("label", label);
            }
            Element startElement = XmlUtil.appendChild(hilite, BOOKMARK, "hiliteStart");
            if (hiliteStart != null) {
                hiliteStart.appendTo(startElement);
            }
            Element endElement = XmlUtil.appendChild(hilite, BOOKMARK, "hiliteEnd");
            if (hiliteEnd != null) {
                hiliteEnd.appendTo(endElement);
            }
            if (note != null) {
                note.appendTo(hilite);
            }
            parent.appendChild(hilite);
        }
    }

    /**
     * A text and/or audio note attached to a bookmark or highlight.
     */
    public static final class Note {

        private final String text;
        private final Audio audio;

        public Note(String text, Audio audio) {
            this.text = text;
            this.audio = audio;
        }

        public String getText() {
            return text;
        }

        public Audio getAudio() {
            return audio;
        }

        public static Note parse(Element element) {
            String text = XmlUtil.childText(element, BOOKMARK, "text");
            Element audioElement = XmlUtil.firstChildElement(element, BOOKMARK, "audio");
            Audio audio = audioElement == null ? null : Audio.parse(audioElement);
            return new Note(text, audio);
        }

        void appendTo(Element parent) {
            Element note = parent.getOwnerDocument().createElementNS(BOOKMARK, "note");
            if (text != null) {
                XmlUtil.appendChildText(note, BOOKMARK, "text", text);
            }
            if (audio != null) {
                audio.appendTo(note);
            }
            parent.appendChild(note);
        }
    }

    /**
     * An audio clip of a user-recorded note.
     */
    public static final class Audio {

        private final String src;
        private final String clipBegin;
        private final String clipEnd;

        public Audio(String src, String clipBegin, String clipEnd) {
            this.src = src;
            this.clipBegin = clipBegin;
            this.clipEnd = clipEnd;
        }

        public String getSrc() {
            return src;
        }

        public String getClipBegin() {
            return clipBegin;
        }

        public String getClipEnd() {
            return clipEnd;
        }

        public static Audio parse(Element element) {
            return new Audio(XmlUtil.attribute(element, "src"),
                    XmlUtil.attribute(element, "clipBegin"),
                    XmlUtil.attribute(element, "clipEnd"));
        }

        void appendTo(Element parent) {
            Element audio = parent.getOwnerDocument().createElementNS(BOOKMARK, "audio");
            if (src != null) {
                audio.setAttribute("src", src);
            }
            if (clipBegin != null) {
                audio.setAttribute("clipBegin", clipBegin);
            }
            if (clipEnd != null) {
                audio.setAttribute("clipEnd", clipEnd);
            }
            parent.appendChild(audio);
        }
    }
}
