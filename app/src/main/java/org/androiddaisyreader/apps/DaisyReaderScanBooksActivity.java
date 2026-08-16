package org.androiddaisyreader.apps;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.androiddaisyreader.adapter.DaisyBookAdapter;
import org.androiddaisyreader.base.DaisyEbookReaderBaseActivity;
import org.androiddaisyreader.metadata.MetaDataHandler;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.documentfile.provider.DocumentFile;

import com.github.naofum.androiddaisyreader.R;

/**
 * The Class DaisyReaderScanBooksActivity.
 * 
 * @author LogiGear
 * @date Jul 8, 2013
 */

@SuppressLint({ "DefaultLocale", "NewApi" })
public class DaisyReaderScanBooksActivity extends DaisyEbookReaderBaseActivity {

    private ListView mlistViewScanBooks;
    private ProgressBar mProgressBar;
    private LinearLayout mFolderPromptLayout;
    private Button mSelectFolderButton;
    private List<DaisyBookInfo> mListScanBook;
    private List<DaisyBookInfo> mListDaisyBookOriginal;
    private DaisyBookAdapter mDaisyBookAdapter;
    private int mNumberOfRecentBooks;
    private SharedPreferences mPreferences;
    private SQLiteDaisyBookHelper mSql;
    private EditText mTextSearch;
    private MetaDataHandler mMetadata;
    private ExecutorService scanExecutor;

