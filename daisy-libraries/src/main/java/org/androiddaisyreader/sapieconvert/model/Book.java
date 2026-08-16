package org.androiddaisyreader.sapieconvert.model;

/**
 * サピエ図書館の検索結果の1件分（図書）を表すデータクラス。
 * 検索結果一覧テーブルの1行に対応する。
 */
public class Book {

    private final String id;
    private final String title;
    private final String author;
    private final String type;
    private final String time;
    private final String publisher;
    private final String library;

    public Book(String id, String title, String author, String type, String time,
                String publisher, String library) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.type = type;
        this.time = time;
        this.publisher = publisher;
        this.library = library;
    }

    /** 図書ID（詳細・ダウンロードで使用）。 */
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    /** 資料種別（音声デイジー / テキストデイジー / マルチメディアデイジー）。 */
    public String getType() {
        return type;
    }

    /** 時間数。 */
    public String getTime() {
        return time;
    }

    /** 出版年。 */
    public String getPublisher() {
        return publisher;
    }

    /** 所蔵館。 */
    public String getLibrary() {
        return library;
    }

    @Override
    public String toString() {
        return "Book{id=" + id + ", title='" + title + "', author='" + author + "'}";
    }
}
