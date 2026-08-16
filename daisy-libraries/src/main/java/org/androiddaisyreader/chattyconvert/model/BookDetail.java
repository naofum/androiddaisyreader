package org.androiddaisyreader.chattyconvert.model;

/**
 * 図書の詳細情報を表すデータクラス。
 * 図書詳細ページのHTMLから抽出した情報を保持する。
 */
public class BookDetail {

    private final int bookId;
    private final String author;
    private final String publisher;
    private final String description;
    private final String pages;
    private final String copyright;
    private final String isbn;
    private final String issueDate;
    private final String producer;
    private final boolean inMyBox;
    private final boolean readable;
    private final String token;

    private BookDetail(Builder builder) {
        this.bookId = builder.bookId;
        this.author = builder.author;
        this.publisher = builder.publisher;
        this.description = builder.description;
        this.pages = builder.pages;
        this.copyright = builder.copyright;
        this.isbn = builder.isbn;
        this.issueDate = builder.issueDate;
        this.producer = builder.producer;
        this.inMyBox = builder.inMyBox;
        this.readable = builder.readable;
        this.token = builder.token;
    }

    public int getBookId() {
        return bookId;
    }

    public String getAuthor() {
        return author;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getDescription() {
        return description;
    }

    public String getPages() {
        return pages;
    }

    public String getCopyright() {
        return copyright;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getIssueDate() {
        return issueDate;
    }

    public String getProducer() {
        return producer;
    }

    public boolean isInMyBox() {
        return inMyBox;
    }

    /**
     * この図書を読む権限があるかどうか。
     * 詳細画面に「MY本箱に入れる」ボタンまたは既にMY本箱に入っている場合は true。
     */
    public boolean isReadable() {
        return readable;
    }

    /**
     * MY本箱に入れる際に使用するCSRFトークン。
     * inMyBox=false の場合のみ有効。
     */
    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return "BookDetail{bookId=" + bookId + ", author='" + author + "', publisher='" + publisher + "', inMyBox=" + inMyBox + "}";
    }

    public static class Builder {
        private int bookId;
        private String author;
        private String publisher;
        private String description;
        private String pages;
        private String copyright;
        private String isbn;
        private String issueDate;
        private String producer;
        private boolean inMyBox;
        private boolean readable;
        private String token;

        public Builder bookId(int bookId) {
            this.bookId = bookId;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder publisher(String publisher) {
            this.publisher = publisher;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder pages(String pages) {
            this.pages = pages;
            return this;
        }

        public Builder copyright(String copyright) {
            this.copyright = copyright;
            return this;
        }

        public Builder isbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public Builder issueDate(String issueDate) {
            this.issueDate = issueDate;
            return this;
        }

        public Builder producer(String producer) {
            this.producer = producer;
            return this;
        }

        public Builder inMyBox(boolean inMyBox) {
            this.inMyBox = inMyBox;
            return this;
        }

        public Builder readable(boolean readable) {
            this.readable = readable;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public BookDetail build() {
            return new BookDetail(this);
        }
    }
}
