package org.androiddaisyreader.sapieconvert.model;

import java.util.ArrayList;
import java.util.List;

/**
 * サピエ図書館の検索結果を表すデータクラス。
 * ページングに必要な情報（該当件数・次ページ有無・セッショントークン）を保持する。
 */
public class SearchResult {

    private final List<Book> books;
    private final int totalCount;
    private final int page;
    private final boolean hasNext;
    private final String sessionToken;
    private final String rtnme;

    public SearchResult(List<Book> books, int totalCount, int page, boolean hasNext,
                        String sessionToken, String rtnme) {
        this.books = books != null ? books : new ArrayList<>();
        this.totalCount = totalCount;
        this.page = page;
        this.hasNext = hasNext;
        this.sessionToken = sessionToken;
        this.rtnme = rtnme;
    }

    public List<Book> getBooks() {
        return books;
    }

    /** 該当件数。 */
    public int getTotalCount() {
        return totalCount;
    }

    /** 現在ページ（1始まり）。 */
    public int getPage() {
        return page;
    }

    /** 次ページが存在するか。 */
    public boolean isHasNext() {
        return hasNext;
    }

    /** 検索セッショントークン（S00221）。次ページ取得時に使用する。 */
    public String getSessionToken() {
        return sessionToken;
    }

    /** RTNTME トークン。後続リクエストで使用する。 */
    public String getRtnme() {
        return rtnme;
    }
}
