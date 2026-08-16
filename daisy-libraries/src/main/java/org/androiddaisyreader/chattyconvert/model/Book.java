package org.androiddaisyreader.chattyconvert.model;

/**
 * 検索結果の図書情報を表すデータクラス。
 * CSVの各行に対応する。
 */
public class Book {

    private final int id;
    private final String title;
    private final String author;
    private final String layout;
    private final String registeredDate;

    public Book(int id, String title, String author, String layout, String registeredDate) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.layout = layout;
        this.registeredDate = registeredDate;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getLayout() {
        return layout;
    }

    public String getRegisteredDate() {
        return registeredDate;
    }

    @Override
    public String toString() {
        return "Book{id=" + id + ", title='" + title + "', author='" + author + "'}";
    }
}
