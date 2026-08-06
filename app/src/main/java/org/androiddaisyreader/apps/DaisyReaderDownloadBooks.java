package org.androiddaisyreader.apps;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.androiddaisyreader.adapter.DaisyBookAdapter;
import org.androiddaisyreader.base.DaisyEbookReaderBaseActivity;
import org.androiddaisyreader.metadata.MetaDataHandler;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.model.ZippedBookInfo;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.CacheHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;

import com.github.naofum.androiddaisyreader.R;

/**
 * The Class DaisyReaderDownloadBooks.
 */
@SuppressLint("NewApi")
public class DaisyReaderDownloadBooks extends DaisyEbookReaderBaseActivity {

    private String mLink;
    private SQLiteDaisyBookHelper mSql;
    private DaisyBookAdapter mDaisyBookAdapter;
    private List<DaisyBookInfo> mlistDaisyBook;
    private List<DaisyBookInfo> mListDaisyBookOriginal;
    private DaisyBookInfo mDaisyBook;
    private EditText mTextSearch;
    public static final String PATH = Environment.getExternalStorageDirectory().toString()
            + Constants.FOLDER_DOWNLOADED + "/";

    private DownloadManager downloadManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_books);

        mTextSearch = (EditText) findViewById(R.id.edit_text_search);
        mLink = getIntent().getStringExtra(Constants.LINK_WEBSITE);
        String websiteName = getIntent().getStringExtra(Constants.NAME_WEBSITE);

        mSql = SQLiteDaisyBookHelper.getInstance(DaisyReaderDownloadBooks.this);
        mSql.deleteAllDaisyBook(Constants.TYPE_DOWNLOAD_BOOK);
        createDownloadData();
        mlistDaisyBook = mSql.getAllDaisyBook(Constants.TYPE_DOWNLOAD_BOOK);
        mDaisyBookAdapter = new DaisyBookAdapter(DaisyReaderDownloadBooks.this, mlistDaisyBook);
        ListView listDownload = (ListView) findViewById(R.id.list_view_download_books);
        listDownload.setAdapter(mDaisyBookAdapter);
        listDownload.setOnItemClickListener(onItemClick);
        mListDaisyBookOriginal = new ArrayList<DaisyBookInfo>(mlistDaisyBook);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(websiteName.length() != 0 ? websiteName : "");

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home:
                backToTopScreen();
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return false;
    }

    /**
     * Wirte data to sqlite from metadata
     */
    private void createDownloadData() {
        try (InputStream databaseInputStream = new FileInputStream(Constants.folderContainMetadata
                + Constants.META_DATA_FILE_NAME)) {
            MetaDataHandler metadata = new MetaDataHandler();
            NodeList nList = metadata.readDataDownloadFromXmlFile(databaseInputStream, mLink);
            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {

                    Element eElement = (Element) nNode;
                    String author = eElement.getElementsByTagName(Constants.ATT_AUTHOR).item(0)
                            .getTextContent();
                    String publisher = eElement.getElementsByTagName(Constants.ATT_PUBLISHER)
                            .item(0).getTextContent();
                    String path = eElement.getAttribute(Constants.ATT_LINK);
                    String title = eElement.getElementsByTagName(Constants.ATT_TITLE).item(0)
                            .getTextContent();
                    String date = eElement.getElementsByTagName(Constants.ATT_DATE).item(0)
                            .getTextContent();
                    DaisyBookInfo daisyBook = new DaisyBookInfo("", title, path, author, publisher,
                            date, 1);
                    mSql.addDaisyBook(daisyBook, Constants.TYPE_DOWNLOAD_BOOK);
                }
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
            ex.writeLogException();
        }
    }

    private OnItemClickListener onItemClick = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
            final DaisyBookInfo daisyBook = mlistDaisyBook.get(position);
            boolean isDoubleTap = handleClickItem(position);
            if (isDoubleTap) {
                downloadABook(position);
            } else {
                speakTextOnHandler(daisyBook.getTitle());
            }
        }
    };

    /**
     * Create folder if not exists
     *
     * @return
     */
    private boolean checkFolderIsExist() {
        boolean result = false;
        String path = ("".equals(Constants.folderRoot) ? PATH : Constants.folderRoot + Constants.FOLDER_DOWNLOADED + "/"); // 20180710
        File folder = new File(path);
        result = folder.exists();
        if (!result) {
            result = folder.mkdir();
        }
        return result;
    }

    /**
     * handle search book when text changed.
     */
    private void handleSearchBook() {
        mTextSearch.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mListDaisyBookOriginal != null && mListDaisyBookOriginal.size() != 0) {
                    mlistDaisyBook = DaisyBookUtil.searchBookWithText(s, mlistDaisyBook,
                            mListDaisyBookOriginal);
                    mDaisyBookAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleSearchBook();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void doDownloadManager(DaisyBookInfo daisyBook) {
        Uri uri = Uri.parse(daisyBook.getPath());
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, uri.getLastPathSegment())
                .setTitle(daisyBook.getTitle())
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setVisibleInDownloadsUi(true);
        request.allowScanningByMediaScanner();
        downloadManager = (DownloadManager) this.getSystemService(Context.DOWNLOAD_SERVICE);
        long id = downloadManager.enqueue(request);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        Cursor cursor = downloadManager.query(query);
        cursor.moveToFirst();

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                    System.out.println(id);
                    if (id == 0) return;
                    DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
                    final Cursor cursor = downloadManager.query(query);
                    if (cursor.moveToFirst()) {
                        int indexLocalURI = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        String downloadTo = "";
                        if (indexLocalURI > -1) {
                            downloadTo = cursor.getString(indexLocalURI);
                        }
                        Log.i("onReceive: ", "The file has been downloaded to: " + downloadTo);
                        int indexUri = cursor.getColumnIndex(DownloadManager.COLUMN_URI);
                        String downloadFrom = "";
                        if (indexUri > -1) {
                            downloadFrom = cursor.getString(indexUri);
                        }
                        Log.i("onReceive: ", "The file has been downloaded from: " + downloadFrom);
                        int indexMediaProviderUri = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIAPROVIDER_URI);
                        String mediaproviderUri = "";
                        if (indexMediaProviderUri > -1) {
                            mediaproviderUri = cursor.getString(indexMediaProviderUri);
                        }
                        Log.i("onReceive: ", "The file media uri: " + mediaproviderUri);

                        ContentResolver resolver = getApplicationContext()
                                .getContentResolver();
                        try (InputStream stream = resolver.openInputStream(Uri.parse(mediaproviderUri))) {
                            ZippedBookInfo zippedBookInfo = new ZippedBookInfo();
                            DaisyBookInfo info = zippedBookInfo.readFromZipStream(new BufferedInputStream(stream));
                            // content:// URIをキャッシュし、キャッシュのローカルパスでDB登録
                            File cachedFile = CacheHelper.copyToCache(
                                    getApplicationContext(), mediaproviderUri);
                            info.setPath(cachedFile.getAbsolutePath());
                            info.setId(Long.valueOf(id).toString());
                            mSql.addDaisyBook(info, Constants.TYPE_DOWNLOADED_BOOK);
                            // 最近の書籍にも登録
                            DaisyBookUtil.addRecentBookToSQLite(info,
                                    Constants.NUMBER_OF_RECENTBOOK_DEFAULT, mSql);

                            Intent downloaded = new Intent(context, DaisyReaderDownloadedBooks.class);
                            context.startActivity(downloaded);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                }
            }
        };
        registerReceiver(broadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_NOT_EXPORTED);

    }

    private void downloadABook(int position) {
        boolean isConnected = DaisyBookUtil.getConnectivityStatus(DaisyReaderDownloadBooks.this) != Constants.CONNECT_TYPE_NOT_CONNECTED;
        IntentController intent = new IntentController(DaisyReaderDownloadBooks.this);
        if (isConnected) {
            if (checkFolderIsExist()) {
                mDaisyBook = mlistDaisyBook.get(position);
                String link = mDaisyBook.getPath();

                StorageChecker.checkStorage(link, this, result -> {
                    switch (result) {
                        case 1:
                            // 空き容量あり
                            doDownloadManager(mDaisyBook);
                            break;
                        case 0:
                            // 空き容量なし
                            intent.pushToDialog(DaisyReaderDownloadBooks.this
                                            .getString(R.string.error_not_enough_space),
                                    DaisyReaderDownloadBooks.this.getString(R.string.error_title),
                                    R.raw.error, false, false, null);
                            break;
                        case 2:
                            // エラー
                            intent.pushToDialog(DaisyReaderDownloadBooks.this
                                            .getString(R.string.error_cannot_dowload),
                                    DaisyReaderDownloadBooks.this.getString(R.string.error_title),
                                    R.raw.error, false, false, null);
                            break;
                    }
                });
            }
        } else {
            intent.pushToDialog(
                    DaisyReaderDownloadBooks.this.getString(R.string.error_connect_internet),
                    DaisyReaderDownloadBooks.this.getString(R.string.error_title), R.raw.error,
                    false, false, null);
        }
    }

    /**
     * Check storage.
     *
     */
    private static class StorageChecker {

        public interface Callback {
            void onResult(int result);
        }

        private static final ExecutorService executor = Executors.newSingleThreadExecutor();
        private static final Handler mainHandler = new Handler(Looper.getMainLooper());

        public static void checkStorage(String link, Context context, Callback callback) {
            executor.execute(() -> {
                int result;

                try {
                    long lengthOfFile = getContentLengthWithFallback(link);
                    long freeSize = getDownloadDirectoryFreeSize();

                    if (lengthOfFile < 0) {
                        // ファイルサイズ不明（URLが無効、またはサーバーが長さを返さない）
                        // 空き容量十分ならダウンロードを試行する
                        if (freeSize > 0) {
                            result = 1;
                        } else {
                            result = 0;
                        }
                    } else if (freeSize > lengthOfFile) {
                        result = 1;
                    } else {
                        result = 0;
                    }

                } catch (Exception e) {
                    result = 2;
                    if (context != null) {
                        new PrivateException(e, context.getApplicationContext()).writeLogException();
                    }
                }

                int finalResult = result;
                mainHandler.post(() -> callback.onResult(finalResult));
            });
        }

        private static long getContentLengthWithFallback(String link) throws Exception {
            long contentLength = getContentLengthByHead(link);
            if (contentLength >= 0) {
                return contentLength;
            }
            // HEADが-1（タイムアウト等で接続不可）の場合のみGETにフォールバック
            return getContentLengthByGet(link);
        }

        private static long getContentLengthByHead(String link) throws Exception {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(link);
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (isSuccess(responseCode)) {
                    return connection.getContentLengthLong();
                }

                // HTTPエラー（404等）の場合は例外を投げる
                throw new IllegalStateException("HTTP error: " + responseCode);
            } catch (java.net.SocketTimeoutException | java.net.UnknownHostException e) {
                // タイムアウトやDNSエラーの場合はGETにフォールバックするため-1を返す
                return -1L;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private static long getContentLengthByGet(String link) throws Exception {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            try {
                URL url = new URL(link);
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (!isSuccess(responseCode)) {
                    throw new IllegalStateException("HTTP error: " + responseCode);
                }

                long contentLength = connection.getContentLengthLong();
                if (contentLength >= 0) {
                    return contentLength;
                }

                // ヘッダに長さがない場合、サイズ不明として扱う
                // 必要なら inputStream を最後まで読んでサイズ計算もできるが、
                // 通信コストが大きいので通常は非推奨
                inputStream = connection.getInputStream();
                return -1L;

            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (Exception ignored) {
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private static boolean isSuccess(int responseCode) {
            return responseCode >= 200 && responseCode < 300;
        }

        private static long getDownloadDirectoryFreeSize() {
            StatFs statFs = new StatFs(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                    ).getAbsolutePath()
            );
            return statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
        }
    }

}
