package org.androiddaisyreader.sqlite;

import java.util.ArrayList;
import java.util.List;

import org.androiddaisyreader.apps.PrivateException;
import org.androiddaisyreader.model.DaisyBookInfo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * This adapter to handle sqlite of recent book
 * 
 * @author LogiGear
 * @date 2013.03.05
 */

public class SQLiteRecentBookHelper extends SQLiteHandler {

    private static SQLiteRecentBookHelper sInstance;
    private Context mContext;

    public static synchronized SQLiteRecentBookHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SQLiteRecentBookHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * @deprecated getInstance(Context)を使用してください。
     */
    @Deprecated
    public SQLiteRecentBookHelper(Context context) {
        super(context);
        this.mContext = context;
    }

    /**
     * Add a record to RecentBooks table
     * 
     * @param recentBooks
     */
    public void addRecentBook(DaisyBookInfo recentBooks) {

        ContentValues mValue = new ContentValues();

        mValue.put(NAME_KEY_RECENT_BOOKS, recentBooks.getTitle());
        mValue.put(PATH_KEY_RECENT_BOOKS, recentBooks.getPath());
        mValue.put(SORT_KEY_RECENT_BOOKS, recentBooks.getSort());
        try {
            SQLiteDatabase mdb = getWritableDatabase();
            mdb.insert(TABLE_NAME_RECENT_BOOKS, null, mValue);
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        }

    }

    /**
     * Delete a record of RecentBooks table
     * 
     * @param recentBooks
     */
    public void deleteRecentBook(DaisyBookInfo recentBooks) {

        try {
            SQLiteDatabase mdb = getWritableDatabase();
            mdb.delete(TABLE_NAME_RECENT_BOOKS, NAME_KEY_RECENT_BOOKS + "=?",
                    new String[] { String.valueOf(recentBooks.getTitle()) });
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        }

    }

    /**
     * Update a record of RecentBooks table
     * 
     * @param recentBooks
     */
    public void updateRecentBook(DaisyBookInfo recentBooks) {

        ContentValues mValue = new ContentValues();
        mValue.put(SORT_KEY_RECENT_BOOKS, recentBooks.getSort());
        try {
            SQLiteDatabase mdb = getWritableDatabase();
            mdb.update(TABLE_NAME_RECENT_BOOKS, mValue, NAME_KEY_RECENT_BOOKS + "=?",
                    new String[] { recentBooks.getTitle() });
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        }

    }

    /**
     * Get info a recent book
     * 
     * @param name
     * @return
     */
    public DaisyBookInfo getInfoRecentBook(String name) {
        DaisyBookInfo mRecentBooks = null;
        SQLiteDatabase mdb = null;
        Cursor mCursor = null;
        try {
            mdb = getReadableDatabase();
            mCursor = mdb.query(TABLE_NAME_RECENT_BOOKS, new String[] {
                    NAME_KEY_RECENT_BOOKS, PATH_KEY_RECENT_BOOKS, SORT_KEY_RECENT_BOOKS },
                    NAME_KEY_RECENT_BOOKS + "=?", new String[] { name }, null, null, null);
            if (mCursor != null && mCursor.moveToFirst()) {
                String valueName = mCursor.getString(mCursor.getColumnIndex(NAME_KEY_RECENT_BOOKS));
                String path = mCursor.getString(mCursor.getColumnIndex(PATH_KEY_RECENT_BOOKS));
                int sort = Integer.valueOf(mCursor.getString(mCursor
                        .getColumnIndex(SORT_KEY_RECENT_BOOKS)));
                mRecentBooks = new DaisyBookInfo("", valueName, path, "author", "publisher",
                        "date", sort);
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        } finally {
            if (mCursor != null) mCursor.close();
        }
        return mRecentBooks;
    }

    /**
     * Get all recent books from sql lite
     * 
     * @return
     */
    public List<DaisyBookInfo> getAllRecentBooks() {
        ArrayList<DaisyBookInfo> arrRecentBooks = new ArrayList<DaisyBookInfo>();
        SQLiteDatabase mdb = null;
        Cursor mCursor = null;
        try {
            mdb = getReadableDatabase();
            String sql = "SELECT * FROM " + TABLE_NAME_RECENT_BOOKS + " ORDER BY "
                    + SORT_KEY_RECENT_BOOKS + " DESC";
            mCursor = mdb.rawQuery(sql, null);

            if (mCursor.moveToFirst()) {
                do {
                    String valueName = mCursor.getString(mCursor
                            .getColumnIndex(NAME_KEY_RECENT_BOOKS));
                    String path = mCursor.getString(mCursor.getColumnIndex(PATH_KEY_RECENT_BOOKS));
                    int sort = Integer.valueOf(mCursor.getString(mCursor
                            .getColumnIndex(SORT_KEY_RECENT_BOOKS)));
                    arrRecentBooks.add(new DaisyBookInfo("", valueName, path, "author",
                            "publisher", "date", sort));
                } while (mCursor.moveToNext());
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        } finally {
            if (mCursor != null) mCursor.close();
        }
        return arrRecentBooks;
    }

    /**
     * Check exists.
     * 
     * @param name
     * @return
     */
    public boolean isExists(String name) {
        boolean result = false;
        Cursor mCursor = null;
        try {
            SQLiteDatabase mdb = getReadableDatabase();
            mCursor = mdb.query(TABLE_NAME_RECENT_BOOKS, new String[] {
                    NAME_KEY_RECENT_BOOKS, PATH_KEY_RECENT_BOOKS, SORT_KEY_RECENT_BOOKS },
                    NAME_KEY_RECENT_BOOKS + "=?", new String[] { name }, null, null, null);
            result = mCursor.moveToFirst();
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        } finally {
            if (mCursor != null) mCursor.close();
        }
        return result;
    }
}
