package org.androiddaisyreader.apps;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.androiddaisyreader.adapter.DaisyBookAdapter;
import org.androiddaisyreader.base.DaisyEbookReaderBaseActivity;
import org.androiddaisyreader.metadata.MetaDataHandler;
import org.androiddaisyreader.model.DaisyBook;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.model.ZippedBookInfo;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.github.naofum.androiddaisyreader.R;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * The Class DaisyReaderDownloadBooks.
 */
@SuppressLint("NewApi")
public class DaisyReaderDownloadBooks extends DaisyEbookReaderBaseActivity {

    private String mLink;
    private SQLiteDaisyBookHelper mSql;
    private DaisyBookAdapter mDaisyBookAdapter;
    private String mName;
    private DownloadFileFromURL mTask;
    private List<DaisyBookInfo> mlistDaisyBook;
    private List<DaisyBookInfo> mListDaisyBookOriginal;
    private DaisyBookInfo mDaisyBook;
    private EditText mTextSearch;
    public static final String PATH = Environment.getExternalStorageDirectory().toString()
            + Constants.FOLDER_DOWNLOADED + "/";
    private ProgressDialog mProgressDialog;
    private AlertDialog alertDialog;

    private LocalBroadcastManager broadcastManager;
    private BroadcastReceiver broadcastReceiver;
    private DownloadManager downloadManager;

    private static final int MAX_PROGRESS = 100;
    private static final int SIZE = 8192;
    private static final int BYTE_VALUE = 1024;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_books);

        mTextSearch = (EditText) findViewById(R.id.edit_text_search);
        mLink = getIntent().getStringExtra(Constants.LINK_WEBSITE);
        String websiteName = getIntent().getStringExtra(Constants.NAME_WEBSITE);

        mSql = new SQLiteDaisyBookHelper(DaisyReaderDownloadBooks.this);
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
        InputStream databaseInputStream = null;
        try {
            databaseInputStream = new FileInputStream(Constants.folderContainMetadata
                    + Constants.META_DATA_FILE_NAME);
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
        } finally {
            try {
                if (databaseInputStream != null) {
                    databaseInputStream.close();
                }
            } catch (IOException e) {
                //
            }
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
     * Run asyn task.
     *
     * @param params the params
     */
    private void runAsynTask(String params[]) {
        mTask = new DownloadFileFromURL();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mTask.execute(params);
//            mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        } else {
            mTask.execute(params);
        }
    }

    /**
     * Check storage.
     *
     * @param link the link
     * @return true, if successful
     */
    private int checkStorage(String link) {
        CheckStorageAsyncTask task = new CheckStorageAsyncTask();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, link, getApplicationContext());
        } else {
            task.execute(link, getApplicationContext());
        }
        try {
            for (int i = 0; i < 20; i++) {
                Thread.sleep(500);
                if (task.result > -1) {
                    break;
                }
            }
            System.out.println("wait");
        } catch (Exception e) {
            //
        }
        return task.result;
    }

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
     * Background Async Task to download file
     */
    class DownloadFileFromURL extends AsyncTask<String, Integer, Boolean> {
        /**
         * Before starting background thread Show Progress Bar Dialog
         */

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(DaisyReaderDownloadBooks.this);
            mProgressDialog.setMessage(DaisyReaderDownloadBooks.this
                    .getString(R.string.message_downloading_file));
            mProgressDialog.setCancelable(true);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(MAX_PROGRESS);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            pushToDialogOptions(DaisyReaderDownloadBooks.this
                                    .getString(R.string.message_confirm_exit_download));
                        }
                    });
                }
            });
            mProgressDialog.show();
        }

        /**
         * Downloading file in background thread
         */
        @Override
        protected Boolean doInBackground(String... params) {
            SSLContext sslContext = null;
            try {
                TrustManager[] tm = {
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }
                };
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tm, null);
                HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String s, SSLSession sslSession) {
                        return true;
                    }
                });
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }

            int count;
            boolean result = false;
            String link = params[0];
            InputStream input = null;
            OutputStream output = null;
            try {
                java.net.URL url = new java.net.URL(link);
                URLConnection conection = url.openConnection();
                conection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:45.0) Gecko/20100101 Firefox/45.0");
                conection.setRequestProperty("Accept", "text/html");
                conection.setRequestProperty("Accept-Language", "ja");
                conection.setRequestProperty("Accept-Encoding", "deflate");
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    conection = (HttpsURLConnection) conection;
                    ((HttpsURLConnection) conection).setSSLSocketFactory(sslContext.getSocketFactory());
                    ((HttpsURLConnection) conection).setRequestMethod("GET");
                }
                conection.connect();
                Map headers = conection.getHeaderFields();
                Iterator headerIt = headers.keySet().iterator();
                String header = null;
                while (headerIt.hasNext()) {
                    String headerKey = (String) headerIt.next();
                    header += headerKey + "：" + headers.get(headerKey) + "\r\n";
                }
                long startTime = System.currentTimeMillis();
                // this will be useful so that you can show a tipical 0-100%
                // progress bar
                int lenghtOfFile = conection.getContentLength();
                // download the file
                input = new BufferedInputStream(url.openStream(), SIZE);
                // Output stream
                String splitString[] = link.split("/");
                mName = splitString[splitString.length - 1];
                if (mName.indexOf("?") > -1) {
                    mName = mName.substring(mName.indexOf("?")).replaceAll("&", "_").replaceAll("=", "") + ".zip";
                }
                String path = ("".equals(Constants.folderRoot) ? PATH : Constants.folderRoot + Constants.FOLDER_DOWNLOADED + "/"); // 20180710
                output = new FileOutputStream(path + mName);
                byte data[] = new byte[BYTE_VALUE];
                long total = 0;
                while ((count = input.read(data)) != -1) {
                    if (isCancelled()) {
                        File file = new File(path + mName);
                        if (file.delete()) {
                            Log.i("Delete", "Deleted temporary file, " + mName);
                        } else {
                            Log.i("Delete", "Cannot delete temporary file, " + mName);
                        }
                        break;
                    } else {
                        total += count;
                        // publishing the progress....
                        // After this onProgressUpdate will be called
                        publishProgress((int) ((total * MAX_PROGRESS) / lenghtOfFile));
                        // writing data to file
                        output.write(data, 0, count);
                    }
                }
                // Record the time taken for the download excluding local cleanup.
                long stopTime = System.currentTimeMillis();
                long elapsedTime = stopTime - startTime;
                String timeTaken = Long.toString(elapsedTime);

                // flushing output
                output.flush();
                // closing streams
