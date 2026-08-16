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
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.androiddaisyreader.adapter.DaisyBookPagingAdapter;
import org.androiddaisyreader.base.DaisyEbookReaderBaseActivity;
import org.androiddaisyreader.aozoraconvert.AozoraClient;
import org.androiddaisyreader.aozoraconvert.AozoraEpubConverter;
import org.androiddaisyreader.chattyconvert.ChattyLibraryClient;
import org.androiddaisyreader.machiiroconvert.MachiiroClient;
import org.androiddaisyreader.machiiroconvert.model.Municipality;
import org.androiddaisyreader.mykohoconvert.MyKohoClient;
import org.androiddaisyreader.voicepageconvert.VoicePageClient;
import org.androiddaisyreader.voicepageconvert.VoiceEpubConverter;
import org.androiddaisyreader.metadata.MetaDataHandler;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.model.ZippedBookInfo;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.CacheHelper;
import org.androiddaisyreader.utils.ChattyLibPreferences;
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
import android.widget.EditText;
import android.widget.Toast;

import com.github.naofum.androiddaisyreader.R;

/**
 * The Class DaisyReaderDownloadBooks.
 */
@SuppressLint("NewApi")
public class DaisyReaderDownloadBooks extends DaisyEbookReaderBaseActivity {

    private String mLink;
    private SQLiteDaisyBookHelper mSql;
    private String mName;
    private DaisyBookPagingAdapter mPagingAdapter;
    private DaisyBookInfo mDaisyBook;
    private EditText mTextSearch;
    public static final String PATH = Environment.getExternalStorageDirectory().toString()
            + Constants.FOLDER_DOWNLOADED + "/";

    private DownloadManager downloadManager;
    private BroadcastReceiver mDownloadReceiver;

    // Paging
    private final ExecutorService pagingExecutor = Executors.newSingleThreadExecutor();
    private String currentSearchQuery = "";
    private static final int PAGE_SIZE = 100;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private boolean isLoadingMore = false;
    private boolean hasMoreData = true;
    private int currentOffset = 0;

    // for ftp
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private Future<?> downloadFuture;
    private final AtomicBoolean downloadCancelled = new AtomicBoolean(false);

