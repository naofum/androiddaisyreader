package org.androiddaisyreader.paging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingSource;
import androidx.paging.PagingState;

import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;

import java.util.List;

import kotlin.coroutines.Continuation;

/**
 * SQLite から DaisyBookInfo をページ単位で読み込む PagingSource。
 * Paging 3 ライブラリのデータソースとして機能する。
 */
public class DaisyBookPagingSource extends PagingSource<Integer, DaisyBookInfo> {

    private final SQLiteDaisyBookHelper sqlHelper;
    private final String type;
    private final String searchQuery;

    /**
     * @param sqlHelper SQLiteヘルパー
     * @param type      メタデータ種別（TYPE_DOWNLOAD_BOOK等）
     * @param searchQuery 検索クエリ（null または空文字列で全件取得）
     */
    public DaisyBookPagingSource(SQLiteDaisyBookHelper sqlHelper, String type, String searchQuery) {
        this.sqlHelper = sqlHelper;
        this.type = type;
        this.searchQuery = searchQuery;
    }

    @Nullable
    @Override
    public Object load(@NonNull LoadParams<Integer> params, @NonNull Continuation<? super LoadResult<Integer, DaisyBookInfo>> continuation) {
        int page = (params.getKey() != null) ? params.getKey() : 0;
        int pageSize = params.getLoadSize();
        int offset = page * pageSize;

        try {
            List<DaisyBookInfo> books = sqlHelper.getDaisyBookPage(type, searchQuery, pageSize, offset);

            Integer prevKey = (page > 0) ? page - 1 : null;
            Integer nextKey = (books.size() < pageSize) ? null : page + 1;

            return new LoadResult.Page<>(books, prevKey, nextKey);
        } catch (Exception e) {
            return new LoadResult.Error<>(e);
        }
    }

    @Nullable
    @Override
    public Integer getRefreshKey(@NonNull PagingState<Integer, DaisyBookInfo> state) {
        Integer anchorPosition = state.getAnchorPosition();
        if (anchorPosition == null) return null;
        LoadResult.Page<Integer, DaisyBookInfo> closestPage =
                state.closestPageToPosition(anchorPosition);
        if (closestPage == null) return null;
        Integer prevKey = closestPage.getPrevKey();
        Integer nextKey = closestPage.getNextKey();
        if (prevKey != null) return prevKey + 1;
        if (nextKey != null) return nextKey - 1;
        return null;
    }

    @Override
    public boolean getJumpingSupported() {
        return true;
    }
}
