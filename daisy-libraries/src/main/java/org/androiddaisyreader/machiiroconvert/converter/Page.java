package org.androiddaisyreader.machiiroconvert.converter;

import java.util.List;

/**
 * EPUBに変換する広報誌の1ページ分を表す。
 * ページ画像と、そのページに属するテキストブロックを保持する。
 */
public class Page {

    private final int pageNo;
    private final String title;
    private final List<Integer> ids;
    private final List<String> texts;
    private final String imageHref;
    private final byte[] imageData;

    /**
     * @param pageNo    ページ番号
     * @param title     ページの見出し（目次用）
     * @param ids       id一覧（上から下・左から右の順）
     * @param texts     テキストブロック一覧（上から下・左から右の順）
     * @param imageHref ページ画像のhref（画像が無い場合はnull）
     * @param imageData ページ画像のバイナリ（画像が無い場合はnull）
     */
    public Page(int pageNo, String title, List<Integer> ids, List<String> texts, String imageHref, byte[] imageData) {
        this.pageNo = pageNo;
        this.title = title;
        this.ids = ids;
        this.texts = texts;
        this.imageHref = imageHref;
        this.imageData = imageData;
    }

    public int getPageNo() {
        return pageNo;
    }

    public String getTitle() {
        return title;
    }

    public List<Integer> getIds() {
        return ids;
    }

    public List<String> getTexts() {
        return texts;
    }

    public String getImageHref() {
        return imageHref;
    }

    public byte[] getImageData() {
        return imageData;
    }
}
