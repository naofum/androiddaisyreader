package org.androiddaisyreader.sqlite;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class SQLiteHandler extends SQLiteOpenHelper {

    private static final String TAG = "SQLiteHandler";
    private static final String DATABASE_NAME = "EbookReaderDB";
    /**
     * データベースバージョン履歴:
     *   1: 初期スキーマ（Bookmarks, CurrentInformation, RecentBooks, DaisyBook）
     *   2: Bookmarks に _text_show カラム追加
     *      DaisyBook に _language カラム追加
     */
    private static final int DATABASE_VERSION = 2;

    private static SQLiteHandler sInstance;

    // ---- Bookmarks テーブル ----
    public static final String TABLE_NAME_BOOKMARK = "Bookmarks";
    public static final String ID_KEY_BOOKMARK = "_id";
    public static final String AUDIO_FILE_NAME_KEY_BOOKMARK = "_audio_file_name";
    public static final String PATH_KEY_BOOKMARK = "_path";
    public static final String TEXT_KEY_BOOKMARK = "_text";
    public static final String TEXT_SHOW_KEY_BOOKMARK = "_text_show";
    public static final String TIME_KEY_BOOKMARK = "_time";
    public static final String SECTION_KEY_BOOKMARK = "_section";
    public static final String SORT_KEY_BOOKMARK = "_sort";

    // ---- CurrentInformation テーブル ----
    public static final String TABLE_NAME_CURRENT_INFORMATION = "CurrentInformation";
    public static final String ID_KEY_CURRENT_INFORMATION = "_id";
    public static final String AUDIO_NAME_KEY_CURRENT_INFORMATION = "_audio_name";
    public static final String PATH_KEY_CURRENT_INFORMATION = "_path";
    public static final String TIME_KEY_CURRENT_INFORMATION = "_time";
    public static final String SECTION_KEY_CURRENT_INFORMATION = "_section";
    public static final String PLAYING_KEY_CURRENT_INFORMATION = "_playing";
    public static final String SENTENCE_KEY_CURRENT_INFORMATION = "_sentence";
    public static final String ACTIVITY_KEY_CURRENT_INFORMATION = "_activity";
    public static final String FIRST_NEXT_KEY_CURRENT_INFORMATION = "_first_next";
    public static final String FIRST_PREVIOUS_KEY_CURRENT_INFORMATION = "_first_previous";
    public static final String AT_THE_END_KEY_CURRENT_INFORMATION = "_at_the_end";

    // ---- RecentBooks テーブル ----
    public static final String TABLE_NAME_RECENT_BOOKS = "RecentBooks";
    public static final String NAME_KEY_RECENT_BOOKS = "_name";
    public static final String PATH_KEY_RECENT_BOOKS = "_path";
    public static final String SORT_KEY_RECENT_BOOKS = "_sort";

    // ---- DaisyBook テーブル ----
    public static final String TABLE_NAME_DAISY_BOOK = "DaisyBook";
    public static final String ID_KEY_DAISY_BOOK = "_id";
    public static final String TITLE_KEY_DAISY_BOOK = "_name";
    public static final String PATH_KEY_DAISY_BOOK = "_path";
    public static final String AUTHOR_KEY_DAISY_BOOK = "_author";
    public static final String PUBLISHER_KEY_DAISY_BOOK = "_publisher";
    public static final String DATE_DAISY_BOOK = "_date";
    public static final String TYPE_OF_METADATA_DAISY_BOOK = "_type";
    public static final String SORT_KEY_DAISY_BOOK = "_sort";
    public static final String LANGUAGE_KEY_DAISY_BOOK = "_language";

    /**
     * Singletonインスタンスを取得する。
     * ApplicationContextを使用してActivityリークを防止する。
     */
    public static synchronized SQLiteHandler getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SQLiteHandler(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * @deprecated Singleton化のため、getInstance(Context)を使用してください。
     *             既存コードの後方互換性のために残しています。
     */
    @Deprecated
    public SQLiteHandler(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(createBookmarkTableSql());
        db.execSQL(createCurrentInformationTableSql());
        db.execSQL(createRecentBooksTableSql());
        db.execSQL(createDaisyBookTableSql());
        Log.i(TAG, "Database created with version " + DATABASE_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        // 段階的マイグレーション: 各バージョンの変更を順番に適用
        if (oldVersion < 2) {
            upgradeToVersion2(db);
        }
        // 将来のマイグレーション例:
        // if (oldVersion < 3) {
        //     upgradeToVersion3(db);
        // }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // ダウングレード時はデータを保持しつつ、新しいカラムを無視する
        // SQLite は存在しないカラムへのアクセスでエラーになるため、
        // 安全策としてテーブルを再作成する
        Log.w(TAG, "Downgrading database from version " + oldVersion + " to " + newVersion
                + ". This will reset all data.");
        dropAllTables(db);
        onCreate(db);
    }

    // =========================================================================
    // マイグレーション: Version 1 → 2
    // =========================================================================

    /**
     * Version 2 へのマイグレーション:
     * - Bookmarks テーブルに _text_show カラムを追加
     * - DaisyBook テーブルに _language カラムを追加
     */
    private void upgradeToVersion2(SQLiteDatabase db) {
        Log.i(TAG, "Applying migration to version 2");

        // Bookmarks: _text_show カラム追加
        addColumnIfNotExists(db, TABLE_NAME_BOOKMARK, TEXT_SHOW_KEY_BOOKMARK, "TEXT");

        // DaisyBook: _language カラム追加
        addColumnIfNotExists(db, TABLE_NAME_DAISY_BOOK, LANGUAGE_KEY_DAISY_BOOK, "TEXT");
    }

    // =========================================================================
    // テーブル作成SQL
    // =========================================================================

    private String createBookmarkTableSql() {
        return "CREATE TABLE " + TABLE_NAME_BOOKMARK + "("
                + ID_KEY_BOOKMARK + " TEXT PRIMARY KEY,"
                + AUDIO_FILE_NAME_KEY_BOOKMARK + " TEXT,"
                + PATH_KEY_BOOKMARK + " TEXT,"
                + TEXT_KEY_BOOKMARK + " TEXT,"
                + TEXT_SHOW_KEY_BOOKMARK + " TEXT,"
                + TIME_KEY_BOOKMARK + " INTEGER,"
                + SECTION_KEY_BOOKMARK + " INTEGER,"
                + SORT_KEY_BOOKMARK + " INTEGER"
                + ")";
    }

    private String createCurrentInformationTableSql() {
        return "CREATE TABLE " + TABLE_NAME_CURRENT_INFORMATION + "("
                + ID_KEY_CURRENT_INFORMATION + " TEXT PRIMARY KEY,"
                + AUDIO_NAME_KEY_CURRENT_INFORMATION + " TEXT,"
                + PATH_KEY_CURRENT_INFORMATION + " TEXT,"
                + TIME_KEY_CURRENT_INFORMATION + " INTEGER,"
                + SECTION_KEY_CURRENT_INFORMATION + " INTEGER,"
                + PLAYING_KEY_CURRENT_INFORMATION + " INTEGER,"
                + SENTENCE_KEY_CURRENT_INFORMATION + " INTEGER,"
                + ACTIVITY_KEY_CURRENT_INFORMATION + " TEXT,"
                + FIRST_NEXT_KEY_CURRENT_INFORMATION + " INTEGER,"
                + FIRST_PREVIOUS_KEY_CURRENT_INFORMATION + " INTEGER,"
                + AT_THE_END_KEY_CURRENT_INFORMATION + " TEXT"
                + ")";
    }

    private String createRecentBooksTableSql() {
        return "CREATE TABLE " + TABLE_NAME_RECENT_BOOKS + "("
                + NAME_KEY_RECENT_BOOKS + " TEXT PRIMARY KEY,"
                + PATH_KEY_RECENT_BOOKS + " TEXT,"
                + SORT_KEY_RECENT_BOOKS + " INTEGER"
                + ")";
    }

    private String createDaisyBookTableSql() {
        return "CREATE TABLE " + TABLE_NAME_DAISY_BOOK + "("
                + ID_KEY_DAISY_BOOK + " TEXT PRIMARY KEY,"
                + PATH_KEY_DAISY_BOOK + " TEXT,"
                + TITLE_KEY_DAISY_BOOK + " TEXT NOT NULL,"
                + AUTHOR_KEY_DAISY_BOOK + " TEXT,"
                + PUBLISHER_KEY_DAISY_BOOK + " TEXT,"
                + TYPE_OF_METADATA_DAISY_BOOK + " TEXT,"
                + DATE_DAISY_BOOK + " TEXT,"
                + LANGUAGE_KEY_DAISY_BOOK + " TEXT,"
                + SORT_KEY_DAISY_BOOK + " INTEGER"
                + ")";
    }

    // =========================================================================
    // ユーティリティ
    // =========================================================================

    /**
     * カラムが存在しない場合のみ ALTER TABLE ADD COLUMN を実行する。
     * SQLite は IF NOT EXISTS 構文をサポートしないため、PRAGMA で確認する。
     */
    private void addColumnIfNotExists(SQLiteDatabase db, String tableName, String columnName, String columnType) {
        if (!isColumnExists(db, tableName, columnName)) {
            String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType;
            db.execSQL(sql);
            Log.i(TAG, "Added column " + columnName + " to " + tableName);
        } else {
            Log.d(TAG, "Column " + columnName + " already exists in " + tableName);
        }
    }

    /**
     * 指定テーブルに指定カラムが存在するかチェックする。
     */
    private boolean isColumnExists(SQLiteDatabase db, String tableName, String columnName) {
        boolean exists = false;
        android.database.Cursor cursor = null;
        try {
            cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    if (columnName.equals(name)) {
                        exists = true;
                        break;
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return exists;
    }

    /**
     * 全テーブルを削除する（onDowngrade等での使用を想定）。
     */
    private void dropAllTables(SQLiteDatabase db) {
        dropTable(db, TABLE_NAME_BOOKMARK);
        dropTable(db, TABLE_NAME_CURRENT_INFORMATION);
        dropTable(db, TABLE_NAME_RECENT_BOOKS);
        dropTable(db, TABLE_NAME_DAISY_BOOK);
    }

    /**
     * テーブルを削除する。
     *
     * @param db        データベース
     * @param tableName テーブル名
     */
    private void dropTable(SQLiteDatabase db, String tableName) {
        db.execSQL("DROP TABLE IF EXISTS " + tableName);
    }
}
