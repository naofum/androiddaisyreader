package org.androiddaisyreader.apps;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.androiddaisyreader.adapter.DaisyBookPagingAdapter;
import org.androiddaisyreader.base.DaisyEbookReaderBaseActivity;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sapieconvert.SapieLibraryClient;
import org.androiddaisyreader.sapieconvert.model.Book;
import org.androiddaisyreader.sapieconvert.model.SearchResult;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;
import org.androiddaisyreader.utils.SapiePreferences;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.github.naofum.androiddaisyreader.R;

/**
 * サピエ図書館のデイジーデータ検索・ダウンロード画面。
 * タイトル・著者の2つの検索窓と、ページング対応の一覧を提供する。
 */
@SuppressLint("NewApi")
public class DaisyReaderSapieBooksActivity extends DaisyEbookReaderBaseActivity {

    private static final String TAG = "SapieBooks";
    private static final int PAGE_SIZE = 50;

    private SQLiteDaisyBookHelper mSql;
    private DaisyBookPagingAdapter mPagingAdapter;
    private DaisyBookInfo mDaisyBook;

    private EditText mTextSearchTitle;
    private EditText mTextSearchAuthor;

    // Paging
    private final ExecutorService pagingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService downloadExecutor = Executors.newSingleThreadExecutor();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isLoadingMore = false;
    private volatile boolean hasMoreData = true;
    private volatile int currentPage = 0;
    private volatile String currentSessionToken = "";
    private volatile String currentRtnme = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sapie_books);

        mTextSearchTitle = findViewById(R.id.edit_text_sapie_title);
        mTextSearchAuthor = findViewById(R.id.edit_text_sapie_author);

        String websiteName = getIntent().getStringExtra(Constants.NAME_WEBSITE);

        mSql = SQLiteDaisyBookHelper.getInstance(this);

        androidx.recyclerview.widget.RecyclerView recyclerView =
                findViewById(R.id.recycler_view_sapie_books);
        androidx.recyclerview.widget.LinearLayoutManager layoutManager =
                new androidx.recyclerview.widget.LinearLayoutManager(this);
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

        recyclerView.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(androidx.recyclerview.widget.RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0) return;
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                if (lastVisibleItem >= totalItemCount - 10) {
                    loadMoreData();
                }
            }
        });

        // 初回データロード（空検索）
        loadPagingData();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(websiteName != null && !websiteName.isEmpty()
                ? websiteName : getString(R.string.sapie_books));
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

    private void setupSearchWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacksAndMessages(null);
                searchHandler.postDelayed(() -> loadPagingData(), 300);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        mTextSearchTitle.addTextChangedListener(watcher);
        mTextSearchAuthor.addTextChangedListener(watcher);
    }

    /**
     * 初回/検索条件変更時のデータロード（1ページ目）。
     */
    private void loadPagingData() {
        final String title = mTextSearchTitle.getText().toString().trim();
        final String author = mTextSearchAuthor.getText().toString().trim();
        pagingExecutor.execute(() -> doSearch(title, author, true));
    }

    /**
     * スクロール時の追加ページロード。
     */
    private void loadMoreData() {
        final String title = mTextSearchTitle.getText().toString().trim();
        final String author = mTextSearchAuthor.getText().toString().trim();
        pagingExecutor.execute(() -> doSearch(title, author, false));
    }

    /**
     * 検索を実行して一覧を更新する。
     * ページング状態はすべてこの単一スレッド上でのみ更新する。
     *
     * @param reset true の場合は1ページ目から検索し直す
     */
    private void doSearch(String title, String author, boolean reset) {
        if (reset) {
            currentPage = 0;
            currentSessionToken = "";
            currentRtnme = "";
            hasMoreData = true;
            isLoadingMore = false;
        } else {
            if (isLoadingMore || !hasMoreData) return;
            isLoadingMore = true;
        }

        int page = reset ? 1 : currentPage + 1;
        try (SapieLibraryClient client = new SapieLibraryClient("", "")) {
            SearchResult result = client.search(title, author, page,
                    currentSessionToken, currentRtnme);
            currentPage = result.getPage();
            currentSessionToken = result.getSessionToken();
            currentRtnme = result.getRtnme();
            hasMoreData = result.isHasNext();
            isLoadingMore = false;

            List<DaisyBookInfo> books = toDaisyBooks(result, page);
            runOnUiThread(() -> {
                if (reset) {
                    mPagingAdapter.submitList(books);
                    if (books.isEmpty()) {
                        Toast.makeText(DaisyReaderSapieBooksActivity.this,
                                getString(R.string.sapie_search_no_result), Toast.LENGTH_SHORT).show();
                    }
                } else if (!books.isEmpty()) {
                    List<DaisyBookInfo> currentList =
                            new ArrayList<>(mPagingAdapter.getCurrentList());
                    currentList.addAll(books);
                    mPagingAdapter.submitList(currentList);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Search failed", e);
            org.androiddaisyreader.utils.LogFile.e(TAG, "Search failed", e);
            isLoadingMore = false;
            runOnUiThread(() -> {
                Toast.makeText(DaisyReaderSapieBooksActivity.this,
                        getString(R.string.error_cannot_dowload), Toast.LENGTH_LONG).show();
            });
        }
    }

    /**
     * 検索結果をDaisyBookInfoリストに変換する。
     */
    private List<DaisyBookInfo> toDaisyBooks(SearchResult result, int page) {
        List<DaisyBookInfo> books = new ArrayList<>();
        int sort = (page - 1) * PAGE_SIZE + 1;
        for (Book book : result.getBooks()) {
            DaisyBookInfo info = new DaisyBookInfo(
                    book.getId(),
                    book.getTitle(),
                    "sapie://" + book.getId(),
                    book.getAuthor(),
                    book.getLibrary(),
                    book.getPublisher(),
                    sort++
            );
            books.add(info);
        }
        return books;
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupSearchWatchers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHandler.removeCallbacksAndMessages(null);
        pagingExecutor.shutdown();
        downloadExecutor.shutdown();
    }

    private void downloadABook(int position) {
        boolean isConnected = DaisyBookUtil.getConnectivityStatus(this)
                != Constants.CONNECT_TYPE_NOT_CONNECTED;
        IntentController intent = new IntentController(this);
        if (!isConnected) {
            intent.pushToDialog(
                    getString(R.string.error_connect_internet),
                    getString(R.string.error_title), R.raw.error,
                    false, false, null);
            return;
        }

        if (mDaisyBook == null) return;
        String bookPath = mDaisyBook.getPath();
        if (bookPath == null || !bookPath.startsWith("sapie://")) return;

        final String bookId = bookPath.replace("sapie://", "");
        final String sessionToken = currentSessionToken;
        final String rtnme = currentRtnme;

        Toast.makeText(this, getString(R.string.message_downloading_file), Toast.LENGTH_SHORT).show();
        speakText(getString(R.string.message_downloading_file));

        downloadExecutor.execute(() -> {
            try {
                String loginId = SapiePreferences.getLoginId(getApplicationContext());
                String password = SapiePreferences.getPassword(getApplicationContext());

                File tempDir = new File(getCacheDir(), "sapie");
                if (!tempDir.exists()) tempDir.mkdirs();

                File downloadedFile;
                try (SapieLibraryClient client = new SapieLibraryClient(loginId, password)) {
                    downloadedFile = client.download(bookId, sessionToken, rtnme, tempDir);
                }

                String safeTitle = mDaisyBook.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");
                String fileName = safeTitle + fileExtensionOf(downloadedFile);
                String mimeType = fileName.endsWith(".zip") ? "application/zip" : "application/octet-stream";
                Uri savedUri = saveToDownloadsAndMediaStore(downloadedFile, fileName, mimeType);
                String savedPath = (savedUri != null) ? savedUri.toString() : downloadedFile.getAbsolutePath();

                DaisyBookInfo updatedInfo = new DaisyBookInfo(
                        mDaisyBook.getId(), mDaisyBook.getTitle(),
                        savedPath,
                        mDaisyBook.getAuthor(), mDaisyBook.getPublisher(),
                        mDaisyBook.getDate(), mDaisyBook.getSort());
                mSql.addOrReplaceDaisyBook(updatedInfo, Constants.TYPE_DOWNLOADED_BOOK);
                DaisyBookUtil.addRecentBookToSQLite(updatedInfo,
                        Constants.NUMBER_OF_RECENTBOOK_DEFAULT, mSql);

                downloadedFile.delete();

                showDownloadCompleteNotification(fileName, savedUri);

                runOnUiThread(() -> {
                    Toast.makeText(DaisyReaderSapieBooksActivity.this,
                            getString(R.string.message_download_complete), Toast.LENGTH_SHORT).show();
                    Intent downloaded = new Intent(DaisyReaderSapieBooksActivity.this,
                            DaisyReaderDownloadedBooks.class);
                    startActivity(downloaded);
                });
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                org.androiddaisyreader.utils.LogFile.e(TAG, "Sapie download failed: bookId=" + bookId, e);
                runOnUiThread(() -> {
                    speakText(getString(R.string.error_cannot_dowload));
                    showErrorWithLogSend(R.string.error_cannot_dowload);
                });
            }
        });
    }

    private String fileExtensionOf(File file) {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(idx) : ".zip";
    }

    /**
     * ダウンロードしたファイルを公開 Downloads フォルダーに保存し、MediaStore に登録する。
     */
    private Uri saveToDownloadsAndMediaStore(File sourceFile, String fileName, String mimeType) {
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

        Uri uri = resolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            Log.e(TAG, "Failed to create MediaStore entry");
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
            Log.e(TAG, "Failed to copy to Downloads", e);
            resolver.delete(uri, null, null);
            return null;
        }

        return uri;
    }

    /**
     * ダウンロード完了の通知を表示する。タップで書籍を開く。
     */
    private void showDownloadCompleteNotification(String fileName, Uri fileUri) {
        String channelId = "sapie_download";
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId,
                    getString(R.string.sapie_settings_title),
                    android.app.NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Sapie download notifications");
            notificationManager.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(this, DaisyEbookReaderModeChoiceActivity.class);
        openIntent.putExtra(Constants.DAISY_PATH, fileUri != null ? fileUri.toString() : "");
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this, bookIdFromUri(fileUri),
                openIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder builder =
                new androidx.core.app.NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle(getString(R.string.message_download_complete))
                        .setContentText(fileName)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        notificationManager.notify(bookIdFromUri(fileUri), builder.build());
    }

    private int bookIdFromUri(Uri uri) {
        if (uri == null) return 0;
        try {
            String lastSegment = uri.getLastPathSegment();
            return lastSegment != null ? lastSegment.hashCode() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
