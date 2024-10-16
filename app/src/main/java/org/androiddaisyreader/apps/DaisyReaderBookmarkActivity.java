package org.androiddaisyreader.apps;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.androiddaisyreader.adapter.BookmarkListAdapter;
import org.androiddaisyreader.base.DaisyEbookReaderBaseActivity;
import org.androiddaisyreader.model.Bookmark;
import org.androiddaisyreader.model.DaisyBook;
import org.androiddaisyreader.model.Navigator;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sqlite.SQLiteBookmarkHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.ListView;

import com.github.naofum.androiddaisyreader.R;

/**
 * This activity is bookmark which control all things about save bookmarks and
 * load bookmarks.
 * 
 * @author LogiGear
 * @date 2013.03.05
 */
@SuppressLint("NewApi")
public class DaisyReaderBookmarkActivity extends DaisyEbookReaderBaseActivity {
    private ListView mListBookmark;
    private List<Bookmark> mListItems;
    private Bookmark mBookmark;
    private String mPath;
    private SharedPreferences mPreferences;
    private IntentController mIntentController;
    private LoadingData mLoadingData;
    private boolean isFormat202 = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daisy_reader_bookmark);
        mPreferences = PreferenceManager
                .getDefaultSharedPreferences(DaisyReaderBookmarkActivity.this);
        mIntentController = new IntentController(this);

        mListBookmark = (ListView) this.findViewById(R.id.listBookmark);
        mPath = getIntent().getStringExtra(Constants.DAISY_PATH);
        isFormat202 = DaisyBookUtil.findDaisyFormat(mPath) == Constants.DAISY_202_FORMAT;
        createNewBookmark();
        SQLiteBookmarkHelper mSql = new SQLiteBookmarkHelper(DaisyReaderBookmarkActivity.this);
        mListItems = new ArrayList<Bookmark>();
        mListItems = mSql.getAllBookmark(mPath);
        loadData();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getBookTitle());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int order = 1;
        SubMenu subMenu = menu.addSubMenu(0, Constants.SUBMENU_MENU, order++, R.string.menu_title);

        subMenu.add(0, Constants.SUBMENU_LIBRARY, order++, R.string.submenu_library).setIcon(
                R.raw.library);

        subMenu.add(0, Constants.SUBMENU_TABLE_OF_CONTENTS, order++,
                R.string.submenu_table_of_contents).setIcon(R.raw.table_of_contents);

        subMenu.add(0, Constants.SUBMENU_SIMPLE_MODE, order++, R.string.submenu_simple_mode)
                .setIcon(R.raw.simple_mode);

        subMenu.add(0, Constants.SUBMENU_SETTINGS, order++, R.string.submenu_settings).setIcon(
                R.raw.settings);

        MenuItem subMenuItem = subMenu.getItem();
        subMenuItem.setIcon(R.raw.ic_menu_32x32);
        subMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // go to table of contents
        case Constants.SUBMENU_TABLE_OF_CONTENTS:
            pushToTableOfContent();
            return true;
            // go to simple mode
        case Constants.SUBMENU_SIMPLE_MODE:
            mIntentController.pushToDaisyEbookReaderSimpleModeIntent(getIntent().getStringExtra(
                    Constants.DAISY_PATH));
            return true;
            // go to settings
        case Constants.SUBMENU_SETTINGS:
            mIntentController.pushToDaisyReaderSettingIntent();
            return true;
            // go to library
        case Constants.SUBMENU_LIBRARY:
            mIntentController.pushToLibraryIntent();
            return true;
            // back to previous screen
        case android.R.id.home:
            onBackPressed();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private String getBookTitle() {
        String titleOfBook = null;
        mPath = getIntent().getStringExtra(Constants.DAISY_PATH);
        try {
            titleOfBook = new DaisyBookUtil().getBookTitle(mPath, getApplicationContext());
        } catch (PrivateException e) {
            e.showDialogException(mIntentController);
        }
        return titleOfBook;
    }

    /**
     * Create new bookmark.
     */
    private void createNewBookmark() {
        // create a bookmark
        try {
            mBookmark = new Bookmark();
            String audioFileName = getIntent().getStringExtra(Constants.AUDIO_FILE_NAME);
            String sentence = getIntent().getStringExtra(Constants.SENTENCE);
            String section = getIntent().getStringExtra(Constants.SECTION);
            String time = getIntent().getStringExtra(Constants.TIME);
            mBookmark.setAudioFileName(audioFileName);
            mBookmark.setPath(mPath);
            mBookmark.setText(sentence);
            mBookmark.setTime(Integer.valueOf(time));
            mBookmark.setSection(Integer.valueOf(section));
            mBookmark.setId(UUID.randomUUID().toString());
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyReaderBookmarkActivity.this);
            ex.writeLogException();
        }
    }

    private void pushToTableOfContent() {
        Navigator navigator;
        try {
            try {
                DaisyBook daisyBook;
                if (isFormat202) {
                    daisyBook = DaisyBookUtil.getDaisy202Book(mPath, getApplicationContext());
                } else {
                    daisyBook = DaisyBookUtil.getDaisy30Book(mPath, getApplicationContext());
                }
                navigator = new Navigator(daisyBook);
                mIntentController.pushToTableOfContentsIntent(mPath, navigator,
                        getString(R.string.visual_mode));
                finish();
            } catch (Exception e) {
                PrivateException ex = new PrivateException(e, DaisyReaderBookmarkActivity.this,
                        mPath);
                throw ex;
            }
        } catch (PrivateException e) {
            e.showDialogException(mIntentController);
        }
    }

    /**
     * Show dialog when data loading.
     */
    class LoadingData extends AsyncTask<Void, Void, List<Bookmark>> {
        private ProgressDialog progressDialog;
        private int numberOfBookmarks = mPreferences.getInt(Constants.NUMBER_OF_BOOKMARKS,
                Constants.NUMBER_OF_BOOKMARK_DEFAULT);;

        @Override
        protected List<Bookmark> doInBackground(Void... params) {
            ArrayList<Bookmark> result = new ArrayList<Bookmark>();
            if (numberOfBookmarks < mListItems.size()) {
                for (int i = 0; i < numberOfBookmarks; i++) {
                    Bookmark bookmark = mListItems.get(i);
                    bookmark.setTextShow(bookmark.getText());
                    result.add(bookmark);
                }
            } else {
                for (int i = 0; i < mListItems.size(); i++) {
                    Bookmark bookmark = mListItems.get(i);
                    bookmark.setTextShow(bookmark.getText());
                    result.add(bookmark);
                }
                for (int i = 0; i < numberOfBookmarks - mListItems.size(); i++) {
                    Bookmark bookmark = new Bookmark();
                    bookmark.setTextShow(getString(R.string.empty_bookmark));
                    result.add(bookmark);
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<Bookmark> result) {
            BookmarkListAdapter mAdapter;
            mAdapter = new BookmarkListAdapter(DaisyReaderBookmarkActivity.this, result, mBookmark,
                    mPath, mListItems.size());
            mListBookmark.setAdapter(mAdapter);
            progressDialog.dismiss();
        }

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(DaisyReaderBookmarkActivity.this);
            progressDialog.setMessage(getString(R.string.waiting));
            progressDialog.show();
            super.onPreExecute();
        }
    }

    private void loadData() {
        mLoadingData = new LoadingData();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mLoadingData.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            mLoadingData.execute();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mLoadingData.cancel(true);
        finish();
    }

    @Override
    protected void onDestroy() {
//        try {
//            if (mTts != null) {
//                mTts.stop();
//                mTts.shutdown();
//            }
//        } catch (Exception e) {
//            PrivateException ex = new PrivateException(e, DaisyReaderBookmarkActivity.this);
//            ex.writeLogException();
//        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        speakText(getString(R.string.title_activity_daisy_reader_bookmark));
    }
}
