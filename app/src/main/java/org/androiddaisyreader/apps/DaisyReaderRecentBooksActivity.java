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
import android.os.Build;
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
        mSql = new SQLiteDaisyBookHelper(this);

        mListViewRecentBooks.setOnItemClickListener(onItemBookClick);
        deleteCurrentInformation();

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
        if (sizeOfRecentBooks >= mNumberOfRecentBooks) {
            // get all items from 0 to number of recent books
            for (int i = 0; i < mNumberOfRecentBooks; i++) {
                DaisyBookInfo re = recentBooks.get(i);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    String path = re.getPath();
                    ContentResolver resolver = getContentResolver();
                    try (InputStream stream = resolver.openInputStream(Uri.parse(path))) {
                        if (stream != null) {
                            daisyBookList.add(re);
                        }
                    } catch (IOException e) {
                        //
                    }
                } else {
                    File f = new File(re.getPath());
                    if (f.exists()) {
                        daisyBookList.add(re);
                    }
                }
            }
        } else {
            for (int i = 0; i < sizeOfRecentBooks; i++) {
                DaisyBookInfo re = recentBooks.get(i);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    String path = re.getPath();
                    ContentResolver resolver = getContentResolver();
                    try (InputStream stream = resolver.openInputStream(Uri.parse(path))) {
                        if (stream != null) {
                            daisyBookList.add(re);
                        }
                    } catch (IOException e) {
                        //
                    }
                } else {
                    File f = new File(re.getPath());
                    if (f.exists()) {
                        daisyBookList.add(re);
                    }
                }
            }
        }
        return daisyBookList;
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