//                output.close();
//                input.close();

                // Record the book download completed successfully 
                HashMap<String, String> results = new HashMap<String, String>();
                results.put("URL", link);
                results.put("FileSize", Integer.toString(count));
                results.put("DurationIn(ms)", timeTaken);
//                Countly.sharedInstance().recordEvent(Constants.RECORD_BOOK_DOWNLOAD_COMPLETED, results, 1);
//                Bundle bundle = new Bundle();
//                bundle.putString(FirebaseAnalytics.Param.ITEM_ID, link);
//                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, mName);
//                bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "zip");
//                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
                result = true;
            } catch (Exception e) {
                HashMap<String, String> results = new HashMap<String, String>();
                results.put("URL", link);
                results.put("Exception", e.getMessage());
//            	Countly.sharedInstance().recordEvent(Constants.RECORD_BOOK_DOWNLOAD_FAILED, results, 1);
//                Bundle bundle = new Bundle();
//                bundle.putString(FirebaseAnalytics.Param.ITEM_ID, link);
//                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, mName);
//                bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, e.getMessage());
//                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
                result = false;
                mTask.cancel(true);
                mProgressDialog.dismiss();
                // show error message if an error occurs while connecting to the
                // resource
                final PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        IntentController intent = new IntentController(
                                DaisyReaderDownloadBooks.this);
                        ex.showDialogDowloadException(intent);
                    }
                });
            } finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException e) {
                    //
                }
                try {
                    if (output != null) {
                        output.close();
                    }
                } catch (IOException e) {
                    //
                }
            }
            return result;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            mProgressDialog.setProgress(values[0]);
        }

        /**
         * After completing background task Dismiss the progress dialogs
         **/
        @Override
        protected void onPostExecute(Boolean result) {
            if (alertDialog != null) {
                alertDialog.dismiss();
            }
            mProgressDialog.dismiss();
            try {
                if (result) {
                    DaisyBook daisyBook = new DaisyBook();
                    String path = ("".equals(Constants.folderRoot) ? PATH : Constants.folderRoot + Constants.FOLDER_DOWNLOADED + "/") + mName; // 20180710
//                    String path = PATH + mName;
                    if (DaisyBookUtil.findDaisyFormat(path) == Constants.DAISY_202_FORMAT) {
                        daisyBook = DaisyBookUtil.getDaisy202Book(path, getApplicationContext());
                    } else {
                        daisyBook = DaisyBookUtil.getDaisy30Book(path, getApplicationContext());
                    }

                    DaisyBookInfo daisyBookInfo = new DaisyBookInfo();
                    daisyBookInfo.setAuthor(daisyBook.getAuthor());
                    Date date = daisyBook.getDate();
                    String sDate = formatDateOrReturnEmptyString(date);
                    daisyBookInfo.setDate(sDate);
                    daisyBookInfo.setPath(path);
                    daisyBookInfo.setPublisher(daisyBook.getPublisher());
                    daisyBookInfo.setSort(mDaisyBook.getSort());
                    daisyBookInfo.setTitle(daisyBook.getTitle());
                    if (mSql.addDaisyBook(daisyBookInfo, Constants.TYPE_DOWNLOADED_BOOK)) {
                        Intent intent = new Intent(DaisyReaderDownloadBooks.this,
                                DaisyReaderDownloadedBooks.class);
                        DaisyReaderDownloadBooks.this.startActivity(intent);
                    }
                }
            } catch (Exception e) {
                PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
                ex.writeLogException();
            }
        }
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
        if (mTask != null) {
            mTask.cancel(false);
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleSearchBook();
    }

    @Override
    protected void onDestroy() {
//        try {
//            if (mTts != null) {
//                mTts.shutdown();
//            }
//        } catch (Exception e) {
//            PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
//            ex.writeLogException();
//        }
        super.onDestroy();
    }

    /**
     * Show a dialog to confirm exit download.
     *
     * @param message
     */
    private void pushToDialogOptions(String message) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(DaisyReaderDownloadBooks.this);
        // Setting Dialog Title
        alertDialogBuilder.setTitle(R.string.error_title);
        // Setting Dialog Message
        alertDialogBuilder.setMessage(message);