    private boolean isChattyLib = false;
    private boolean isAozora = false;
    private boolean isKoho = false;
    private boolean isMachiiro = false;
    private boolean isMyKoho = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_books);

        mTextSearch = (EditText) findViewById(R.id.edit_text_search);
        mLink = getIntent().getStringExtra(Constants.LINK_WEBSITE);
        String websiteName = getIntent().getStringExtra(Constants.NAME_WEBSITE);

        mSql = SQLiteDaisyBookHelper.getInstance(DaisyReaderDownloadBooks.this);

        // ChattyLib の場合は createDownloadData() 不要（Worker で同期済み）
        isChattyLib = Constants.CHATTYLIB_SITE_URL.equals(mLink);
        // 青空文庫の場合も createDownloadData() 不要（Worker で同期済み）
        isAozora = Constants.AOZORA_SITE_URL.equals(mLink);
        // 広報紙（マチイロ、MY広報紙）の場合も createDownloadData() 不要（Worker で同期済み）
        isMachiiro = Constants.MACHIIRO_SITE_URL.equals(mLink);
        isMyKoho = Constants.MYKOHO_SITE_URL.equals(mLink);
        isKoho = Constants.KOHO_SITE_URL.equals(mLink) || isMachiiro || isMyKoho;
        if (!isChattyLib && !isAozora && !isKoho) {
            createDownloadData();
        }

        // RecyclerView + Paging 3 セットアップ
        androidx.recyclerview.widget.RecyclerView recyclerView = findViewById(R.id.recycler_view_download_books);
        androidx.recyclerview.widget.LinearLayoutManager layoutManager = new androidx.recyclerview.widget.LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        mPagingAdapter = new DaisyBookPagingAdapter((book, position) -> {
            mDaisyBook = book;
            boolean isDoubleTap = handleClickItem(position);
            if (isDoubleTap) {
                downloadABook(position);
            } else {
                speakTextOnHandler(book.getTitle());
            }
        });
        recyclerView.setAdapter(mPagingAdapter);

        // 無限スクロール: リスト末尾付近でページ追加読み込み
        recyclerView.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(androidx.recyclerview.widget.RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0) return; // 上方向スクロールは無視
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                if (lastVisibleItem >= totalItemCount - 10) {
                    loadMoreData();
                }
            }
        });

        // 初回データロード
        loadPagingData();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(websiteName != null && !websiteName.isEmpty() ? websiteName : "");
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
            List<DaisyBookInfo> books = new ArrayList<>();
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
                    books.add(daisyBook);
                }
            }
            // トランザクション内で一括削除＋挿入
            mSql.replaceAllDaisyBooks(books, Constants.TYPE_DOWNLOAD_BOOK);
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyReaderDownloadBooks.this);
            ex.writeLogException();
        }
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
                currentSearchQuery = s.toString();
                // 300ms debounce: 入力中の無駄なクエリを抑制
                searchHandler.removeCallbacksAndMessages(null);
                searchHandler.postDelayed(() -> loadPagingData(), 300);
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
     * データをロードして RecyclerView に表示する（初回/検索変更時）。
     */
    private void loadPagingData() {
        final String bookType;
        if (isChattyLib) {
            bookType = Constants.TYPE_CHATTYLIB_BOOK;
        } else if (isAozora) {
            bookType = Constants.TYPE_AOZORA_BOOK;
        } else if (isKoho) {
            bookType = Constants.TYPE_KOHO_BOOK;
        } else {
            bookType = Constants.TYPE_DOWNLOAD_BOOK;
        }

        // ページング状態リセット
        currentOffset = 0;
        hasMoreData = true;
        isLoadingMore = false;

        pagingExecutor.execute(() -> {
            java.util.List<DaisyBookInfo> allBooks = mSql.getDaisyBookPage(
                    bookType, currentSearchQuery, PAGE_SIZE, 0);
            currentOffset = allBooks.size();
            hasMoreData = allBooks.size() >= PAGE_SIZE;
            runOnUiThread(() -> {
                mPagingAdapter.submitList(allBooks);
            });
        });
    }

    /**
     * スクロール時に追加データをロードする。
     */
    private void loadMoreData() {
        if (isLoadingMore || !hasMoreData) return;
        isLoadingMore = true;

        final String bookType;
        if (isChattyLib) {
            bookType = Constants.TYPE_CHATTYLIB_BOOK;
        } else if (isAozora) {
            bookType = Constants.TYPE_AOZORA_BOOK;
        } else if (isKoho) {
            bookType = Constants.TYPE_KOHO_BOOK;
        } else {
            bookType = Constants.TYPE_DOWNLOAD_BOOK;
        }

        pagingExecutor.execute(() -> {
            java.util.List<DaisyBookInfo> moreBooks = mSql.getDaisyBookPage(
                    bookType, currentSearchQuery, PAGE_SIZE, currentOffset);
            currentOffset += moreBooks.size();
            hasMoreData = moreBooks.size() >= PAGE_SIZE;
            runOnUiThread(() -> {
                if (!moreBooks.isEmpty()) {
                    java.util.List<DaisyBookInfo> currentList = new java.util.ArrayList<>(mPagingAdapter.getCurrentList());
                    currentList.addAll(moreBooks);
                    mPagingAdapter.submitList(currentList);
                }
                isLoadingMore = false;
            });
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
    protected void onResume() {
        super.onResume();
        handleSearchBook();
    }

    @Override
    protected void onDestroy() {
        if (mDownloadReceiver != null) {
            try {
                unregisterReceiver(mDownloadReceiver);
            } catch (IllegalArgumentException e) {
                // not registered
            }
            mDownloadReceiver = null;
        }
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

        mDownloadReceiver = new BroadcastReceiver() {
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
                            mSql.addOrReplaceDaisyBook(info, Constants.TYPE_DOWNLOADED_BOOK);
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
        registerReceiver(mDownloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_NOT_EXPORTED);

        Toast.makeText(DaisyReaderDownloadBooks.this,
                this.getString(R.string.message_downloading_file), Toast.LENGTH_SHORT).show();
        speakText(this.getString(R.string.message_downloading_file));
    }

    private void downloadABook(int position) {
        boolean isConnected = DaisyBookUtil.getConnectivityStatus(DaisyReaderDownloadBooks.this) != Constants.CONNECT_TYPE_NOT_CONNECTED;
        IntentController intent = new IntentController(DaisyReaderDownloadBooks.this);
        if (!isConnected) {
            intent.pushToDialog(
                    DaisyReaderDownloadBooks.this.getString(R.string.error_connect_internet),
                    DaisyReaderDownloadBooks.this.getString(R.string.error_title), R.raw.error,
                    false, false, null);
            return;
        }

        if (isChattyLib) {
            downloadFromChattyLib();
            return;
        }

        if (isAozora) {
            downloadFromAozora();
            return;
        }

        // 広報紙: 選択された図書のURIスキームで判定
        if (mDaisyBook != null && mDaisyBook.getPath() != null) {
            if (mDaisyBook.getPath().startsWith("mykoho://")) {
                downloadFromMyKoho();
                return;
            }
            if (mDaisyBook.getPath().startsWith("machiiro://")) {
                downloadFromMachiiro();
                return;
            }
            if (mDaisyBook.getPath().startsWith("voicepage://")) {
                downloadFromVoicePage();
                return;
            }
            if (isKoho && (mDaisyBook.getPath().startsWith("https://") || mDaisyBook.getPath().startsWith("http://"))) {
                downloadFromDirectUrl();
                return;
            }
        }

        if (checkFolderIsExist()) {
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
    }

    /**
     * ChattyLib からバックグラウンドで図書をダウンロードする。
     */
    private void downloadFromChattyLib() {
        if (mDaisyBook == null) return;
        String bookPath = mDaisyBook.getPath();
        if (bookPath == null || !bookPath.startsWith("chattylib://")) return;

        int bookId;
        try {
            bookId = Integer.parseInt(bookPath.replace("chattylib://", ""));
        } catch (NumberFormatException e) {
            return;
        }

        Toast.makeText(this, getString(R.string.message_downloading_file), Toast.LENGTH_SHORT).show();
        speakText(getString(R.string.message_downloading_file));

        pagingExecutor.execute(() -> {
            try {
                String loginId = ChattyLibPreferences.getLoginId(getApplicationContext());
                String password = ChattyLibPreferences.getPassword(getApplicationContext());

                try (ChattyLibraryClient client = new ChattyLibraryClient(loginId, password)) {
                    client.login();
                    // 一時ディレクトリでダウンロード＋EPUB変換
                    java.io.File tempDir = new java.io.File(getCacheDir(), "chattylib");
                    if (!tempDir.exists()) tempDir.mkdirs();
                    java.io.File epubFile = client.download(bookId, tempDir);

                    // Downloads フォルダーに保存 + MediaStore 登録
                    String fileName = mDaisyBook.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_") + ".epub";
                    android.net.Uri savedUri = saveToDownloadsAndMediaStore(epubFile, fileName);
                    String savedPath = (savedUri != null) ? savedUri.toString() : epubFile.getAbsolutePath();

                    // DB に登録
                    DaisyBookInfo updatedInfo = new DaisyBookInfo(
                            mDaisyBook.getId(), mDaisyBook.getTitle(),
                            savedPath,
                            mDaisyBook.getAuthor(), mDaisyBook.getPublisher(),
                            mDaisyBook.getDate(), mDaisyBook.getSort());
                    mSql.addOrReplaceDaisyBook(updatedInfo, Constants.TYPE_DOWNLOADED_BOOK);
                    DaisyBookUtil.addRecentBookToSQLite(updatedInfo,
                            Constants.NUMBER_OF_RECENTBOOK_DEFAULT, mSql);

                    // 一時ファイル削除
                    epubFile.delete();

                    // ダウンロード完了通知（タップで書籍を開く）
                    showDownloadCompleteNotification(fileName, savedUri);

                    runOnUiThread(() -> {
                        Toast.makeText(DaisyReaderDownloadBooks.this,
                                getString(R.string.message_download_complete), Toast.LENGTH_SHORT).show();
                        Intent downloaded = new Intent(DaisyReaderDownloadBooks.this,
                                DaisyReaderDownloadedBooks.class);
                        startActivity(downloaded);
                    });
                }
            } catch (Exception e) {
                Log.e("ChattyLibDownload", "Download failed", e);
                org.androiddaisyreader.utils.LogFile.e("ChattyLibDownload", "Download failed", e);
                String errorMessage = (e.getMessage() != null && e.getMessage().contains("読むことができません"))
                        ? getString(R.string.chattylib_error_not_readable)
                        : getString(R.string.error_cannot_dowload);
                final String finalMessage = errorMessage;
                runOnUiThread(() -> {
                    speakText(finalMessage);
                    showErrorWithLogSend(finalMessage);
                });
            }
        });
    }

    /**
     * 青空文庫からバックグラウンドで図書をダウンロードしてEPUBに変換する。
     */
    private void downloadFromAozora() {
        if (mDaisyBook == null) return;
        String bookPath = mDaisyBook.getPath();
        if (bookPath == null || !bookPath.startsWith("aozora://")) return;

        // "aozora://{authorId}/{workId}" からIDを抽出
        String[] parts = bookPath.replace("aozora://", "").split("/");
        if (parts.length < 2) return;

        int authorId;
        int workId;
        try {
            authorId = Integer.parseInt(parts[0]);
            workId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }

        Toast.makeText(this, getString(R.string.message_downloading_file), Toast.LENGTH_SHORT).show();
        speakText(getString(R.string.message_downloading_file));

        final String bookTitle = mDaisyBook.getTitle();
        final String bookAuthor = mDaisyBook.getAuthor();

        pagingExecutor.execute(() -> {
            try (AozoraClient client = new AozoraClient()) {
                // 一時ディレクトリでダウンロード
                java.io.File tempDir = new java.io.File(getCacheDir(), "aozora");
                if (!tempDir.exists()) tempDir.mkdirs();
                java.io.File bookDir = client.downloadBook(authorId, workId, tempDir);

                // EPUB変換
                String safeTitle = bookTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
                java.io.File epubFile = new java.io.File(tempDir, safeTitle + ".epub");
                AozoraEpubConverter converter = new AozoraEpubConverter();
                converter.convert(bookDir, bookTitle, bookAuthor, epubFile);

                // Downloads フォルダーに保存 + MediaStore 登録
                String fileName = safeTitle + ".epub";
                android.net.Uri savedUri = saveToDownloadsAndMediaStore(epubFile, fileName);
                String savedPath = (savedUri != null) ? savedUri.toString() : epubFile.getAbsolutePath();

                // DB に登録
                DaisyBookInfo updatedInfo = new DaisyBookInfo(
                        mDaisyBook.getId(), bookTitle,
                        savedPath,
                        bookAuthor, mDaisyBook.getPublisher(),
                        mDaisyBook.getDate(), mDaisyBook.getSort());
                mSql.addOrReplaceDaisyBook(updatedInfo, Constants.TYPE_DOWNLOADED_BOOK);
                DaisyBookUtil.addRecentBookToSQLite(updatedInfo,
                        Constants.NUMBER_OF_RECENTBOOK_DEFAULT, mSql);

                // 一時ファイル削除
                epubFile.delete();
                deleteDirectory(bookDir);

                // ダウンロード完了通知
                showDownloadCompleteNotification(fileName, savedUri);

                runOnUiThread(() -> {
                    Toast.makeText(DaisyReaderDownloadBooks.this,
                            getString(R.string.message_download_complete), Toast.LENGTH_SHORT).show();
                    Intent downloaded = new Intent(DaisyReaderDownloadBooks.this,
                            DaisyReaderDownloadedBooks.class);
                    startActivity(downloaded);
                });
            } catch (Exception e) {
                Log.e("AozoraDownload", "Download failed", e);
                org.androiddaisyreader.utils.LogFile.e("AozoraDownload", "Download failed", e);
                runOnUiThread(() -> {
                    speakText(getString(R.string.error_cannot_dowload));
                    showErrorWithLogSend(R.string.error_cannot_dowload);
                });
            }
        });
    }

    /**
     * マチイロからバックグラウンドで図書をダウンロードしてEPUBに変換する。
     */
    private void downloadFromMachiiro() {
        if (mDaisyBook == null) return;
        String bookPath = mDaisyBook.getPath();
        if (bookPath == null || !bookPath.startsWith("machiiro://")) return;

        // "machiiro://{unicipalityId}" からIDを抽出
        String[] parts = bookPath.replace("machiiro://", "").split("/");
        if (parts.length < 1) return;

        String url = "https://machiiro.town/lp/" + parts[0];
        Municipality municipality = new Municipality(parts[0], mDaisyBook.getTitle(), url);
//        int workId;
//        try {
//            workId = Integer.parseInt(parts[0]);
//        } catch (NumberFormatException e) {
//            return;
//        }

        Toast.makeText(this, getString(R.string.message_downloading_file), Toast.LENGTH_SHORT).show();
        speakText(getString(R.string.message_downloading_file));

        final String bookTitle = mDaisyBook.getTitle();
        final String bookAuthor = mDaisyBook.getAuthor();

        pagingExecutor.execute(() -> {
            try (MachiiroClient client = new MachiiroClient()) {
                // 一時ディレクトリでダウンロード
                java.io.File tempDir = new java.io.File(getCacheDir(), "machiiro");
                if (!tempDir.exists()) tempDir.mkdirs();
                java.io.File epubFile = client.downloadMunicipality(municipality, tempDir);

                // Downloads フォルダーに保存 + MediaStore 登録
                String fileName = bookTitle + ".epub";
                android.net.Uri savedUri = saveToDownloadsAndMediaStore(epubFile, fileName);
                String savedPath = (savedUri != null) ? savedUri.toString() : epubFile.getAbsolutePath();

                // DB に登録
                DaisyBookInfo updatedInfo = new DaisyBookInfo(
                        mDaisyBook.getId(), bookTitle,
                        savedPath,
                        bookAuthor, resolvePublisher(Constants.MACHIIRO_SITE_NAME),
                        mDaisyBook.getDate(), mDaisyBook.getSort());
                mSql.addOrReplaceDaisyBook(updatedInfo, Constants.TYPE_DOWNLOADED_BOOK);
                DaisyBookUtil.addRecentBookToSQLite(updatedInfo,
                        Constants.NUMBER_OF_RECENTBOOK_DEFAULT, mSql);

                // 一時ファイル削除
                epubFile.delete();
                deleteDirectory(tempDir);

                // ダウンロード完了通知
                showDownloadCompleteNotification(fileName, savedUri);

                runOnUiThread(() -> {
                    Toast.makeText(DaisyReaderDownloadBooks.this,
                            getString(R.string.message_download_complete), Toast.LENGTH_SHORT).show();
                    Intent downloaded = new Intent(DaisyReaderDownloadBooks.this,
                            DaisyReaderDownloadedBooks.class);
                    startActivity(downloaded);
                });
            } catch (Exception e) {
                Log.e("MachiiroDownload", "Download failed", e);
                org.androiddaisyreader.utils.LogFile.e("MachiiroDownload", "Download failed", e);
                runOnUiThread(() -> {
                    speakText(getString(R.string.error_cannot_dowload));
                    showErrorWithLogSend(R.string.error_cannot_dowload);
                });
            }
        });
    }

    /**
     * MY広報紙からバックグラウンドでEPUBを生成する。
     * URIフォーマット: mykoho://{地方公共団体コード}/{マイ広報紙自治体コード}
     * PDF（pdfbox）と音声（jlayer）からEPUB3マルチメディアオーバーレイを作成する。
     */
    private void downloadFromMyKoho() {
        if (mDaisyBook == null) return;
        String bookPath = mDaisyBook.getPath();
        if (bookPath == null || !bookPath.startsWith("mykoho://")) return;

        // "mykoho://{lgCode}/{myKohoCode}" からコードを抽出
        String[] parts = bookPath.replace("mykoho://", "").split("/");
        if (parts.length < 2) return;

        final String lgCode = parts[0];
        final String myKohoCode = parts[1];

        Toast.makeText(this, getString(R.string.message_downloading_file), Toast.LENGTH_SHORT).show();
        speakText(getString(R.string.message_downloading_file));

        final String bookTitle = mDaisyBook.getTitle();
        final String bookAuthor = mDaisyBook.getAuthor();

        pagingExecutor.execute(() -> {
            try (MyKohoClient client = new MyKohoClient()) {
                // 一時ディレクトリでダウンロード＋EPUB生成
                java.io.File tempDir = new java.io.File(getCacheDir(), "mykoho");
                if (!tempDir.exists()) tempDir.mkdirs();
                String safeTitle = bookTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
                String fileName = safeTitle + ".epub";

                java.io.File epubFile = client.downloadEpub(lgCode, myKohoCode, tempDir);

                // Downloads フォルダーに保存 + MediaStore 登録
                android.net.Uri savedUri = saveToDownloadsAndMediaStore(epubFile, fileName);
                String savedPath = (savedUri != null) ? savedUri.toString() : epubFile.getAbsolutePath();

                // DB に登録
                DaisyBookInfo updatedInfo = new DaisyBookInfo(
                        mDaisyBook.getId(), bookTitle,
                        savedPath,
                        bookAuthor, resolvePublisher(Constants.MYKOHO_SITE_NAME),
                        mDaisyBook.getDate(), mDaisyBook.getSort());
                mSql.addOrReplaceDaisyBook(updatedInfo, Constants.TYPE_DOWNLOADED_BOOK);
                DaisyBookUtil.addRecentBookToSQLite(updatedInfo,
                        Constants.NUMBER_OF_RECENTBOOK_DEFAULT, mSql);

                // 一時ファイル削除
                deleteDirectory(tempDir);

                // ダウンロード完了通知
                showDownloadCompleteNotification(fileName, savedUri);

                runOnUiThread(() -> {
                    Toast.makeText(DaisyReaderDownloadBooks.this,
                            getString(R.string.message_download_complete), Toast.LENGTH_SHORT).show();
                    Intent downloaded = new Intent(DaisyReaderDownloadBooks.this,
                            DaisyReaderDownloadedBooks.class);
                    startActivity(downloaded);
                });
            } catch (Exception e) {
                Log.e("MyKohoDownload", "Download failed", e);
                org.androiddaisyreader.utils.LogFile.e("MyKohoDownload", "Download failed", e);
                runOnUiThread(() -> {
                    speakText(getString(R.string.error_cannot_dowload));
                    showErrorWithLogSend(R.string.error_cannot_dowload);
                });
            }
        });
    }

    /**
     * 音声ページ（複数リンク → 最新号 → mp3一覧）からバックグラウンドで EPUB3 メディアオーバーレイを生成する。
     * URIフォーマット: voicepage://{ホスト}/{パス}
     */
    private void downloadFromVoicePage() {
        if (mDaisyBook == null) return;
        String bookPath = mDaisyBook.getPath();
        if (bookPath == null || !bookPath.startsWith("voicepage://")) return;

        final String url = "https://" + bookPath.substring("voicepage://".length());
        Log.i("VoicePageDownload", "voicepage URL=" + url);

        Toast.makeText(this, getString(R.string.message_downloading_file), Toast.LENGTH_SHORT).show();
        speakText(getString(R.string.message_downloading_file));

        final String bookTitle = mDaisyBook.getTitle();
        final String bookAuthor = mDaisyBook.getAuthor();

        pagingExecutor.execute(() -> {
            try (VoicePageClient client = new VoicePageClient()) {
                java.io.File tempDir = new java.io.File(getCacheDir(), "voicepage");
                if (!tempDir.exists()) tempDir.mkdirs();

                java.io.File epubFile = client.download(url, bookTitle, tempDir);

                String safeTitle = bookTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
                String fileName = safeTitle + ".epub";
                saveAndRegisterDownloadedFile(epubFile, fileName, bookTitle, bookAuthor);

                deleteDirectory(tempDir);
            } catch (Exception e) {
                Log.e("VoicePageDownload", "Download failed", e);
                org.androiddaisyreader.utils.LogFile.e("VoicePageDownload", "Download failed", e);
                runOnUiThread(() -> {
                    speakText(getString(R.string.error_cannot_dowload));
                    showErrorWithLogSend(R.string.error_cannot_dowload);
                });
            }
        });
    }

    /**
     * https:// URLから直接ダウンロードする（広報紙向け）。
     * URLがzip/epubファイルを直接指していればそのまま保存し、
     * HTMLページの場合は最初に見つかった.zipまたは.epubリンクを辿ってダウンロードする。
     */
    private void downloadFromDirectUrl() {
        if (mDaisyBook == null) return;
        String bookPath = mDaisyBook.getPath();
        if (bookPath == null) return;

        Toast.makeText(this, getString(R.string.message_downloading_file), Toast.LENGTH_SHORT).show();
        speakText(getString(R.string.message_downloading_file));

        final String bookTitle = mDaisyBook.getTitle();
        final String bookAuthor = mDaisyBook.getAuthor();

        pagingExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // URLにアクセスしてContent-Typeを確認
                URL url = new URL(bookPath);
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    throw new java.io.IOException("HTTP error: " + responseCode);
                }

                String contentType = connection.getContentType();
                String finalUrl = connection.getURL().toString(); // リダイレクト後のURL
                Log.i("DirectUrlDownload", "URL=" + bookPath + " HTTP=" + responseCode
                        + " Content-Type=" + contentType + " finalUrl=" + finalUrl);

                // zip/epub ファイルを直接ダウンロードするケース
                if (isDownloadableContentType(contentType) || isDownloadableUrl(finalUrl)) {
                    java.io.File tempDir = new java.io.File(getCacheDir(), "koho_direct");
                    if (!tempDir.exists()) tempDir.mkdirs();
                    String safeTitle = bookTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
                    String ext = guessExtension(contentType, finalUrl);
                    String fileName = safeTitle + ext;
                    java.io.File downloadedFile = new java.io.File(tempDir, fileName);

                    try (InputStream in = connection.getInputStream();
                         java.io.FileOutputStream out = new java.io.FileOutputStream(downloadedFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    saveAndRegisterDownloadedFile(downloadedFile, fileName, bookTitle, bookAuthor);
                    downloadedFile.delete();
                    deleteDirectory(tempDir);
                } else {
                    // HTMLの場合 → .zip / .epub リンクを探す
                    String html;
                    try (InputStream in = connection.getInputStream();
                         java.io.InputStreamReader reader = new java.io.InputStreamReader(in, "UTF-8");
                         java.io.BufferedReader buffered = new java.io.BufferedReader(reader)) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = buffered.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                        html = sb.toString();
                    }

                    String downloadLink = findFirstDownloadableLink(html, finalUrl);
                    if (downloadLink == null) {
                        // .zip/.epub が無ければ、最初の .mp3 を1つダウンロードしてEPUB化する
                        String mp3Link = findFirstMp3Link(html, finalUrl);
                        if (mp3Link == null) {
                            Log.w("DirectUrlDownload",
                                    "ダウンロード可能なリンクが見つかりません(zip/epub/mp3): " + finalUrl);
                            org.androiddaisyreader.utils.LogFile.w("DirectUrlDownload",
                                    "ダウンロード可能なリンクが見つかりません(zip/epub/mp3): " + finalUrl);
                            runOnUiThread(() -> {
                                speakText(getString(R.string.error_cannot_dowload));
                                showErrorWithLogSend(R.string.error_cannot_dowload);
                            });
                            return;
                        }
                        Log.i("DirectUrlDownload", "mp3リンクを検出: " + mp3Link);
                        downloadMp3AndCreateEpub(mp3Link, bookTitle, bookAuthor);
                        return;
                    }
                    Log.i("DirectUrlDownload", "zip/epubリンクを検出: " + downloadLink);

                    // 見つかったリンクをダウンロード
                    java.io.File tempDir = new java.io.File(getCacheDir(), "koho_direct");
                    if (!tempDir.exists()) tempDir.mkdirs();
                    String safeTitle = bookTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
                    String ext = guessExtensionFromUrl(downloadLink);
                    String fileName = safeTitle + ext;
                    java.io.File downloadedFile = new java.io.File(tempDir, fileName);

                    downloadUrlToFile(downloadLink, downloadedFile);

                    saveAndRegisterDownloadedFile(downloadedFile, fileName, bookTitle, bookAuthor);
                    downloadedFile.delete();
                    deleteDirectory(tempDir);
                }
            } catch (Exception e) {
                Log.e("DirectUrlDownload", "Download failed", e);
                org.androiddaisyreader.utils.LogFile.e("DirectUrlDownload", "Download failed", e);
                runOnUiThread(() -> {
                    speakText(getString(R.string.error_cannot_dowload));
                    showErrorWithLogSend(R.string.error_cannot_dowload);
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * ダウンロードしたファイルをDownloadsに保存しDB登録する共通処理。
     */
    private void saveAndRegisterDownloadedFile(java.io.File file, String fileName,
                                               String bookTitle, String bookAuthor) {
        String mimeType = fileName.endsWith(".epub") ? "application/epub+zip" : "application/zip";
        android.net.Uri savedUri = saveToDownloadsAndMediaStoreGeneric(file, fileName, mimeType);
        String savedPath = (savedUri != null) ? savedUri.toString() : file.getAbsolutePath();

        DaisyBookInfo updatedInfo = new DaisyBookInfo(
                mDaisyBook.getId(), bookTitle,
                savedPath,
                bookAuthor, resolvePublisher(mDaisyBook.getTitle()),
                mDaisyBook.getDate(), mDaisyBook.getSort());
        mSql.addOrReplaceDaisyBook(updatedInfo, Constants.TYPE_DOWNLOADED_BOOK);
        DaisyBookUtil.addRecentBookToSQLite(updatedInfo,
                Constants.NUMBER_OF_RECENTBOOK_DEFAULT, mSql);

        showDownloadCompleteNotification(fileName, savedUri);

        runOnUiThread(() -> {
            Toast.makeText(DaisyReaderDownloadBooks.this,
                    getString(R.string.message_download_complete), Toast.LENGTH_SHORT).show();
            Intent downloaded = new Intent(DaisyReaderDownloadBooks.this,
                    DaisyReaderDownloadedBooks.class);
            startActivity(downloaded);
        });
    }

    /**
     * 出版社を返す。BookInfoに未設定（空）の場合は、ダウンロードサイト名・自治体名で補う。
     *
     * @param fallbackSiteName 出版社が空の場合に使うダウンロードサイト名
     */
    private String resolvePublisher(String fallbackSiteName) {
        String publisher = mDaisyBook != null ? mDaisyBook.getPublisher() : null;
        if (publisher != null && !publisher.trim().isEmpty()) {
            return publisher;
        }
        return fallbackSiteName;
    }

    /**
     * Content-Typeがzip/epub等のダウンロード可能なファイルかを判定する。
     */
    private boolean isDownloadableContentType(String contentType) {
        if (contentType == null) return false;
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.contains("application/zip")
                || ct.contains("application/epub+zip")
                || ct.contains("application/octet-stream")
                || ct.contains("application/x-zip");
    }

    /**
     * URLが.zipまたは.epubで終わるかを判定する。
     */
    private boolean isDownloadableUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        int queryIdx = lower.indexOf('?');
        String path = queryIdx >= 0 ? lower.substring(0, queryIdx) : lower;
        return path.endsWith(".zip") || path.endsWith(".epub");
    }

    /**
     * Content-TypeとURLから適切な拡張子を推定する。
     */
    private String guessExtension(String contentType, String url) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("epub")) {
            return ".epub";
        }
        if (url != null) {
            String ext = guessExtensionFromUrl(url);
            if (!ext.isEmpty()) return ext;
        }
        return ".zip";
    }

    /**
     * URLから拡張子を推定する。
     */
    private String guessExtensionFromUrl(String url) {
        if (url == null) return ".zip";
        String lower = url.toLowerCase(Locale.ROOT);
        int queryIdx = lower.indexOf('?');
        String path = queryIdx >= 0 ? lower.substring(0, queryIdx) : lower;
        if (path.endsWith(".epub")) return ".epub";
        if (path.endsWith(".zip")) return ".zip";
        return ".zip";
    }

    /**
     * HTMLの中から最初の.zipまたは.epubリンクを探す。
     *
     * @param html    HTMLコンテンツ
     * @param baseUrl 相対URLの解決に使用するベースURL
     * @return 見つかったダウンロードURL、見つからない場合はnull
     */
    private String findFirstDownloadableLink(String html, String baseUrl) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "href\\s*=\\s*[\"']([^\"']*\\.(zip|epub))[\"']",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String href = matcher.group(1);
            if (href.startsWith("http://") || href.startsWith("https://")) {
                return href;
            }
            try {
                URL base = new URL(baseUrl);
                URL resolved = new URL(base, href);
                return resolved.toExternalForm();
            } catch (Exception e) {
                return href;
            }
        }
        return null;
    }

    /**
     * HTMLの中から最初の.mp3リンクを探す。
     *
     * @param html    HTMLコンテンツ
     * @param baseUrl 相対URLの解決に使用するベースURL
     * @return 見つかったmp3のURL、見つからない場合はnull
     */
    private String findFirstMp3Link(String html, String baseUrl) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "href\\s*=\\s*[\"']([^\"']*\\.mp3)[\"']",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String href = matcher.group(1);
            if (href.startsWith("http://") || href.startsWith("https://")) {
                return href;
            }
            try {
                URL base = new URL(baseUrl);
                URL resolved = new URL(base, href);
                return resolved.toExternalForm();
            } catch (Exception e) {
                return href;
            }
        }
        return null;
    }

    /**
     * 最初のmp3を1つダウンロードして、1セクションのEPUBを作成・登録する。
     */
    private void downloadMp3AndCreateEpub(String mp3Url, String bookTitle, String bookAuthor)
            throws Exception {
        java.io.File tempDir = new java.io.File(getCacheDir(), "koho_direct");
        if (!tempDir.exists()) tempDir.mkdirs();

        String safeTitle = bookTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileName = safeTitle + ".epub";
        java.io.File mp3File = new java.io.File(tempDir, "audio_0001.mp3");
        java.io.File epubFile = new java.io.File(tempDir, fileName);

        try {
            downloadUrlToFile(mp3Url, mp3File);

            List<VoiceEpubConverter.AudioItem> items = new ArrayList<>();
            items.add(new VoiceEpubConverter.AudioItem(bookTitle, mp3File));
            new VoiceEpubConverter().convert(bookTitle, items, epubFile);

            saveAndRegisterDownloadedFile(epubFile, fileName, bookTitle, bookAuthor);
        } finally {
            epubFile.delete();
            mp3File.delete();
            deleteDirectory(tempDir);
        }
    }

    /**
     * 指定URLのファイルをダウンロードしてローカルファイルに保存する。
     */
    private void downloadUrlToFile(String urlStr, java.io.File destFile) throws java.io.IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new java.io.IOException("Download failed: HTTP " + responseCode);
            }

            try (InputStream in = conn.getInputStream();
                 java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 汎用のファイル保存+MediaStore登録。
     */
    private android.net.Uri saveToDownloadsAndMediaStoreGeneric(java.io.File sourceFile,
                                                                String fileName, String mimeType) {
        android.content.ContentResolver resolver = getContentResolver();

        String selection = android.provider.MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                + android.provider.MediaStore.Downloads.RELATIVE_PATH + "=?";
        String[] selectionArgs = new String[]{
                fileName,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/DaisyReader/"
        };
        try {
            resolver.delete(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    selection, selectionArgs);
        } catch (Exception e) {
            // ignore
        }

        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/DaisyReader");

        android.net.Uri uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

        if (uri == null) {
            Log.e("DirectUrlDownload", "Failed to create MediaStore entry");
            return null;
        }

        try (java.io.InputStream in = new java.io.FileInputStream(sourceFile);
             java.io.OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) {
                resolver.delete(uri, null, null);
                return null;
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } catch (java.io.IOException e) {
            Log.e("DirectUrlDownload", "Failed to copy to Downloads", e);
            resolver.delete(uri, null, null);
            return null;
        }

        return uri;
    }

    /**
     * PDF ファイルを公開 Downloads フォルダーに保存し、MediaStore に登録する。
     *
     * @param sourceFile 保存元ファイル
     * @param fileName   保存ファイル名
     * @return MediaStore の content URI（失敗時は null）
     */
    private android.net.Uri saveToDownloadsAndMediaStorePdf(java.io.File sourceFile, String fileName) {
        android.content.ContentResolver resolver = getContentResolver();

        // 同名ファイルが既に存在する場合は削除（重複ダウンロード対応）
        String selection = android.provider.MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                + android.provider.MediaStore.Downloads.RELATIVE_PATH + "=?";
        String[] selectionArgs = new String[]{
                fileName,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/DaisyReader/"
        };
        try {
            resolver.delete(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    selection, selectionArgs);
        } catch (Exception e) {
            // 削除失敗は無視
        }

        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf");
        values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/DaisyReader");

        android.net.Uri uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

        if (uri == null) {
            Log.e("MyKohoDownload", "Failed to create MediaStore entry");
            return null;
        }

        try (java.io.InputStream in = new java.io.FileInputStream(sourceFile);
             java.io.OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) {
                resolver.delete(uri, null, null);
                return null;
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } catch (java.io.IOException e) {
            Log.e("MyKohoDownload", "Failed to copy to Downloads", e);
            resolver.delete(uri, null, null);
            return null;
        }

        Log.i("MyKohoDownload", "Saved to Downloads: " + uri);
        return uri;
    }

    /**
     * ディレクトリを再帰的に削除する。
     */
    private void deleteDirectory(java.io.File dir) {
        if (dir == null || !dir.exists()) return;
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    /**
     * EPUB ファイルを公開 Downloads フォルダーに保存し、MediaStore に登録する。
     *
     * @param sourceFile 保存元ファイル
     * @param fileName   保存ファイル名
     * @return MediaStore の content URI（失敗時は null）
     */
    private android.net.Uri saveToDownloadsAndMediaStore(java.io.File sourceFile, String fileName) {
        android.content.ContentResolver resolver = getContentResolver();

        // 同名ファイルが既に存在する場合は削除（重複ダウンロード対応）
        String selection = android.provider.MediaStore.Downloads.DISPLAY_NAME + "=? AND "
                + android.provider.MediaStore.Downloads.RELATIVE_PATH + "=?";
        String[] selectionArgs = new String[]{
                fileName,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/DaisyReader/"
        };
        try {
            resolver.delete(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    selection, selectionArgs);
        } catch (Exception e) {
            // 削除失敗は無視（初回ダウンロード時は該当なし）
        }

        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/epub+zip");
        values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/DaisyReader");

        resolver = getContentResolver();
        android.net.Uri uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

        if (uri == null) {
            Log.e("ChattyLibDownload", "Failed to create MediaStore entry");
            return null;
        }

        try (java.io.InputStream in = new java.io.FileInputStream(sourceFile);
             java.io.OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) {
                resolver.delete(uri, null, null);
                return null;
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } catch (java.io.IOException e) {
            Log.e("ChattyLibDownload", "Failed to copy to Downloads", e);
            resolver.delete(uri, null, null);
            return null;
        }

        Log.i("ChattyLibDownload", "Saved to Downloads: " + uri);
        return uri;
    }

    /**
     * ダウンロード完了の通知を表示する。タップで書籍を開く。
     */
    private void showDownloadCompleteNotification(String fileName, android.net.Uri fileUri) {
        String channelId = "chattylib_download";
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // 通知チャネル作成（Android 8.0+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId,
                    getString(R.string.chattylib_settings_title),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("ChattyLib download notifications");
            notificationManager.createNotificationChannel(channel);
        }

        // タップ時のIntent: 書籍を開く
        Intent openIntent = new Intent(this, DaisyEbookReaderModeChoiceActivity.class);
        openIntent.putExtra(Constants.DAISY_PATH, fileUri != null ? fileUri.toString() : "");
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this, bookIdFromUri(fileUri),
                openIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        // 通知作成
        androidx.core.app.NotificationCompat.Builder builder =
                new androidx.core.app.NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(getString(R.string.message_download_complete))
                        .setContentText(fileName)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        notificationManager.notify(bookIdFromUri(fileUri), builder.build());
    }

    private int bookIdFromUri(android.net.Uri uri) {
        if (uri == null) return 0;
        try {
            String lastSegment = uri.getLastPathSegment();
            return lastSegment != null ? lastSegment.hashCode() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Check storage.
     *
     */
    public static class StorageChecker {

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

        public static long getContentLengthWithFallback(String link) throws Exception {
            long contentLength = getContentLengthByHead(link);
            if (contentLength >= 0) {
                return contentLength;
            }
            // HEADが-1（タイムアウト等で接続不可）の場合のみGETにフォールバック
            return getContentLengthByGet(link);
        }

        public static long getContentLengthByHead(String link) throws Exception {
            HttpURLConnection connection = null;
            String currentUrl = link;
            try {
                for (int i = 0; i < 3; i++) {
                    URL url = new URL(currentUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestMethod("HEAD");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP      // 302
                            || responseCode == HttpURLConnection.HTTP_MOVED_PERM // 301
                            || responseCode == HttpURLConnection.HTTP_SEE_OTHER) // 303
                    {
                        String location = connection.getHeaderField("Location");
                        if (location == null || location.isEmpty()) {
                            throw new IllegalStateException("Redirect response without Location header.");
                        }

                        URL nextUrl = new URL(url, location);
                        currentUrl = nextUrl.toExternalForm();
                        connection.disconnect();
                        continue;
                    }
                    if (!isSuccess(responseCode)) {
                        // HTTPエラー（404等）の場合は例外を投げる
                        throw new IllegalStateException("HTTP error: " + responseCode);
                    }
                    return connection.getContentLengthLong();
                }
                return -1L;

            } catch (java.net.SocketTimeoutException | java.net.UnknownHostException e) {
                // タイムアウトやDNSエラーの場合はGETにフォールバックするため-1を返す
                return -1L;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        public static long getContentLengthByGet(String link) throws Exception {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            String currentUrl = link;
            try {
                for (int i = 0; i < 3; i++) {
                    URL url = new URL(currentUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP      // 302
                            || responseCode == HttpURLConnection.HTTP_MOVED_PERM // 301
                            || responseCode == HttpURLConnection.HTTP_SEE_OTHER) // 303
                    {
                        String location = connection.getHeaderField("Location");
                        if (location == null || location.isEmpty()) {
                            throw new IllegalStateException("Redirect response without Location header.");
                        }

                        URL nextUrl = new URL(url, location);
                        currentUrl = nextUrl.toExternalForm();
                        connection.disconnect();
                        continue;
                    }
                    if (!isSuccess(responseCode)) {
                        throw new IllegalStateException("HTTP error: " + responseCode);
                    } else {
                        break;
                    }
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

        static long getDownloadDirectoryFreeSize() {
            StatFs statFs = new StatFs(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                    ).getAbsolutePath()
            );
            return statFs.getAvailableBlocksLong() * statFs.getBlockSizeLong();
        }
    }
}
