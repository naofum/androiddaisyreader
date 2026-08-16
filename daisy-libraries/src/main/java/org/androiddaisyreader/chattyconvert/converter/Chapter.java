package org.androiddaisyreader.chattyconvert.converter;

import org.jsoup.nodes.Element;

/**
 * 変換対象の図書の1章（index.html内のsection要素）を表す。
 */
public class Chapter {

    private final int index;
    private final String id;
    private final String title;
    private final Element sectionElement;

    /**
     * @param index          章番号（1始まり）
     * @param id             section要素のid（例: section_1_section0001_xhtml）
     * @param title          章題
     * @param sectionElement section要素（本文コンテンツ）
     */
    public Chapter(int index, String id, String title, Element sectionElement) {
        this.index = index;
        this.id = id;
        this.title = title;
        this.sectionElement = sectionElement;
    }

    public int getIndex() {
        return index;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Element getSectionElement() {
        return sectionElement;
    }
}
