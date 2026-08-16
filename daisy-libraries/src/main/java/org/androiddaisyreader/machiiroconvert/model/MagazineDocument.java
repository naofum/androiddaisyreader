package org.androiddaisyreader.machiiroconvert.model;

/**
 * 広報誌の1つのテキストブロックを表すデータクラス。
 * テキストデータAPIのJSONにある documents 配列の1要素に対応する。
 */
public class MagazineDocument {

    private final int pageNo;
    private final double positionYfrom;
    private final double positionXfrom;
    private final int id;
    private final String documentText;

    /**
     * @param pageNo        ページ番号
     * @param positionYfrom ブロックの縦位置（上端）
     * @param positionXfrom ブロックの横位置（左端）
     * @param id            id
     * @param documentText  テキスト内容
     */
    public MagazineDocument(int pageNo, double positionYfrom, double positionXfrom,
                            int id, String documentText) {
        this.pageNo = pageNo;
        this.positionYfrom = positionYfrom;
        this.positionXfrom = positionXfrom;
        this.id = id;
        this.documentText = documentText;
    }

    public int getPageNo() {
        return pageNo;
    }

    public double getPositionYfrom() {
        return positionYfrom;
    }

    public double getPositionXfrom() {
        return positionXfrom;
    }

    public int getId() {
        return id;
    }
    public String getDocumentText() {
        return documentText;
    }

    @Override
    public String toString() {
        return "MagazineDocument{pageNo=" + pageNo
                + ", positionYfrom=" + positionYfrom
                + ", positionXfrom=" + positionXfrom
                + ", id=" + String.valueOf(id)
                + ", documentText='" + documentText + "'}";
    }
}
