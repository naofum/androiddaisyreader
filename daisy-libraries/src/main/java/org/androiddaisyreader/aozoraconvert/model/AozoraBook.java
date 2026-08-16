package org.androiddaisyreader.aozoraconvert.model;

/**
 * 青空文庫の図書情報を表すデータクラス。
 * カタログCSVの各行に対応する。
 */
public class AozoraBook {

    private final int authorId;
    private final String authorName;
    private final int workId;
    private final String workName;
    private final String kanaType;      // 仮名遣い種別
    private final String status;        // 状態
    private final String statusDate;    // 状態の開始日
    private final String publisher;     // 出版社名

    public AozoraBook(int authorId, String authorName, int workId, String workName,
                      String kanaType, String status, String statusDate, String publisher) {
        this.authorId = authorId;
        this.authorName = authorName;
        this.workId = workId;
        this.workName = workName;
        this.kanaType = kanaType;
        this.status = status;
        this.statusDate = statusDate;
        this.publisher = publisher;
    }

    public int getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public int getWorkId() {
        return workId;
    }

    public String getWorkName() {
        return workName;
    }

    public String getKanaType() {
        return kanaType;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusDate() {
        return statusDate;
    }

    public String getPublisher() {
        return publisher;
    }

    /**
     * DBの_idカラムに使用するユニークキーを返す。
     */
    public String getUniqueId() {
        return authorId + "_" + workId;
    }

    /**
     * ダウンロード前のプレースホルダパスを返す。
     */
    public String getPlaceholderPath() {
        return "aozora://" + authorId + "/" + workId;
    }

    @Override
    public String toString() {
        return "AozoraBook{authorId=" + authorId + ", workId=" + workId
                + ", workName='" + workName + "', authorName='" + authorName + "'}";
    }
}
