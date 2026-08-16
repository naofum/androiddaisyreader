package org.androiddaisyreader.machiiroconvert.converter;

import java.util.Locale;

/**
 * 広報誌の1ページ分の画像データ。
 * htmlUrlで取得したHTMLに埋め込まれた data:image のバイナリデータを保持する。
 */
public class PageImage {

    private final int pageNo;
    private final byte[] data;
    private final String extension;

    /**
     * @param pageNo    ページ番号
     * @param data      画像のバイナリデータ
     * @param extension 拡張子（例: "png", "jpg"）
     */
    public PageImage(int pageNo, byte[] data, String extension) {
        this.pageNo = pageNo;
        this.data = data;
        this.extension = extension;
    }

    public int getPageNo() {
        return pageNo;
    }

    public byte[] getData() {
        return data;
    }

    public String getExtension() {
        return extension;
    }

    /**
     * EPUB内での画像リソースのhref（例: images/page_0001.png）。
     */
    public String getHref() {
        return String.format(Locale.ROOT, "images/page_%04d.%s", pageNo, extension);
    }

    @Override
    public String toString() {
        return "PageImage{pageNo=" + pageNo + ", extension='" + extension + "'}";
    }
}
