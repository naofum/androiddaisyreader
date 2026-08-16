package org.androiddaisyreader.machiiroconvert.model;

/**
 * マチイロに掲載されている自治体を表すデータクラス。
 * 自治体一覧ページの &lt;a href&gt; から抽出した情報を保持する。
 */
public class Municipality {

    private final String id;
    private final String name;
    private final String url;

    /**
     * @param id   自治体ID（/lp/{自治体ID} の ID 部分）
     * @param name 自治体名（アンカーテキスト）
     * @param url  自治体のランディングページURL（https://machiiro.town/lp/{自治体ID}）
     */
    public Municipality(String id, String name, String url) {
        this.id = id;
        this.name = name;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return "Municipality{id='" + id + "', name='" + name + "'}";
    }
}