//        alertDialogBuilder.setCanceledOnTouchOutside(false);
        alertDialogBuilder.setCancelable(false);
        // Setting Icon to Dialog
        alertDialogBuilder.setIcon(R.drawable.error);
        alertDialogBuilder.setNegativeButton(DaisyReaderDownloadBooks.this.getString(R.string.no),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mProgressDialog.show();
                    }
                });
        alertDialogBuilder.setPositiveButton(DaisyReaderDownloadBooks.this.getString(R.string.yes),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mTask.cancel(true);
                    }
                });
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
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
                            info.setPath(mediaproviderUri);
                            info.setId(Long.valueOf(id).toString());
                            mSql.addDaisyBook(info, Constants.TYPE_DOWNLOADED_BOOK);

                            Intent downloaded = new Intent(context, DaisyReaderDownloadedBooks.class);
                            context.startActivity(downloaded);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(broadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }

    }

    private void downloadABook(int position) {
        boolean isConnected = DaisyBookUtil.getConnectivityStatus(DaisyReaderDownloadBooks.this) != Constants.CONNECT_TYPE_NOT_CONNECTED;
        IntentController intent = new IntentController(DaisyReaderDownloadBooks.this);
        if (isConnected) {
            if (checkFolderIsExist()) {
                mDaisyBook = mlistDaisyBook.get(position);
                String link = mDaisyBook.getPath();

                if (checkStorage(link) != 0) {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                        String params[] = {link};
                        runAsynTask(params);
                    } else {
                        doDownloadManager(mDaisyBook);
                    }
                } else {
                    intent.pushToDialog(DaisyReaderDownloadBooks.this
                                    .getString(R.string.error_not_enough_space),
                            DaisyReaderDownloadBooks.this.getString(R.string.error_title),
                            R.raw.error, false, false, null);
                }
            }
        } else {
            intent.pushToDialog(
                    DaisyReaderDownloadBooks.this.getString(R.string.error_connect_internet),
                    DaisyReaderDownloadBooks.this.getString(R.string.error_title), R.raw.error,
                    false, false, null);
        }
    }

    private static class CheckStorageAsyncTask extends AsyncTask<Object, Void, Integer> {
        public int result = -1;

        @Override
        protected Integer doInBackground(Object... params) {
            Context context = null;

            SSLContext sslContext = null;
            try {
                TrustManager[] tm = {
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }
                };
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tm, null);
                HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String s, SSLSession sslSession) {
                        return true;
                    }
                });
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                String link = (String) params[0];
                context = (Context) params[1];
                java.net.URL url = new java.net.URL(link);
                URLConnection conection = url.openConnection();
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    conection = (HttpsURLConnection) conection;
                    ((HttpsURLConnection) conection).setSSLSocketFactory(sslContext.getSocketFactory());
                    ((HttpsURLConnection) conection).setRequestMethod("GET");
                }

                conection.connect();
                int lenghtOfFile = conection.getContentLength();

                System.out.println("lenghtOfFile");
                System.out.println(lenghtOfFile);

//            StatFs statFs = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());
//            StatFs statFs = new StatFs(Constants.folderRoot); // 20180710
                StatFs statFs = new StatFs(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
                long blockSize = statFs.getBlockSize();
                long freeSize = statFs.getFreeBlocks() * blockSize;

                System.out.println("freeSize");
                System.out.println(freeSize);
                if (freeSize > lenghtOfFile) {
                    result = 1;
                }
            } catch (Exception e) {
                result = 2;
                PrivateException ex = new PrivateException(e, context);
                ex.writeLogException();
            }

            return null;
        }
    }

}
