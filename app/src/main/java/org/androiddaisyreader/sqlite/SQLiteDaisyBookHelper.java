package org.androiddaisyreader.sqlite;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.androiddaisyreader.apps.PrivateException;
import org.androiddaisyreader.model.DaisyBookInfo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class SQLiteDaisyBookHelper extends SQLiteHandler {
    private static SQLiteDaisyBookHelper sInstance;
    private Context mContext;

    public static synchronized SQLiteDaisyBookHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SQLiteDaisyBookHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * @deprecated getInstance(Context)を使用してください。
     */
    @Deprecated
    public SQLiteDaisyBookHelper(Context context) {
        super(context);
        this.mContext = context;
    }

    /**
     * Add a record to DaisyBook table
     * 
     * @param DaisyBookInfo
     */
    public boolean addDaisyBook(DaisyBookInfo daisyBook, String type) {

        boolean result = false;
        ContentValues mValue = new ContentValues();
        mValue.put(ID_KEY_DAISY_BOOK, UUID.randomUUID().toString());
        mValue.put(TITLE_KEY_DAISY_BOOK, daisyBook.getTitle());
        mValue.put(PATH_KEY_DAISY_BOOK, daisyBook.getPath());
        mValue.put(AUTHOR_KEY_DAISY_BOOK, daisyBook.getAuthor());
        mValue.put(PUBLISHER_KEY_DAISY_BOOK, daisyBook.getPublisher());
        mValue.put(DATE_DAISY_BOOK, daisyBook.getDate());
        mValue.put(TYPE_OF_METADATA_DAISY_BOOK, type);
        mValue.put(SORT_KEY_DAISY_BOOK, daisyBook.getSort());
        try {
            SQLiteDatabase mdb = getWritableDatabase();
            long i = mdb.insert(TABLE_NAME_DAISY_BOOK, null, mValue);
            if (i != -1) {
                result = true;
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        }
        return result;

    }

    /**
     * 同じタイトル・同じタイプの既存レコードをすべて削除してから追加する。
     * ダウンロード済み書籍を再ダウンロードした際に一覧へ重複登録されるのを防ぐ。
     *
     * @param daisyBook 追加する書籍
     * @param type      メタデータ種別
     */
    public boolean addOrReplaceDaisyBook(DaisyBookInfo daisyBook, String type) {
        if (daisyBook != null && daisyBook.getTitle() != null) {
            deleteDaisyBookByTitle(daisyBook.getTitle(), type);
        }
        return addDaisyBook(daisyBook, type);
    }

    /**
     * 指定タイトル・タイプのレコードをすべて削除する。
     */
    public boolean deleteDaisyBookByTitle(String title, String type) {
        boolean result = false;
        try {
            SQLiteDatabase mdb = getWritableDatabase();
            result = mdb.delete(TABLE_NAME_DAISY_BOOK,
                    TITLE_KEY_DAISY_BOOK + "=? AND " + TYPE_OF_METADATA_DAISY_BOOK + "=?",
                    new String[] { title, type }) > 0;
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        }
        return result;
    }

    /**
     * 複数レコードをトランザクション内でバッチ挿入する。
     * deleteAllDaisyBook + 全件挿入のパターンで使用する。
     *
     * @param books 挿入する書籍リスト
     * @param type  メタデータ種別
     */
    public void replaceAllDaisyBooks(List<DaisyBookInfo> books, String type) {
        SQLiteDatabase mdb = null;
        try {
            mdb = getWritableDatabase();
            mdb.beginTransaction();
            try {
                // 既存レコード削除
                mdb.delete(TABLE_NAME_DAISY_BOOK, TYPE_OF_METADATA_DAISY_BOOK + "=?",
                        new String[]{type});

                // バッチ挿入
                ContentValues mValue = new ContentValues();
                for (DaisyBookInfo daisyBook : books) {
                    mValue.clear();
                    mValue.put(ID_KEY_DAISY_BOOK, UUID.randomUUID().toString());
                    mValue.put(TITLE_KEY_DAISY_BOOK, daisyBook.getTitle());
                    mValue.put(PATH_KEY_DAISY_BOOK, daisyBook.getPath());
                    mValue.put(AUTHOR_KEY_DAISY_BOOK, daisyBook.getAuthor());
                    mValue.put(PUBLISHER_KEY_DAISY_BOOK, daisyBook.getPublisher());
                    mValue.put(DATE_DAISY_BOOK, daisyBook.getDate());
                    mValue.put(TYPE_OF_METADATA_DAISY_BOOK, type);
                    mValue.put(SORT_KEY_DAISY_BOOK, daisyBook.getSort());
                    mdb.insert(TABLE_NAME_DAISY_BOOK, null, mValue);
                }
                mdb.setTransactionSuccessful();
            } finally {
                mdb.endTransaction();
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        }
    }

    /**
     * 複数レコードをトランザクション内でバッチ挿入する（既存レコードは削除しない追記）。
     *
     * @param books 挿入する書籍リスト
     * @param type  メタデータ種別
     */
    public void appendAllDaisyBooks(List<DaisyBookInfo> books, String type) {
        SQLiteDatabase mdb = null;
        try {
            mdb = getWritableDatabase();
            mdb.beginTransaction();
            try {
                // バッチ挿入（既存レコードは削除しない）
                ContentValues mValue = new ContentValues();
                for (DaisyBookInfo daisyBook : books) {
                    mValue.clear();
                    mValue.put(ID_KEY_DAISY_BOOK, UUID.randomUUID().toString());
                    mValue.put(TITLE_KEY_DAISY_BOOK, daisyBook.getTitle());
                    mValue.put(PATH_KEY_DAISY_BOOK, daisyBook.getPath());
                    mValue.put(AUTHOR_KEY_DAISY_BOOK, daisyBook.getAuthor());
                    mValue.put(PUBLISHER_KEY_DAISY_BOOK, daisyBook.getPublisher());
                    mValue.put(DATE_DAISY_BOOK, daisyBook.getDate());
                    mValue.put(TYPE_OF_METADATA_DAISY_BOOK, type);
                    mValue.put(SORT_KEY_DAISY_BOOK, daisyBook.getSort());
                    mdb.insert(TABLE_NAME_DAISY_BOOK, null, mValue);
                }
                mdb.setTransactionSuccessful();
            } finally {
                mdb.endTransaction();
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        }
    }

    /**
     * Get all daisy book from sqlite
     * 
     * @return
     */
    public List<DaisyBookInfo> getAllDaisyBook(String type) {
        ArrayList<DaisyBookInfo> arrDaisyBook = new ArrayList<DaisyBookInfo>();
        SQLiteDatabase mdb = null;
        Cursor mCursor = null;
        try {
            mdb = getReadableDatabase();
            mCursor = mdb.query(TABLE_NAME_DAISY_BOOK, new String[] { ID_KEY_DAISY_BOOK,
                    TITLE_KEY_DAISY_BOOK, PATH_KEY_DAISY_BOOK, AUTHOR_KEY_DAISY_BOOK,
                    PUBLISHER_KEY_DAISY_BOOK, DATE_DAISY_BOOK, SORT_KEY_DAISY_BOOK },
                    TYPE_OF_METADATA_DAISY_BOOK + "=?", new String[] { type },
                    null, null, SORT_KEY_DAISY_BOOK);

            if (mCursor.moveToFirst()) {
                int idxId = mCursor.getColumnIndex(ID_KEY_DAISY_BOOK);
                int idxTitle = mCursor.getColumnIndex(TITLE_KEY_DAISY_BOOK);
                int idxPath = mCursor.getColumnIndex(PATH_KEY_DAISY_BOOK);
                int idxAuthor = mCursor.getColumnIndex(AUTHOR_KEY_DAISY_BOOK);
                int idxPublisher = mCursor.getColumnIndex(PUBLISHER_KEY_DAISY_BOOK);
                int idxDate = mCursor.getColumnIndex(DATE_DAISY_BOOK);
                int idxSort = mCursor.getColumnIndex(SORT_KEY_DAISY_BOOK);
                do {
                    String id = mCursor.getString(idxId);
                    String title = mCursor.getString(idxTitle);
                    String path = mCursor.getString(idxPath);
                    String author = mCursor.getString(idxAuthor);
                    String publisher = mCursor.getString(idxPublisher);
                    String date = mCursor.getString(idxDate);
                    int sort = Integer.valueOf(mCursor.getString(idxSort));
                    arrDaisyBook.add(new DaisyBookInfo(id, title, path, author, publisher, date,
                            sort));
                } while (mCursor.moveToNext());
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        } finally {
            if (mCursor != null) mCursor.close();
        }
        return arrDaisyBook;
    }

    public DaisyBookInfo getDaisyBookByTitle(String title, String type) {
        DaisyBookInfo daisyBook = null;
        SQLiteDatabase mdb = null;
        Cursor mCursor = null;
        try {
            mdb = getReadableDatabase();
            mCursor = mdb.query(TABLE_NAME_DAISY_BOOK, new String[] { ID_KEY_DAISY_BOOK,
                    TITLE_KEY_DAISY_BOOK, PATH_KEY_DAISY_BOOK, AUTHOR_KEY_DAISY_BOOK,
                    PUBLISHER_KEY_DAISY_BOOK, DATE_DAISY_BOOK, SORT_KEY_DAISY_BOOK },
                    TITLE_KEY_DAISY_BOOK + "=?" + " AND " + TYPE_OF_METADATA_DAISY_BOOK + "=?",
                    new String[] { title, type }, null, null, null);
            if (mCursor != null && mCursor.moveToFirst()) {
                String id = mCursor.getString(mCursor.getColumnIndex(ID_KEY_DAISY_BOOK));
                String titleBook = mCursor.getString(mCursor.getColumnIndex(TITLE_KEY_DAISY_BOOK));
                String path = mCursor.getString(mCursor.getColumnIndex(PATH_KEY_DAISY_BOOK));
                String author = mCursor.getString(mCursor.getColumnIndex(AUTHOR_KEY_DAISY_BOOK));
                String publisher = mCursor.getString(mCursor
                        .getColumnIndex(PUBLISHER_KEY_DAISY_BOOK));
                String date = mCursor.getString(mCursor.getColumnIndex(DATE_DAISY_BOOK));
                int sort = Integer.valueOf(mCursor.getString(mCursor
                        .getColumnIndex(SORT_KEY_DAISY_BOOK)));
                daisyBook = new DaisyBookInfo(id, titleBook, path, author, publisher, date, sort);
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        } finally {
            if (mCursor != null) mCursor.close();
        }
        return daisyBook;
    }

    /**
     * ページ単位で DaisyBook を取得する（Paging 3 用）。
     *
     * @param type        メタデータ種別
     * @param searchQuery 検索クエリ（null または空文字列で全件）
     * @param limit       取得件数
     * @param offset      オフセット
     * @return 書籍リスト
     */
    public List<DaisyBookInfo> getDaisyBookPage(String type, String searchQuery, int limit, int offset) {
        ArrayList<DaisyBookInfo> result = new ArrayList<>();
        SQLiteDatabase mdb = null;
        Cursor mCursor = null;
        try {
            mdb = getReadableDatabase();
            String sql;
            String[] args;
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                sql = "SELECT " + ID_KEY_DAISY_BOOK + ", " + TITLE_KEY_DAISY_BOOK + ", "
                        + PATH_KEY_DAISY_BOOK + ", " + AUTHOR_KEY_DAISY_BOOK + ", "
                        + PUBLISHER_KEY_DAISY_BOOK + ", " + DATE_DAISY_BOOK + ", "
                        + SORT_KEY_DAISY_BOOK
                        + " FROM " + TABLE_NAME_DAISY_BOOK
                        + " WHERE " + TYPE_OF_METADATA_DAISY_BOOK + "=?"
                        + " AND (" + TITLE_KEY_DAISY_BOOK + " LIKE ?"
                        + " OR " + AUTHOR_KEY_DAISY_BOOK + " LIKE ?)"
                        + " ORDER BY " + SORT_KEY_DAISY_BOOK
                        + " LIMIT ? OFFSET ?";
                String like = "%" + searchQuery.trim() + "%";
                args = new String[]{type, like, like, String.valueOf(limit), String.valueOf(offset)};
            } else {
                sql = "SELECT " + ID_KEY_DAISY_BOOK + ", " + TITLE_KEY_DAISY_BOOK + ", "
                        + PATH_KEY_DAISY_BOOK + ", " + AUTHOR_KEY_DAISY_BOOK + ", "
                        + PUBLISHER_KEY_DAISY_BOOK + ", " + DATE_DAISY_BOOK + ", "
                        + SORT_KEY_DAISY_BOOK
                        + " FROM " + TABLE_NAME_DAISY_BOOK
                        + " WHERE " + TYPE_OF_METADATA_DAISY_BOOK + "=?"
                        + " ORDER BY " + SORT_KEY_DAISY_BOOK
                        + " LIMIT ? OFFSET ?";
                args = new String[]{type, String.valueOf(limit), String.valueOf(offset)};
            }
            mCursor = mdb.rawQuery(sql, args);
            if (mCursor.moveToFirst()) {
                int idxId = mCursor.getColumnIndex(ID_KEY_DAISY_BOOK);
                int idxTitle = mCursor.getColumnIndex(TITLE_KEY_DAISY_BOOK);
                int idxPath = mCursor.getColumnIndex(PATH_KEY_DAISY_BOOK);
                int idxAuthor = mCursor.getColumnIndex(AUTHOR_KEY_DAISY_BOOK);
                int idxPublisher = mCursor.getColumnIndex(PUBLISHER_KEY_DAISY_BOOK);
                int idxDate = mCursor.getColumnIndex(DATE_DAISY_BOOK);
                int idxSort = mCursor.getColumnIndex(SORT_KEY_DAISY_BOOK);
                do {
                    String id = mCursor.getString(idxId);
                    String titleVal = mCursor.getString(idxTitle);
                    String path = mCursor.getString(idxPath);
                    String author = mCursor.getString(idxAuthor);
                    String publisher = mCursor.getString(idxPublisher);
                    String date = mCursor.getString(idxDate);
                    int sort = Integer.valueOf(mCursor.getString(idxSort));
                    result.add(new DaisyBookInfo(id, titleVal, path, author, publisher, date, sort));
                } while (mCursor.moveToNext());
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        } finally {
            if (mCursor != null) mCursor.close();
        }
        return result;
    }

    public boolean deleteAllDaisyBook(String type) {
        boolean result = false;
        try {
            SQLiteDatabase mdb = getWritableDatabase();
            result = mdb.delete(TABLE_NAME_DAISY_BOOK, TYPE_OF_METADATA_DAISY_BOOK + "=?",
                    new String[] { type }) > 0;
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        }
        return result;
    }

    public boolean deleteDaisyBook(String id) {
        boolean result = false;
        try {
            SQLiteDatabase mdb = getWritableDatabase();
            result = mdb.delete(TABLE_NAME_DAISY_BOOK, ID_KEY_DAISY_BOOK + "=?",
                    new String[] { id }) > 0;
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, mContext);
            ex.writeLogException();
        }
        return result;
    }

    /**
     * Check exists.
     * 
     * @param name
     * @return
     */
    public boolean isExists(String name, String type) {
        boolean result = false;
        Cursor mCursor = null;
        try {
            SQLiteDatabase mdb = getReadableDatabase();
            mCursor = mdb.query(TABLE_NAME_DAISY_BOOK, new String[] { ID_KEY_DAISY_BOOK,
                    TITLE_KEY_DAISY_BOOK, PATH_KEY_DAISY_BOOK, AUTHOR_KEY_DAISY_BOOK,
                    PUBLISHER_KEY_DAISY_BOOK, DATE_DAISY_BOOK, SORT_KEY_DAISY_BOOK },
                    TITLE_KEY_DAISY_BOOK + "=?" + " AND " + TYPE_OF_METADATA_DAISY_BOOK + "=?",
                    new String[] { name, type }, null, null, null);
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
