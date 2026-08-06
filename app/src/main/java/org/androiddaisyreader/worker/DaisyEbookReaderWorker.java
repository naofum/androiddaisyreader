package org.androiddaisyreader.worker;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.androiddaisyreader.apps.PrivateException;
import org.androiddaisyreader.metadata.MetaDataHandler;
import org.androiddaisyreader.model.DaisyBook;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.model.ZippedBookInfo;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * バックグラウンドで書籍をスキャンし、メタデータXMLに保存するWorker。
 *
 * API 29 (Android 10) 以降は MediaStore.Downloads を使用して、
 * 自アプリがダウンロードした ZIP/EPUB ファイルを検出する。
 * （Scoped Storage により他アプリのファイルは参照不可）
 *
 * @author naofum
 * {@code @date} Sep 7, 2024
 */

public class DaisyEbookReaderWorker extends Worker {
    private final String TAG = "DaisyReaderWorker";

    private MetaDataHandler mMetaData;
    private SharedPreferences.Editor mEditor;

    private final File mCurrentDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    private final Context context = getApplicationContext();

    public DaisyEbookReaderWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        start();

        boolean isSDPresent = Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED);
        if (isSDPresent) {
            String localPath = Constants.folderContainMetadata
                    + Constants.META_DATA_SCAN_BOOK_FILE_NAME;
            // API 29+: MediaStore.Downloads で自アプリDL分を検出
            mMetaData.writeDataToXmlFile(getDataFromDownloads(), localPath);
        }

        finish();
        return Result.success();
    }

    private void start() {
        mMetaData = new MetaDataHandler();
        SharedPreferences mPreferences = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        mEditor = mPreferences.edit();
        mEditor.putBoolean(Constants.SERVICE_DONE, false);
        mEditor.commit();
    }

    private void finish() {
        mEditor.putBoolean(Constants.SERVICE_DONE, true);
        mEditor.commit();
    }

    /**
     * MediaStore.Downloads から自アプリがダウンロードした ZIP/EPUB を検出する。
     * API 29+ 対応。パーミッション不要（自アプリが挿入したファイルは常にアクセス可能）。
     *
     * @return 検出された書籍情報のリスト
     */
    private List<DaisyBookInfo> getDataFromDownloads() {
        List<DaisyBookInfo> filesResult = new ArrayList<>();
        ContentResolver resolver = getApplicationContext().getContentResolver();

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

        String[] projection = {
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.MIME_TYPE
        };

        String selection = MediaStore.Downloads.MIME_TYPE + "=? OR " +
                MediaStore.Downloads.MIME_TYPE + "=?";
        String[] selectionArgs = {"application/zip", "application/epub+zip"};

        try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, null)) {
            if (cursor == null) {
                Log.d(TAG, "Cursor is null for Downloads query");
                return filesResult;
            }

            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME);

            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String displayName = cursor.getString(nameColumn);

                if (displayName == null) continue;
                String lowerName = displayName.toLowerCase(Locale.ROOT);
                if (!lowerName.endsWith(".zip") && !lowerName.endsWith(".epub")) {
                    continue;
                }

                Uri contentUri = ContentUris.withAppendedId(collection, id);
                DaisyBookInfo bookInfo = readBookInfoFromUri(resolver, contentUri, id);
                if (bookInfo != null) {
                    filesResult.add(bookInfo);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying MediaStore.Downloads", e);
        }

        return filesResult;
    }

    /**
     * content:// URI からZIPストリームを読み取り、書籍メタデータを取得する。
     *
     * @param resolver ContentResolver
     * @param uri      書籍の content:// URI
     * @param id       MediaStore のID
     * @return 書籍情報、読み取れない場合はnull
     */
    private DaisyBookInfo readBookInfoFromUri(ContentResolver resolver, Uri uri, long id) {
        // MS932 charset で試行
        try (InputStream input = new BufferedInputStream(resolver.openInputStream(uri))) {
            DaisyBookInfo info = ZippedBookInfo.readFromZipStream(input, Charset.forName("MS932"));
            if (info != null) {
                info.setId(String.valueOf(id));
                info.setPath(uri.toString());
                return info;
            }
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Not permitted: " + uri);
            return null;
        } catch (IllegalArgumentException iae) {
            // charset フォールバック
            return readBookInfoWithDefaultCharset(resolver, uri, id);
        } catch (Exception e) {
            Log.d(TAG, "Error reading: " + uri, e);
        }
        return null;
    }

    /**
     * content:// URI からZIPストリームを読み取り、書籍メタデータを取得する。
     *
     * @param resolver ContentResolver
     * @param uri      書籍の content:// URI
     * @param id       MediaStore のID
     * @return 書籍情報、読み取れない場合はnull
     */
    private DaisyBookInfo readBookInfoFromUri_bak(ContentResolver resolver, Uri uri, long id) {
        // キャッシュにコピーしてローカルパスを取得
        String cachedPath;
        try {
            java.io.File cachedFile = org.androiddaisyreader.utils.CacheHelper.copyToCache(
                    getApplicationContext(), uri);
            cachedPath = cachedFile.getAbsolutePath();
        } catch (Exception e) {
            Log.d(TAG, "Failed to cache: " + uri, e);
            return null;
        }

        // MS932 charset で試行
        try (InputStream input = new BufferedInputStream(resolver.openInputStream(uri))) {
            DaisyBookInfo info = ZippedBookInfo.readFromZipStream(input, Charset.forName("MS932"));
            if (info != null) {
                info.setId(String.valueOf(id));
                info.setPath(cachedPath);
                return info;
            }
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Not permitted: " + uri);
            return null;
        } catch (IllegalArgumentException iae) {
            // charset フォールバック
//            return readBookInfoWithDefaultCharset(resolver, uri, id, cachedPath);
        } catch (Exception e) {
            Log.d(TAG, "Error reading: " + uri, e);
        }
        return null;
    }

    /**
     * デフォルト charset でフォールバック読み取り。
     */
    private DaisyBookInfo readBookInfoWithDefaultCharset(ContentResolver resolver, Uri uri, long id) {
        try (InputStream input = new BufferedInputStream(resolver.openInputStream(uri))) {
            DaisyBookInfo info = ZippedBookInfo.readFromZipStream(input, Charset.defaultCharset());
            if (info != null) {
                info.setId(String.valueOf(id));
                info.setPath(uri.toString());
                return info;
            }
        } catch (Exception e) {
            Log.d(TAG, "Fallback charset also failed: " + uri, e);
        }
        return null;
    }

    /**
     * デフォルト charset でフォールバック読み取り。
     */
    private DaisyBookInfo readBookInfoWithDefaultCharset_bak(ContentResolver resolver, Uri uri, long id, String cachedPath) {
        try (InputStream input = new BufferedInputStream(resolver.openInputStream(uri))) {
            DaisyBookInfo info = ZippedBookInfo.readFromZipStream(input, Charset.defaultCharset());
            if (info != null) {
                info.setId(String.valueOf(id));
                info.setPath(cachedPath);
                return info;
            }
        } catch (Exception e) {
            Log.d(TAG, "Fallback charset also failed: " + uri, e);
        }
        return null;
    }

    /**
     * レガシー方式: ファイルシステムから直接スキャン（API 28以下用）。
     *
     * @return 検出された書籍情報のリスト
     */
    private List<DaisyBookInfo> getData() {
        ArrayList<DaisyBookInfo> filesResult = new ArrayList<>();
        File[] files = mCurrentDirectory.listFiles();
        try {
            if (files != null) {
                for (File file : files) {
                    List<String> listResult = DaisyBookUtil.getDaisyBook(file, false);

                    for (String result : listResult) {
                        try {
                            File daisyPath = new File(result);
                            DaisyBookInfo daisyBook;
                            DaisyBook mBook202 = null;
                            // Check zip files.
                            if (!daisyPath.getAbsolutePath().endsWith(Constants.SUFFIX_ZIP_FILE) && !daisyPath.getAbsolutePath().endsWith(Constants.SUFFIX_EPUB_FILE)) {
                                if (DaisyBookUtil.getNccFileName(daisyPath) != null) {
                                    result = result + File.separator
                                            + DaisyBookUtil.getNccFileName(daisyPath);
                                    mBook202 = DaisyBookUtil.getDaisy202Book(result, getApplicationContext());
                                }
                            } else {
                                mBook202 = DaisyBookUtil.getDaisy202Book(result, getApplicationContext());
                            }
                            if (mBook202 == null) {
                                DaisyBook mBook30 = DaisyBookUtil.getDaisy30Book(result, getApplicationContext());
                                daisyBook = getDataFromDaisyBook(mBook30, result);
                            } else {
                                daisyBook = getDataFromDaisyBook(mBook202, result);
                            }
                            filesResult.add(daisyBook);

                        } catch (Exception e) {
                            PrivateException ex = new PrivateException(e, context);
                            ex.writeLogException();
                        }
                    }
                }
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, context);
            ex.writeLogException();
        }
        return filesResult;
    }

    /**
     * DaisyBook から DaisyBookInfo を生成する。
     */
    private DaisyBookInfo getDataFromDaisyBook(DaisyBook daisybook, String result) {
        Date date = daisybook.getDate();
        String sDate = formatDateOrReturnEmptyString(date);
        return new DaisyBookInfo("", daisybook.getTitle(), result, daisybook.getAuthor(),
                daisybook.getPublisher(), sDate, 1);
    }

    /**
     * 日付をフォーマットする。nullの場合は空文字を返す。
     */
    private String formatDateOrReturnEmptyString(Date date) {
        String sDate = "";
        if (date != null) {
            if (Locale.getDefault().getLanguage().equals("ja")) {
                sDate = String.format(Locale.getDefault(), "%tY/%tm/%td", date, date, date);
            } else {
                sDate = String.format(Locale.getDefault(), "%tB %te, %tY", date, date, date);
            }
        }
        return sDate;
    }
}
