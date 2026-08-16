package org.androiddaisyreader.machiiroconvert.model;

import java.util.List;

/**
 * マチイロの広報誌（1号分）のデータモデル。
 * テキストデータAPIのJSONレスポンスから構築される。
 */
public class Magazine {

    private final String issueId;
    private final String title;
    private final String magazineTitle;
    private final List<MagazineDocument> documents;
    private final String htmlUrl;
    private final String pdfUrl;
    private final int pages;

    /**
     * @param issueId       発行ID
     * @param title         号タイトル（例: "令和8年8月11日号"）
     * @param magazineTitle 誌名（例: "なかの区報"）
     * @param documents     テキストブロック一覧（ページ順・位置順にソート済み）
     * @param htmlUrl       ページ画像版のHTML URL
     * @param pdfUrl        PDF URL
     * @param pages         総ページ数
     */
    public Magazine(String issueId, String title, String magazineTitle,
                    List<MagazineDocument> documents, String htmlUrl, String pdfUrl, int pages) {
        this.issueId = issueId;
        this.title = title;
        this.magazineTitle = magazineTitle;
        this.documents = documents;
        this.htmlUrl = htmlUrl;
        this.pdfUrl = pdfUrl;
        this.pages = pages;
    }

    public String getIssueId() {
        return issueId;
    }

    public String getTitle() {
        return title;
    }

    public String getMagazineTitle() {
        return magazineTitle;
    }

    public List<MagazineDocument> getDocuments() {
        return documents;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public int getPages() {
        return pages;
    }

    @Override
    public String toString() {
        return "Magazine{issueId='" + issueId + "', magazineTitle='" + magazineTitle
                + "', title='" + title + "', pages=" + pages + "}";
    }
}