    private ActivityResultLauncher<Uri> mFolderPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_books);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // set title of this screen
        getSupportActionBar().setTitle(R.string.title_activity_daisy_reader_scan_book);

        mPreferences = PreferenceManager
                .getDefaultSharedPreferences(DaisyReaderScanBooksActivity.this);
        mNumberOfRecentBooks = mPreferences.getInt(Constants.NUMBER_OF_RECENT_BOOKS,
                Constants.NUMBER_OF_RECENTBOOK_DEFAULT);

        mSql = SQLiteDaisyBookHelper.getInstance(DaisyReaderScanBooksActivity.this);
        // initial view
        mTextSearch = (EditText) findViewById(R.id.edit_text_search);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar_scan);
        mFolderPromptLayout = (LinearLayout) findViewById(R.id.layout_folder_prompt);
        mSelectFolderButton = (Button) findViewById(R.id.button_select_folder);
        mlistViewScanBooks = (ListView) findViewById(R.id.list_view_scan_books);
        mlistViewScanBooks.setOnItemClickListener(onItemBookClick);

        mListScanBook = new ArrayList<DaisyBookInfo>();
        mMetadata = new MetaDataHandler();

        // SAF folder picker launcher
        mFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        // 永続的なパーミッションを取得
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        // URIをPreferencesに保存
                        mPreferences.edit()
                                .putString(Constants.SAF_SCAN_FOLDER_URI, uri.toString())
                                .apply();
                        // フォルダ選択UIを隠してスキャン開始
                        mFolderPromptLayout.setVisibility(View.GONE);
                        scanFromSafFolder(uri);
                    }
                });

        mSelectFolderButton.setOnClickListener(v -> {
            mFolderPickerLauncher.launch(null);
        });

        deleteCurrentInformation();
        loadScanBooks();
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

    @Override
    protected void onResume() {
        super.onResume();

        speakText(getString(R.string.title_activity_daisy_reader_scan_book));
        handleSearchBook();
        deleteCurrentInformation();

    }

    @Override
    protected void onDestroy() {
        if (scanExecutor != null && !scanExecutor.isShutdown()) {
            scanExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    protected void onRestart() {
        deleteCurrentInformation();
        super.onRestart();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mListScanBook != null && mDaisyBookAdapter != null) {
            mDaisyBookAdapter.notifyDataSetChanged();
        }
    }

    /**
     * handle search book when text changed.
     */
    private void handleSearchBook() {
        mTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mListDaisyBookOriginal != null && mListDaisyBookOriginal.size() != 0) {
                    mListScanBook = DaisyBookUtil.searchBookWithText(s, mListScanBook,
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
     * Scan books — SAF URI が保存済みならそこからスキャン、
     * なければ従来のMediaStore + フォルダ選択UIを表示。
     */
    private void loadScanBooks() {
        String savedUri = mPreferences.getString(Constants.SAF_SCAN_FOLDER_URI, null);
        if (savedUri != null) {
            // 保存済みURIでスキャン
            mFolderPromptLayout.setVisibility(View.GONE);
            scanFromSafFolder(Uri.parse(savedUri));
        } else {
            // 従来のMediaStoreスキャン + フォルダ選択プロンプト表示
            mFolderPromptLayout.setVisibility(View.VISIBLE);
            loadBooksFromMediaStore();
        }
    }

    /**
     * SAF URI のフォルダからZIP/EPUBファイルをスキャンする。
     */
    private void scanFromSafFolder(Uri treeUri) {
        mProgressBar.setVisibility(View.VISIBLE);

        final Handler handler = new Handler(Looper.getMainLooper());
        if (scanExecutor != null && !scanExecutor.isShutdown()) {
            scanExecutor.shutdownNow();
        }
        scanExecutor = Executors.newSingleThreadExecutor();
        scanExecutor.execute(() -> {
            ArrayList<DaisyBookInfo> filesResult = new ArrayList<>();
            try {
                DocumentFile folder = DocumentFile.fromTreeUri(
                        DaisyReaderScanBooksActivity.this, treeUri);
                if (folder != null && folder.exists()) {
                    DocumentFile[] files = folder.listFiles();
                    for (DocumentFile file : files) {
                        if (!file.isFile()) continue;
                        String name = file.getName();
                        if (name == null) continue;
                        String lowerName = name.toLowerCase();
                        if (!lowerName.endsWith(".zip") && !lowerName.endsWith(".epub")) {
                            continue;
                        }

                        Uri fileUri = file.getUri();
                        DaisyBookInfo bookInfo = readBookInfoFromSafUri(fileUri, name);
                        if (bookInfo != null) {
                            filesResult.add(bookInfo);
                        }
                    }
                }
            } catch (Exception e) {
                PrivateException ex = new PrivateException(e, getApplicationContext());
                ex.writeLogException();
            }

            final List<DaisyBookInfo> result = filesResult;
            handler.post(() -> {
                mListScanBook = result;
                mListDaisyBookOriginal = new ArrayList<DaisyBookInfo>(result);
                mDaisyBookAdapter = new DaisyBookAdapter(DaisyReaderScanBooksActivity.this,
                        mListScanBook);
                mlistViewScanBooks.setAdapter(mDaisyBookAdapter);
                mProgressBar.setVisibility(View.GONE);
            });
        });
        scanExecutor.shutdown();
    }

    /**
     * SAF URI からZIPストリームを読み取り、書籍メタデータを取得する。
     */
    private DaisyBookInfo readBookInfoFromSafUri(Uri uri, String displayName) {
        try (InputStream input = new java.io.BufferedInputStream(
                getContentResolver().openInputStream(uri))) {
            org.androiddaisyreader.model.DaisyBookInfo info =
                    org.androiddaisyreader.model.ZippedBookInfo.readFromZipStream(
                            input, java.nio.charset.Charset.forName("MS932"));
            if (info != null) {
                info.setPath(uri.toString());
                return info;
            }
        } catch (IllegalArgumentException iae) {
            // charset フォールバック
            return readBookInfoWithFallbackCharset(uri, displayName);
        } catch (Exception e) {
            android.util.Log.d("ScanBooks", "Error reading: " + displayName, e);
        }
        return null;
    }

    /**
     * デフォルト charset でフォールバック読み取り。
     */
    private DaisyBookInfo readBookInfoWithFallbackCharset(Uri uri, String displayName) {
        try (InputStream input = new java.io.BufferedInputStream(
                getContentResolver().openInputStream(uri))) {
            org.androiddaisyreader.model.DaisyBookInfo info =
                    org.androiddaisyreader.model.ZippedBookInfo.readFromZipStream(
                            input, java.nio.charset.Charset.defaultCharset());
            if (info != null) {
                info.setPath(uri.toString());
                return info;
            }
        } catch (Exception e) {
            android.util.Log.d("ScanBooks", "Fallback also failed: " + displayName, e);
        }
        return null;
    }

    /**
     * 従来のMediaStore経由でスキャン（自アプリDL分のみ）。
     */
    private void loadBooksFromMediaStore() {
        Boolean isSDPresent = Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED);
        if (isSDPresent) {
            loadBooksWithExecutor();
        } else {
            IntentController mIntentController = new IntentController(this);
            mIntentController.pushToDialog(getString(R.string.sd_card_not_present),
                    getString(R.string.error_title), R.raw.error, false, false, null);
        }
    }

    /**
     * Load scan books using ExecutorService + Handler (MediaStore方式).
     */
    private void loadBooksWithExecutor() {
        mProgressBar.setVisibility(View.VISIBLE);

        final Handler handler = new Handler(Looper.getMainLooper());
        if (scanExecutor != null && !scanExecutor.isShutdown()) {
            scanExecutor.shutdownNow();
        }
        scanExecutor = Executors.newSingleThreadExecutor();
        scanExecutor.execute(() -> {
            // Background work
            ArrayList<DaisyBookInfo> filesResult = new ArrayList<DaisyBookInfo>();
            InputStream databaseInputStream = null;
            try {
                while (!mPreferences.getBoolean(Constants.SERVICE_DONE, false)) {
                    Thread.sleep(1000);
                }
                if (mPreferences.getBoolean(Constants.SERVICE_DONE, false)) {
                    databaseInputStream = new FileInputStream(
                            Constants.folderContainMetadata
                                    + Constants.META_DATA_SCAN_BOOK_FILE_NAME);
                    NodeList nList = mMetadata.readDataScanFromXmlFile(databaseInputStream);
                    for (int temp = 0; temp < nList.getLength(); temp++) {
                        Node nNode = nList.item(temp);
                        if (nNode.getNodeType() == Node.ELEMENT_NODE) {

                            Element eElement = (Element) nNode;
                            String author = eElement.getElementsByTagName(Constants.ATT_AUTHOR)
                                    .item(0).getTextContent();
                            String publisher = eElement
                                    .getElementsByTagName(Constants.ATT_PUBLISHER).item(0)
                                    .getTextContent();
                            String path = eElement.getAttribute(Constants.ATT_PATH);
                            String title = eElement.getElementsByTagName(Constants.ATT_TITLE)
                                    .item(0).getTextContent();
                            String date = eElement.getElementsByTagName(Constants.ATT_DATE).item(0)
                                    .getTextContent();
                            DaisyBookInfo daisyBook = new DaisyBookInfo("", title, path, author,
                                    publisher, date, 1);
                            filesResult.add(daisyBook);
                        }
                    }
                }
            } catch (Exception e) {
                PrivateException ex = new PrivateException(e, getApplicationContext());
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

            // Post to main thread (post-execute)
            final List<DaisyBookInfo> result = filesResult;
            handler.post(() -> {
                if (result != null) {
                    mListScanBook = result;
                    mListDaisyBookOriginal = new ArrayList<DaisyBookInfo>(result);
                    mDaisyBookAdapter = new DaisyBookAdapter(DaisyReaderScanBooksActivity.this,
                            mListScanBook);
                    mlistViewScanBooks.setAdapter(mDaisyBookAdapter);
                }
                mProgressBar.setVisibility(View.GONE);
            });
        });
        scanExecutor.shutdown();
    }

    /** The on item book click. */
    private OnItemClickListener onItemBookClick = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
            final DaisyBookInfo daisyBook = mListScanBook.get(arg2);
            boolean isDoubleTap = handleClickItem(arg2);
            if (isDoubleTap) {
                DaisyBookUtil.addRecentBookToSQLite(mListScanBook.get(arg2), mNumberOfRecentBooks,
                        mSql);
                itemScanBookClick(daisyBook);
            } else {
                speakTextOnHandler(daisyBook.getTitle());
            }
        }
    };

    /**
     * Item scan book click.
     * 
     * @param daisyBook the daisy book
     */
    private void itemScanBookClick(DaisyBookInfo daisyBook) {
        String path = daisyBook.getPath();
        // content:// URI が残っている場合はキャッシュ経由でローカルパスに変換
        if (path != null && path.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
            new Thread(() -> {
                try {
                    java.io.File cachedFile = org.androiddaisyreader.utils.CacheHelper.copyToCache(
                            getApplicationContext(), path);
                    String cachedPath = cachedFile.getAbsolutePath();
                    runOnUiThread(() -> {
                        IntentController intentController = new IntentController(
                                DaisyReaderScanBooksActivity.this);
                        intentController.pushToDaisyEbookReaderIntent(cachedPath);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        PrivateException ex = new PrivateException(e,
                                DaisyReaderScanBooksActivity.this, path);
                        ex.writeLogException();
                    });
                }
            }).start();
        } else {
            IntentController intentController = new IntentController(this);
            intentController.pushToDaisyEbookReaderIntent(path);
        }
    }

}
