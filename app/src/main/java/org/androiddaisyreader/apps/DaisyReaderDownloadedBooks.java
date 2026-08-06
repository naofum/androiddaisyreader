package org.androiddaisyreader.apps;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.androiddaisyreader.adapter.DaisyBookAdapter;
import org.androiddaisyreader.base.DaisyEbookReaderBaseActivity;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.model.ZippedBookInfo;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.CacheHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;

import com.github.naofum.androiddaisyreader.R;

/**
 * 
 * @author LogiGear
 * @date Jul 18, 2013
 */
public class DaisyReaderDownloadedBooks extends DaisyEbookReaderBaseActivity {

    private SQLiteDaisyBookHelper mSql;
    private List<DaisyBookInfo> mlistDaisyBook;
    private List<DaisyBookInfo> mListDaisyBookOriginal;
    private DaisyBookAdapter mDaisyBookAdapter;
    private EditText mTextSearch;
    private int mNumberOfRecentBooks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_downloaded_books);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.downloaded_books);

        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(DaisyReaderDownloadedBooks.this);
        mNumberOfRecentBooks = preferences.getInt(Constants.NUMBER_OF_RECENT_BOOKS,
                Constants.NUMBER_OF_RECENTBOOK_DEFAULT);

        mTextSearch = (EditText) findViewById(R.id.edit_text_search);

        mSql = SQLiteDaisyBookHelper.getInstance(DaisyReaderDownloadedBooks.this);

        // 通知クリック等でcontent:// URIが渡された場合、キャッシュしてDB登録する
        handleIntentUri(getIntent());

        mlistDaisyBook = getActualDownloadedBooks();
        mDaisyBookAdapter = new DaisyBookAdapter(DaisyReaderDownloadedBooks.this, mlistDaisyBook);
        ListView listDownloaded = (ListView) findViewById(R.id.list_view_downloaded_books);
        listDownloaded.setAdapter(mDaisyBookAdapter);
        listDownloaded.setOnItemClickListener(onItemClick);
        deleteCurrentInformation();
        mListDaisyBookOriginal = new ArrayList<DaisyBookInfo>(mlistDaisyBook);
        // start service application when download completed
//        Intent serviceIntent = new Intent(DaisyReaderDownloadedBooks.this,
//                DaisyEbookReaderService.class);
//        startService(serviceIntent);
    }

    /**
     * Intent で渡された content:// URI をキャッシュし、キャッシュのローカルパスでDB登録する。
     * DownloadManager の通知クリック時に呼ばれることを想定。
     */
    private void handleIntentUri(android.content.Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }
        Uri contentUri = intent.getData();
        String uriStr = contentUri.toString();
        if (!uriStr.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
            return;
        }

        try {
            // キャッシュにコピー
            File cachedFile = CacheHelper.copyToCache(getApplicationContext(), contentUri);
            String cachedPath = cachedFile.getAbsolutePath();

            // 既にDB登録済みか確認（同じキャッシュパスのエントリがあれば重複登録しない）
            List<DaisyBookInfo> existing = mSql.getAllDaisyBook(Constants.TYPE_DOWNLOADED_BOOK);
            for (DaisyBookInfo book : existing) {
                if (cachedPath.equals(book.getPath())) {
                    return; // 既に登録済み
                }
            }

            // ZIPストリームからメタデータを読み取り
            DaisyBookInfo bookInfo = null;
            try (InputStream input = new BufferedInputStream(new FileInputStream(cachedFile))) {
                bookInfo = ZippedBookInfo.readFromZipStream(input, Charset.forName("MS932"));
            } catch (IllegalArgumentException iae) {
                try (InputStream input = new BufferedInputStream(new FileInputStream(cachedFile))) {
                    bookInfo = ZippedBookInfo.readFromZipStream(input, Charset.defaultCharset());
                }
            }

            if (bookInfo != null) {
                bookInfo.setPath(cachedPath);
                mSql.addDaisyBook(bookInfo, Constants.TYPE_DOWNLOADED_BOOK);
                // 最近の書籍にも登録
                DaisyBookUtil.addRecentBookToSQLite(bookInfo, mNumberOfRecentBooks, mSql);
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, getApplicationContext(), uriStr);
            ex.writeLogException();
        }
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
     * get all book is downloaded
     * 
     * @return List daisy book
     */
    private List<DaisyBookInfo> getActualDownloadedBooks() {
        ArrayList<DaisyBookInfo> actualDownloadedBooks = new ArrayList<DaisyBookInfo>();
        List<DaisyBookInfo> listBooks = mSql.getAllDaisyBook(Constants.TYPE_DOWNLOADED_BOOK);
        for (DaisyBookInfo book : listBooks) {
            String path = book.getPath();
            if (path.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
                try (InputStream stream = getContentResolver().openInputStream(Uri.parse(path))) {
                    if (stream != null) {
                        actualDownloadedBooks.add(book);
                    }
                } catch (Exception e) {
                    //
                }
            } else {
                File file = new File(book.getPath());
                if (file.exists()) {
                    actualDownloadedBooks.add(book);
                }
            }
        }
        return actualDownloadedBooks;
    }

    private OnItemClickListener onItemClick = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
            speakText(mlistDaisyBook.get(arg2).getTitle());
            final DaisyBookInfo daisyBook = mlistDaisyBook.get(arg2);
            boolean isDoubleTap = handleClickItem(arg2);
            if (isDoubleTap) {
                // add to sqlite
                DaisyBookUtil.addRecentBookToSQLite(daisyBook, mNumberOfRecentBooks, mSql);
                // push to reader activity
                IntentController intentController = new IntentController(
                        DaisyReaderDownloadedBooks.this);
                intentController.pushToDaisyEbookReaderIntent(daisyBook.getPath());
            } else {
                speakTextOnHandler(daisyBook.getTitle());
            }
        }
    };

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

    @Override
    protected void onRestart() {
        deleteCurrentInformation();
        super.onRestart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleSearchBook();
        deleteCurrentInformation();
    }

    @Override
    protected void onDestroy() {
//        try {
//            if (mTts != null) {
//                mTts.shutdown();
//            }
//        } catch (Exception e) {
//            PrivateException ex = new PrivateException(e, DaisyReaderDownloadedBooks.this);
//            ex.writeLogException();
//        }
        super.onDestroy();
    }
}
