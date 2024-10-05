package org.androiddaisyreader.worker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.github.naofum.androiddaisyreader.R;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * @author naofum
 * @date Sep 7, 2024
 */

public class DaisyEbookReaderWorker extends Worker {

    private MetaDataHandler mMetaData;
    private SharedPreferences.Editor mEditor;

    private final File mCurrentDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    //    private final File mCurrentDirectory = Environment.getExternalStorageDirectory();
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
//        notification();

        boolean isSDPresent = Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED);
        if (isSDPresent) {
            String localPath = Constants.folderContainMetadata
                    + Constants.META_DATA_SCAN_BOOK_FILE_NAME;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) { // 25
                mMetaData.writeDataToXmlFile(getDataFromMediaStore(), localPath);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // 23
                if (ContextCompat.checkSelfPermission(
                        getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    mMetaData.writeDataToXmlFile(getData(), localPath);
                }
            } else {
                mMetaData.writeDataToXmlFile(getData(), localPath);
            }
        }

        finish();
        return Result.success();

    }

    private void notification() {
        String channelId = "ReaderWorker";
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setContentTitle(context.getString(R.string.title_activity_daisy_reader_scan_book))
                .setContentText("")
                .setSmallIcon(R.drawable.media_play);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29 Android 10
            CharSequence name = context.getString(R.string.title_activity_daisy_reader_scan_book);
            String description = "";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);

            notificationManager.notify(1, builder.build());
        }
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
     * Gets the data.
     *
     * @return the data
     */
    private List<DaisyBookInfo> getDataFromMediaStore() {
        List<DaisyBookInfo> filesResult = Collections.synchronizedList(new ArrayList<DaisyBookInfo>());
        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
//            collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }
        ContentResolver resolver = getApplicationContext().getContentResolver();
        String selection = MediaStore.Files.FileColumns.MIME_TYPE + "='application/zip'" +
                " OR " + MediaStore.Files.FileColumns.MIME_TYPE + "='application/epub+zip'";
        Cursor cursor = resolver.query(collection, null, selection, null, null);
        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
        int columnIndex = cursor.getColumnIndexOrThrow("_data");
        while (cursor.moveToNext()) {
            long id = cursor.getLong(idColumn);
            String title = cursor.getString(titleColumn);
            String pathOfUri = cursor.getString(columnIndex);
            Uri uri;
            uri = ContentUris.withAppendedId(collection, id);
            if (pathOfUri.toLowerCase().endsWith(".zip") || pathOfUri.toLowerCase().endsWith(".epub")) {
                InputStream input = null;
                try {
                    input = new BufferedInputStream(resolver.openInputStream(uri));
                    ZippedBookInfo info = new ZippedBookInfo();
                    DaisyBookInfo daisyBookInfo = info.readFromZipStream(input);
                    // EBOOK ?
                    if (daisyBookInfo != null) {
                        daisyBookInfo.setId(Long.valueOf(id).toString());
                        daisyBookInfo.setPath(uri.toString());
                        filesResult.add(daisyBookInfo);
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (input != null) {
                        try {
                            input.close();
                        } catch (IOException e) {
                            //
                        }
                    }
                }
            }
        }
        return filesResult;
    }

    /**
     * Gets the data.
     *
     * @return the data
     */
    private List<DaisyBookInfo> getData() {
        ArrayList<DaisyBookInfo> filesResult = new ArrayList<DaisyBookInfo>();
        File[] files = mCurrentDirectory.listFiles();
        try {
            if (files != null) {
                int lengthOfFile = files.length;
                for (int i = 0; i < lengthOfFile; i++) {
                    List<String> listResult = DaisyBookUtil.getDaisyBook(files[i], false);

                    for (String result : listResult) {
                        try {
                            File daisyPath = new File(result);
                            DaisyBookInfo daisyBook;
                            DaisyBook mBook202 = null;
                            // Check zip files.
                            if (!daisyPath.getAbsolutePath().endsWith(Constants.SUFFIX_ZIP_FILE) && !daisyPath.getAbsolutePath().endsWith(Constants.SUFFIX_EPUB_FILE)) {
                                if (DaisyBookUtil.getNccFileName(daisyPath) != null) {
                                    // We think we have a DAISY 2.02 book as
                                    // these include an NCC file.
                                    result = result + File.separator
                                            + DaisyBookUtil.getNccFileName(daisyPath);
                                    mBook202 = DaisyBookUtil.getDaisy202Book(result, getApplicationContext());
                                }
                            } else {
                                mBook202 = DaisyBookUtil.getDaisy202Book(result, getApplicationContext());
                            }
                            // If book is not daisy 2.02, go to function daisy
                            // 3.0 to read it.
                            if (mBook202 == null) {
                                DaisyBook mBook30 = DaisyBookUtil.getDaisy30Book(result, getApplicationContext());
                                daisyBook = getDataFromDaisyBook(mBook30, result);
                            } else {
                                daisyBook = getDataFromDaisyBook(mBook202, result);
                            }
                            filesResult.add(daisyBook);

                        } catch (Exception e) {
                            PrivateException ex = new PrivateException(e,
                                    context);
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
     * Gets the data from daisy30 book.
     *
     * @param daisybook the daisy30
     * @param result    the result
     * @return the data from daisy book
     */
    private DaisyBookInfo getDataFromDaisyBook(DaisyBook daisybook, String result) {
        DaisyBookInfo daisyBook = null;

        Date date = daisybook.getDate();
        String sDate = formatDateOrReturnEmptyString(date);
        daisyBook = new DaisyBookInfo("", daisybook.getTitle(), result, daisybook.getAuthor(),
                daisybook.getPublisher(), sDate, 1);
        return daisyBook;
    }

    /**
     * Format date or return empty string.
     *
     * @param date the date
     * @return the string
     */
    private String formatDateOrReturnEmptyString(Date date) {
        String sDate = "";
        if (date != null) {
            if (Locale.getDefault().getLanguage().equals("ja")) {
                sDate = String.format(Locale.getDefault(), ("%tY/%tm/%td %n"), date, date, date);
            } else {
                sDate = String.format(Locale.getDefault(), ("%tB %te, %tY %n"), date, date, date);
            }
        }
        return sDate;
    }

}
