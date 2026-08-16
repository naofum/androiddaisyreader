package org.androiddaisyreader.kohoconvert.model;

/**
 * 音声版広報ページの情報を表すデータクラス。
 * トップページ内でリンクされている音声版広報のページに対応する。
 */
public class VoicePage {

    private final String url;
    private final String title;
    private final String pageTitle;
    private final Integer year;
    private final Integer month;

    /**
     * VoicePageを構築する。リンク先ページのタイトルはnullになる。
     *
     * @param url   音声版広報ページの絶対URL
     * @param title リンクテキスト（例: "音声版"、空の場合もある）
     * @param year  URLから抽出した年（/YYYY/MM/ 形式。取得できない場合はnull）
     * @param month URLから抽出した月（/YYYY/MM/ 形式。取得できない場合はnull）
     */
    public VoicePage(String url, String title, Integer year, Integer month) {
        this(url, title, null, year, month);
    }

    /**
     * VoicePageを構築する。
     *
     * @param url       音声版広報ページの絶対URL
     * @param title     リンクテキスト（例: "音声版"、空の場合もある）
     * @param pageTitle リンク先ページの&lt;title&gt;（取得できない場合はnull）
     * @param year      URLから抽出した年（/YYYY/MM/ 形式。取得できない場合はnull）
     * @param month     URLから抽出した月（/YYYY/MM/ 形式。取得できない場合はnull）
     */
    public VoicePage(String url, String title, String pageTitle, Integer year, Integer month) {
        this.url = url;
        this.title = title;
        this.pageTitle = pageTitle;
        this.year = year;
        this.month = month;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    /**
     * リンク先ページのタイトル（&lt;title&gt;）を返す。取得できていない場合はnull。
     */
    public String getPageTitle() {
        return pageTitle;
    }

    public Integer getYear() {
        return year;
    }

    public Integer getMonth() {
        return month;
    }

    /**
     * リンク先ページのタイトルを設定した新しいVoicePageを返す。
     *
     * @param pageTitle リンク先ページのタイトル
     * @return タイトル付きの新しいVoicePage
     */
    public VoicePage withPageTitle(String pageTitle) {
        return new VoicePage(url, title, pageTitle, year, month);
    }

    @Override
    public String toString() {
        return "VoicePage{url='" + url + "', title='" + title
                + "', pageTitle='" + pageTitle + "', year=" + year + ", month=" + month + "}";
    }
}
