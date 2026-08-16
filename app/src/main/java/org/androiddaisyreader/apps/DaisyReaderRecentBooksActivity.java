package org.androiddaisyreader.apps;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.androiddaisyreader.adapter.DaisyBookAdapter;
import org.androiddaisyreader.base.DaisyEbookReaderBaseActivity;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
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
 * The Class DaisyReaderRecentBooksActivity.
 * 
 * @author LogiGear
 * @date Jul 5, 2013
 */

public class DaisyReaderRecentBooksActivity extends DaisyEbookReaderBaseActivity {

    private ListView mListViewRecentBooks;
    private EditText mTextSearch;
    private SQLiteDaisyBookHelper mSql;
    private DaisyBookAdapter mDaisyBookAdapter;
    private List<DaisyBookInfo> mListRecentBooks;
    private List<DaisyBookInfo> mListRecentBookOriginal;
    private int mNumberOfRecentBooks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_recent_books);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // set title of this screen
        getSupportActionBar().setTitle(R.string.recent_books);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        mNumberOfRecentBooks = preferences.getInt(Constants.NUMBER_OF_RECENT_BOOKS,
                Constants.NUMBER_OF_RECENTBOOK_DEFAULT);

        mListViewRecentBooks = (ListView) findViewById(R.id.list_view_recent_books);
        mTextSearch = (EditText) findViewById(R.id.edit_text_search);
        mTextSearch.clearFocus();

        // init SQLite Recent Book
        mSql = SQLiteDaisyBookHelper.getInstance(this);

        mListViewRecentBooks.setOnItemClickListener(onItemBookClick);
        deleteCurrentInformation();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

        case android.R.id.home:
            backToTopScreen();
            break;

        case Constants.CLEAR_RECENT:
            confirmClearAll();
            break;

        default:
            return super.onOptionsItemSelected(item);
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, Constants.CLEAR_RECENT, 0, R.string.clear_recent_books)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    /**
     * 確認ダイアログを表示してから一覧とキャッシュをクリアする。
     */
    private void confirmClearAll() {
        new android.app.AlertDialog.Builder(this)
                .setMessage(R.string.clear_recent_books_confirm)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    clearAllRecentBooksAndCache();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 最近開いた本のDB・キャッシュをすべて削除する。
     */
    private void clearAllRecentBooksAndCache() {
        // DB: 最近開いた本を全削除
        mSql.deleteAllDaisyBook(Constants.TYPE_RECENT_BOOK);

        // キャッシュフォルダを全削除
        org.androiddaisyreader.utils.CacheHelper.clearCache(getApplicationContext());

        // UI更新
        mListRecentBooks.clear();
        mListRecentBookOriginal.clear();
        if (mDaisyBookAdapter != null) {
            mDaisyBookAdapter.notifyDataSetChanged();
        }

        android.widget.Toast.makeText(this, R.string.clear_recent_books_done,
                android.widget.Toast.LENGTH_SHORT).show();
        speakText(getString(R.string.clear_recent_books_done));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // add listener search text changed
        handleSearchBook();
        deleteCurrentInformation();
        speakText(getString(R.string.title_activity_daisy_reader_recent_book));
        mListRecentBooks = loadRecentBooks();
        mListRecentBookOriginal = new ArrayList<DaisyBookInfo>(mListRecentBooks);
        mDaisyBookAdapter = new DaisyBookAdapter(DaisyReaderRecentBooksActivity.this,
                mListRecentBooks);

        mListViewRecentBooks.setAdapter(mDaisyBookAdapter);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        try {
//            if (mTts != null) {
//                mTts.shutdown();
//            }
//        } catch (Exception e) {
//            PrivateException ex = new PrivateException(e, DaisyReaderRecentBooksActivity.this);
//            ex.writeLogException();
//        }

    }

    @Override
    protected void onRestart() {
        deleteCurrentInformation();
        super.onRestart();
    }

    /**
     * Load recent books.
     * 
     * @return the array list recent books
     */
    private List<DaisyBookInfo> loadRecentBooks() {
        ArrayList<DaisyBookInfo> daisyBookList = new ArrayList<DaisyBookInfo>();
        // get all recent books from sqlite.
        List<DaisyBookInfo> recentBooks = mSql.getAllDaisyBook(Constants.TYPE_RECENT_BOOK);
        // if size of recent books > number of recent books in setting.
        int sizeOfRecentBooks = recentBooks.size();
        int limit = Math.min(sizeOfRecentBooks, mNumberOfRecentBooks);
        for (int i = 0; i < limit; i++) {
            DaisyBookInfo re = recentBooks.get(i);
            if (isBookAccessible(re.getPath())) {
                daisyBookList.add(re);
            }
        }
        return daisyBookList;
    }

    /**
     * 書籍ファイルがアクセス可能か確認する。
     * content:// URI の場合は ContentResolver で、ローカルパスの場合は File.exists() で確認。
     */
    private boolean isBookAccessible(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
            try (InputStream stream = getContentResolver().openInputStream(Uri.parse(path))) {
                return stream != null;
            } catch (Exception e) {
                return false;
            }
        } else {
            return new File(path).exists();
        }
    }

    /**
     * handle search book when text changed.
     */
    private void handleSearchBook() {
        mTextSearch.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mListRecentBookOriginal != null && mListRecentBookOriginal.size() != 0) {
                    mListRecentBooks = DaisyBookUtil.searchBookWithText(s, mListRecentBooks,
                            mListRecentBookOriginal);
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

    /** The on item book click. */
    private OnItemClickListener onItemBookClick = new OnItemClickListener() {

        @SuppressLint("HandlerLeak")
        @Override
        public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
            final DaisyBookInfo daisyBook = mListRecentBooks.get(arg2);
            boolean isDoubleTap = handleClickItem(arg2);
            if (isDoubleTap) {
                itemRecentBookClick(daisyBook);
            } else {
                speakTextOnHandler(daisyBook.getTitle());

            }
        }
    };

    /**
     * Item recent book click.
     * 
     * @param daisyBook the daisy book
     */
    private void itemRecentBookClick(DaisyBookInfo daisyBook) {
        IntentController intentController = new IntentController(this);
        intentController.pushToDaisyEbookReaderIntent(daisyBook.getPath());
    }

}
