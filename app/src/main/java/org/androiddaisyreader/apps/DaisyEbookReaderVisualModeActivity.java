package org.androiddaisyreader.apps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.androiddaisyreader.AudioCallbackListener;
import org.androiddaisyreader.base.DaisyEbookReaderBaseModeActivity;
import org.androiddaisyreader.base.DaisyEbookReaderBaseMode;
import org.androiddaisyreader.controller.AudioPlayerController;
import org.androiddaisyreader.model.Audio;
import org.androiddaisyreader.model.Bookmark;
import org.androiddaisyreader.model.CurrentInformation;
import org.androiddaisyreader.model.DaisyBook;
import org.androiddaisyreader.model.Navigable;
import org.androiddaisyreader.model.Navigator;
import org.androiddaisyreader.model.Part;
import org.androiddaisyreader.model.Section;
import org.androiddaisyreader.player.AndroidAudioPlayer;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sqlite.SQLiteCurrentInformationHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings.System;
import android.speech.tts.TextToSpeech;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.naofum.androiddaisyreader.R;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.Validate;

/**
 * This activity is visual mode which play audio and show full text.
 * 
 * @author LogiGear
 * @date 2013.03.05 s
 */

public class DaisyEbookReaderVisualModeActivity extends DaisyEbookReaderBaseModeActivity {

    private boolean mIsFirstNext = false;
    private boolean mIsFirstPrevious = true;
    private DaisyBook mBook;
    private Navigator mNavigator;
    private Navigator mNavigatorOfTableContents;
    private AudioPlayerController mAudioPlayer;
    private MediaPlayer mPlayer;
    private TextView mContents;
    private ImageButton mImgButton;
    private ScrollView mScrollView;
    private Spannable mWordtoSpan;
    private List<String> mListStringText;
    private List<Integer> mListTimeBegin;
    private List<Integer> mListTimeEnd;
    private List<Integer> mListValueScroll;
    private List<Integer> mListValueLine;
    private String mFullTextOfBook;
    private int mTime;
    private int mTotalLineOnScreen;
    private int mNumberOfChar;
    private int mPositionOfScrollView;
    private int mHighlightColor;
    private int mStartOfSentence = 0;
    private int mPositionSection = 0;
    private int mPositionSentence = 0;
    private static final int TIME_FOR_PROCESS = 400;
    private long mLastClickTime = 0;
    private boolean mIsRunable = true;
    private boolean mIsCancel = false;
    private boolean mIsEndOf = false;
    private boolean mIsFound = true;
    private boolean mIsPlaying = false;
    private CurrentInformation mCurrent;
    private List<Audio> listAudio;
    private int countAudio = 0;
    private List<String> listId;

    private Map<String, List<Integer>> mHashMapBegin;
    private Map<String, List<Integer>> mHashMapEnd;
    private long mTimePause = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daisy_ebook_reader_visual_mode);
        mIntentController = new IntentController(this);
        mPath = getIntent().getStringExtra(Constants.DAISY_PATH);
        if (!validatePath(mPath)) {
            return;
        }
        isFormat202 = DaisyBookUtil.findDaisyFormat(mPath, getApplicationContext()) == Constants.DAISY_202_FORMAT;
        baseMode = new DaisyEbookReaderBaseMode(mPath, this);
        presenter = new org.androiddaisyreader.base.ReaderPresenter(this, baseMode, mSql, mPath, isFormat202);

        mContents = (TextView) this.findViewById(R.id.contents);
        presenter.openBook();

        // Presenterから参照を取得
        mBook = presenter.getBook();
        mNavigator = presenter.getNavigator();
        mNavigatorOfTableContents = presenter.getNavigatorOfTableContents();
        mAudioPlayer = presenter.getAudioPlayer();
        mPlayer = presenter.getPlayer();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (mBook != null) {
            getSupportActionBar().setTitle(mBook.getTitle());
            mScrollView = (ScrollView) findViewById(R.id.scrollView);
            mImgButton = (ImageButton) this.findViewById(R.id.btnPlay);
            mImgButton.setOnClickListener(imgButtonClick);
            setEventForNavigationButtons();
            readBook();
        } else {
            mIntentController.pushToDialog(
                    String.format(getString(R.string.error_no_path_found), mPath),
                    getString(R.string.error_title), R.raw.error, true, false, null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int order = 1;
        SubMenu subMenu = menu.addSubMenu(0, Constants.SUBMENU_MENU, order++, R.string.menu_title);

        subMenu.add(0, Constants.SUBMENU_LIBRARY, order++, R.string.submenu_library).setIcon(
                R.raw.library);

        subMenu.add(0, Constants.SUBMENU_BOOKMARKS, order++, R.string.submenu_bookmarks).setIcon(
                R.raw.bookmark);

        subMenu.add(0, Constants.SUBMENU_TABLE_OF_CONTENTS, order++,
                R.string.submenu_table_of_contents).setIcon(R.raw.table_of_contents);

        subMenu.add(0, Constants.SUBMENU_SIMPLE_MODE, order++, R.string.submenu_simple_mode)
                .setIcon(R.raw.simple_mode);

        subMenu.add(0, Constants.SUBMENU_SEARCH, order++, R.string.submenu_search).setIcon(
                R.raw.search);

        subMenu.add(0, Constants.SUBMENU_SETTINGS, order++, R.string.submenu_settings).setIcon(
                R.raw.settings);

        MenuItem subMenuItem = subMenu.getItem();
        subMenuItem.setIcon(R.raw.ic_menu_32x32);
        subMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    /**
     * Event Handling for Individual menu item selected Identify single menu
     * item by it's id
     * */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() != Constants.SUBMENU_MENU) {
            mIsPlaying = mPlayer != null && mPlayer.isPlaying();
            if (mCurrent != null) {
                mCurrent.setPlaying(mIsPlaying);
            }
            if (mIsPlaying) {
                setMediaPause();
            }
        }

        switch (item.getItemId()) {
        // go to table of contents
        case Constants.SUBMENU_TABLE_OF_CONTENTS:
            pushToTableOfContents();
            return true;
            // go to simple mode
        case Constants.SUBMENU_SIMPLE_MODE:
            pushToSimpleMode();
            return true;
            // go to settings
        case Constants.SUBMENU_SETTINGS:
            pushToSettings();
            return true;
            // go to book marks
        case Constants.SUBMENU_BOOKMARKS:
            pushToBookmark();
            return true;
            // go to library
        case Constants.SUBMENU_LIBRARY:
            mIntentController.pushToLibraryIntent();
            return true;
            // go to search
        case Constants.SUBMENU_SEARCH:
            pushToDialogSearch();
            return true;
            // back to previous screen
        case android.R.id.home:
            onBackPressed();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Push to settings.
     */
    private void pushToSettings() {
        handleCurrentInformation(mCurrent);
        mIntentController.pushToDaisyReaderSettingIntent();
    }

    /**
     * Push to simple mode.
     */
    private void pushToSimpleMode() {
        handleCurrentInformation(mCurrent);
        mIntentController.pushToDaisyEbookReaderSimpleModeIntent(getIntent().getStringExtra(
                Constants.DAISY_PATH));
    }

    /**
     * Set event for bottom buttons (next sentence, next section, previous
     * sentence, previous section).
     */
    private void setEventForNavigationButtons() {
        // Event for buttons on navigation.
        ImageButton btnNextSection = (ImageButton) this.findViewById(R.id.btnNextSection);
        btnNextSection.setOnClickListener(btnNextSectionClick);
        ImageButton btnNextSentence = (ImageButton) this.findViewById(R.id.btnNextSentence);
        btnNextSentence.setOnClickListener(btnNextSentenceClick);
        ImageButton btnPreviousSection = (ImageButton) this.findViewById(R.id.btnPreviousSection);
        btnPreviousSection.setOnClickListener(btnPreviousSectionClick);
        ImageButton btnPreviousSentence = (ImageButton) this.findViewById(R.id.btnPreviousSentence);
        btnPreviousSentence.setOnClickListener(btnPreviousSentenceClick);
    }

    /**
     * Start reading book.
     */
    private void readBook() {
        String section = getIntent().getStringExtra(Constants.POSITION_SECTION);
        int intentTime = getIntent().getIntExtra(Constants.TIME, -1);
        String audioFileName = "";
        if (!isFormat202) {
            audioFileName = getIntent().getStringExtra(Constants.AUDIO_FILE_NAME);
        }
        presenter.readBookFromIntent(section, intentTime, audioFileName);
    }

    /**
     * Get current section which user want to play.
     * (Presenter委譲)
     */
    private Navigable getNavigable(int countLoop) {
        return presenter.getNavigable(countLoop);
    }

    private OnClickListener imgButtonClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            togglePlay();
        }
    };

    /**
     * Push to table of contents.
     */
    private void pushToTableOfContents() {
        handleCurrentInformation(mCurrent);
        mIntentController.pushToTableOfContentsIntent(mPath, mNavigatorOfTableContents,
                getString(R.string.visual_mode));
    }

    /**
     * Push to bookmark.
     */
    private void pushToBookmark() {
        if (mIsEndOf) {
            mIntentController.pushToDialog(
                    String.format(this.getString(R.string.error_save_bookmark), mBook.getTitle()),
                    this.getString(R.string.error_title), R.raw.error, false, false, null);
        } else {
            handleCurrentInformation(mCurrent);
            mIntentController.pushToDaisyReaderBookmarkIntent(getBookmark(), getIntent()
                    .getStringExtra(Constants.DAISY_PATH));
        }
    }

    /**
     * get Bookmark to support for function save or load bookmark
     * 
     * @return Bookmark
     */
    private Bookmark getBookmark() {
        String sentence = null;
        int currentTime = mPlayer.getCurrentPosition();
        int i = 0;
        if (mPlayer.isPlaying()) {
            setMediaPause();
        }
        if (mListStringText != null) {
            int sizeOfStringText = mListStringText.size();
            for (; i < sizeOfStringText; i++) {
                if (mListTimeBegin.get(i) <= currentTime && currentTime < mListTimeEnd.get(i)) {
                    sentence = mListStringText.get(i);
                    break;
                }
            }
            // fix bug: chapter does not support audio and contents.
            if (sentence != null && sentence.length() <= 0 && mListStringText.size() > 1) {
                sentence = mListStringText.get(i + 1);
            } else if (sentence != null && sentence.length() <= 0) {
                sentence = " ";
            }
        }
        Bookmark bookmark = null;
        if (!isFormat202 && listAudio != null) {
            bookmark = new Bookmark(listAudio.get(countAudio).getAudioFilename(), mPath, sentence,
                    currentTime, mPositionSection, 0, "");
        } else {
            bookmark = new Bookmark("", mPath, sentence, currentTime, mPositionSection, 0, "");
        }
        return bookmark;
    }

    @Override
    public void onBackPressed() {
        if (mBook != null && mPlayer != null) {
            mIsPlaying = mPlayer.isPlaying();
            if (mIsPlaying) {
                setMediaPause();
            }
//            mTts.setOnUtteranceCompletedListener(null);
            mIsCancel = true;
            super.onBackPressed();
            handleCurrentInformation(mCurrent);
            finish();
        } else {
            super.onBackPressed();
        }

    }

    private void handleCurrentInformation(CurrentInformation current) {
        presenter.setCurrent(current);
        presenter.handleCurrentInformation();
    }

    /**
     * Show dialog when user choose function search of button settings.
     */
    private void pushToDialogSearch() {
        final Dialog dialog = new Dialog(DaisyEbookReaderVisualModeActivity.this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_search);
        // set the custom dialog components - text, image and button
        final EditText searchText = (EditText) dialog.findViewById(R.id.searchText);
        Button dialogButton = (Button) dialog.findViewById(R.id.buttonSearch);
        // if button is clicked, close the custom dialog
        dialogButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String ett = searchText.getText().toString();
                if (ett.trim().length() > 0) {
                    String tvt = mContents.getText().toString();
                    int ofe = tvt.indexOf(ett, 0);
                    Spannable wordtoSpan = new SpannableString(mContents.getText());
                    for (int ofs = 0; ofs < tvt.length() && ofe != -1; ofs = ofe + 1) {
                        ofe = tvt.indexOf(ett, ofs);
                        if (ofe == -1) {
                            break;
                        } else {
                            wordtoSpan.setSpan(new BackgroundColorSpan(mHighlightColor), ofe, ofe
                                    + ett.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            mContents.setText(wordtoSpan, TextView.BufferType.SPANNABLE);
                        }
                    }
                }
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        speakText(getString(R.string.title_activity_daisy_ebook_reader_visual_mode),
                TextToSpeech.QUEUE_ADD);
        if (mBook != null) {
            getValueFromSetting();
            setNightMode();
            mNavigatorOfTableContents = new Navigator(mBook);
        }

        getStatusOfAudio();
    }

    /**
     * Handle current information.
     */
    private void getStatusOfAudio() {
        mCurrent = mSql.getCurrentInformation();
        if (mCurrent != null) {
            if (mCurrent.getPlaying()) {
                presenter.setMediaPlay();
            } else {
                presenter.setMediaPause();
            }
            if (!mCurrent.getActivity().equals(
                    getString(R.string.title_activity_daisy_ebook_reader_visual_mode))) {
                presenter.readBook();
            }
        }
    }

    /**
     * get values from setting activity to apply.
     */
    private void getValueFromSetting() {
        final int numberToConvert = 255;
        int mFontSize;
        ContentResolver cResolver = getContentResolver();
        int valueScreen = 0;
        try {
            Window window = getWindow();
            SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(DaisyEbookReaderVisualModeActivity.this);
            valueScreen = preferences.getInt(Constants.BRIGHTNESS,
                    System.getInt(cResolver, System.SCREEN_BRIGHTNESS));
            LayoutParams layoutpars = window.getAttributes();
            layoutpars.screenBrightness = valueScreen / (float) numberToConvert;
            // apply attribute changes to this window
            window.setAttributes(layoutpars);
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyEbookReaderVisualModeActivity.this);
            ex.writeLogException();
        }
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(DaisyEbookReaderVisualModeActivity.this);
        mFontSize = preferences.getInt(Constants.FONT_SIZE, Constants.FONTSIZE_DEFAULT);
        mContents.setTextSize(mFontSize);
    }

    /**
     * Apply night mode setting, if user turn on.
     */
    private void setNightMode() {
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(DaisyEbookReaderVisualModeActivity.this);
        boolean nightMode = preferences.getBoolean(Constants.NIGHT_MODE, false);
        final int nightModeColor = 0xff408000;
        final int nightModeText = 0xffc0c0c0;
        if (nightMode) {
            mContents.setTextColor(Color.WHITE);
            mScrollView.setBackgroundColor(Color.BLACK);
            mHighlightColor = nightModeColor;
        } else {
            // apply text color
            int textColor = preferences.getInt(Constants.TEXT_COLOR, nightModeText);
            mContents.setTextColor(textColor);

            // apply background color
            int backgroundColor = preferences.getInt(Constants.BACKGROUND_COLOR, Color.BLACK);
            mScrollView.setBackgroundColor(backgroundColor);

            // apply highlight color
            mHighlightColor = preferences.getInt(Constants.HIGHLIGHT_COLOR, Color.YELLOW);
        }

    }

    /**
     * Auto highlight text while audio is playing. Auto scroll when the high
     * light at the end of screen
     */
    private void autoHighlightAndScroll() {
        mWordtoSpan = (Spannable) mContents.getText();
        mRunnalbe = new Runnable() {

            @Override
            public void run() {
                try {
                    if (mIsRunable) {
                        if (mScrollView.getScrollY() != mPositionOfScrollView) {
                            mScrollView.scrollTo(0, mPositionOfScrollView);
                        }
                        autoHighlight();
                        autoScroll();
                    }
                    setIsRunable();
                    if (mTimePause == 0) {
                        if (!mListTimeBegin.isEmpty()) {
                            int timeReadSentence = mListTimeEnd.get(mPositionSentence)
                                    - mListTimeBegin.get(mPositionSentence);
                            mHandler.postDelayed(this, timeReadSentence);
                        } else {
                            mHandler.postDelayed(this, mTimePause + TIME_FOR_PROCESS);
                        }
                    } else {
                        mHandler.postDelayed(this, mTimePause + TIME_FOR_PROCESS);
                    }
                    mTimePause = 0;
                } catch (Exception e) {
                    PrivateException ex = new PrivateException(e,
                            DaisyEbookReaderVisualModeActivity.this);
                    ex.writeLogException();
                }
            }
        };
        mHandler.post(mRunnalbe);
    }

    /**
     * set start/stop runable.
     */
    private void setIsRunable() {
        if (mPlayer.isPlaying()) {
            mIsRunable = true;
        } else {
            // Do not run runable while media player is pause
            mIsRunable = false;
        }
    }

    /**
     * this function support to highlight text while audio is playing.
     */
    private void autoHighlight() {
        try {
            mFullTextOfBook = mContents.getText().toString();
            int sizeOfStringText = mListStringText.size();
            for (int i = mPositionSentence; i < sizeOfStringText; i++) {
                int currentPosition = mPlayer.getCurrentPosition();
                if (mListTimeBegin.isEmpty() && mListTimeEnd.isEmpty()) {
                    highlight(i);
                    break;
                }
                if (mListTimeBegin.get(i) <= currentPosition + TIME_FOR_PROCESS
                        && currentPosition < mListTimeEnd.get(i)) {
                    highlight(i);
                    break;
                }
                // This case for daisy 3.0. Some audio files won't play until it
                // finish, it was splitted and move to the next chapter
                else if (mPositionSentence + 1 >= sizeOfStringText && !mIsEndOf
                        && mNavigator.hasNext()) {
                    nextSection();
                }
            }

        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyEbookReaderVisualModeActivity.this);
            ex.writeLogException();
        }
    }

    private void highlight(final int line) {
        try {
            mWordtoSpan = (Spannable) mContents.getText();
            mFullTextOfBook = mContents.getText().toString();
            mStartOfSentence = mFullTextOfBook.indexOf(mListStringText.get(line), mStartOfSentence);
//            Preconditions.checkArgument(mStartOfSentence > -1);
            Validate.isTrue(mStartOfSentence > -1);
            mNumberOfChar = mStartOfSentence + mListStringText.get(line).length();
            // set color is transparent for all text before.
            runOnUiThread(new Runnable() {
                public void run() {
                    if (line > 0) {
                        mWordtoSpan.setSpan(new BackgroundColorSpan(Color.TRANSPARENT), 0,
                                mStartOfSentence, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    // set color is transparent for all text after.
                    mWordtoSpan.setSpan(new BackgroundColorSpan(Color.TRANSPARENT), mNumberOfChar,
                            mContents.getText().length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    mWordtoSpan.setSpan(new BackgroundColorSpan(mHighlightColor), mStartOfSentence,
                            mNumberOfChar, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                    mContents.setText(mWordtoSpan);

                    // ハイライト位置が画面外ならスクロール
                    Layout layout = mContents.getLayout();
                    if (layout != null && mScrollView != null) {
                        int lineNumber = layout.getLineForOffset(mStartOfSentence);
                        int scrollY = layout.getLineTop(lineNumber) - mScrollView.getHeight() / 2;
                        if (scrollY > 0) {
                            mScrollView.smoothScrollTo(0, scrollY);
                        }
                    }
                }
            });
            mPositionSentence = line;
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyEbookReaderVisualModeActivity.this);
            ex.writeLogException();
        }
    }

    /**
     * this function support to autoscroll when highlight text at the end of
     * screen.
     */
    private void autoScroll() {
        try {
            Layout contentLayout = mContents.getLayout();
//            Preconditions.checkNotNull(contentLayout);
            Validate.notNull(contentLayout);
            // get height of visual mode activity.
            LinearLayout f = (LinearLayout) findViewById(R.id.layoutVisualMode);
            int heightOfViewVisualMode = f.getMeasuredHeight();

            // get height of navigator bar
            RelativeLayout r = (RelativeLayout) findViewById(R.id.layoutRelativeLayout);
            int heightOfNavigator = r.getMeasuredHeight();

            // exactly height show text.
            int heightView = heightOfViewVisualMode - heightOfNavigator;

            int lineEndCurrent = contentLayout.getLineForOffset(mNumberOfChar);
            int lineTopCurrent = contentLayout.getLineForOffset(mStartOfSentence);
            int lineOfScreen = heightView / mContents.getLineHeight();
            if (lineEndCurrent > mTotalLineOnScreen) {
                mPositionOfScrollView = contentLayout.getLineTop(lineTopCurrent);
                mScrollView.scrollTo(0, mPositionOfScrollView);
                addToListValueOfScroll(mPositionOfScrollView);
                mTotalLineOnScreen = lineTopCurrent + lineOfScreen - 1;
                addToListValueLine(mTotalLineOnScreen);
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyEbookReaderVisualModeActivity.this);
            ex.writeLogException();
        }
    }

    /**
     * This function help to add value scroll to list to support for auto scroll
     * previous sentence.
     */
    private void addToListValueOfScroll(int positionOfScrollView) {
        boolean isAdd = true;
        int sizeOfListValueScroll = mListValueScroll.size();
        for (int i = 0; i < sizeOfListValueScroll; i++) {
            int valueOfScroll = mListValueScroll.get(i);
            if (positionOfScrollView == valueOfScroll) {
                // Check add permission.
                isAdd = false;
                break;
            }
        }
        if (isAdd) {
            mListValueScroll.add(positionOfScrollView);
        }
    }

    /**
     * This function help to add value position of line to list to support for
     * auto scroll previous sentence.
     */
    private void addToListValueLine(int positionOfLine) {
        boolean isAdd = true;
        int sizeOfListValueLine = mListValueLine.size();
        for (int i = 0; i < sizeOfListValueLine; i++) {
            int currentLine = mListValueLine.get(i);
            if (positionOfLine == currentLine) {
                // Check add permission.
                isAdd = false;
                break;
            }
        }
        if (isAdd) {
            mListValueLine.add(positionOfLine);
        }
    }

    /**
     * Toggles the Media Player between Play and Pause states.
     */
    public void togglePlay() {
        if (mPlayer.isPlaying() || mTts.isSpeaking()) {
            setMediaPause();
        } else {
            try {
                setMediaPlay();
            } catch (Exception e) {
                PrivateException ex = new PrivateException(e,
                        DaisyEbookReaderVisualModeActivity.this, mPath);
                if (!isFinishing()) {
                    ex.showDialogException(mIntentController);
                }
            }
        }
    }

    /**
     * Set media pause and remove call back
     */
    private void setMediaPause() {
        // VisualMode固有: Runnable停止、TTS停止
        mHandler.removeCallbacks(mRunnalbe);
        if (mTts != null) {
            mTts.stop();
        }
        mIsRunable = false;
        mIsCancel = true;

        // Presenter: player.pause() + DB更新 + view.showPausedState()
        presenter.setMediaPause();
        mCurrent = presenter.getCurrent();
    }

    /**
     * Set media play and post runnable
     */
    private void setMediaPlay() {
        mCurrent = mSql.getCurrentInformation();
        if (mCurrent != null) {
            mIsEndOf = mCurrent.getAtTheEnd();
        }
        if (mIsEndOf) {
            onReachedEndOfBook();
        } else {
            // Presenter: player.start() + DB更新 + view.showPlayingState()
            presenter.setCurrent(mCurrent);
            presenter.setMediaPlay();

            // VisualMode固有: ハイライト/TTS
            mIsRunable = true;
            mIsCancel = false;
            if (mListTimeEnd != null && mListTimeEnd.size() > 0) {
                if (mPlayer != null && mPlayer.getCurrentPosition() != 0) {
                    mTimePause = mListTimeEnd.get(mPositionSentence) - mPlayer.getCurrentPosition()
                            + TIME_FOR_PROCESS;
                }
                mHandler.post(mRunnalbe);
            } else {
                readAloud();
            }
        }
    }

    private HashMap<String, String> params = new HashMap<String, String>();

    /**
     * This function will help application read text by using TTS when Daisybook has not audio files
     */
    @SuppressWarnings("deprecation")
    private void readAloud() {
        if (mListStringText != null && !mListStringText.isEmpty()) {
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                    String.valueOf(mPositionSentence));
//            try {
//                while (mTts.isSpeaking()) {
//                    Thread.sleep(500);
//                }
//            } catch (Exception e) {
//                //
//            }
            mTts.speak(mListStringText.get(mPositionSentence), TextToSpeech.QUEUE_ADD, params);
            highlight(mPositionSentence);
            mTts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {

                @Override
                public void onUtteranceCompleted(String uttId) {
//                    try {
//                        while (mTts.isSpeaking()) {
//                            Thread.sleep(500);
//                        }
//                    } catch (Exception e) {
//                        //
//                    }
                    if (mIsCancel) {
                        return;
                    }
                    if (Integer.valueOf(uttId) < mListStringText.size() - 1) {
                        mPositionSentence += 1;
                        highlight(mPositionSentence);
                        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                                String.valueOf(mPositionSentence));
                        mTts.speak(mListStringText.get(mPositionSentence), TextToSpeech.QUEUE_ADD,
                                params);
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                nextSection();
                            }
                        });
                    }
                }
            });
        }
    }

    private OnClickListener btnNextSentenceClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // do not allow user press button many times at the same time.
            if (SystemClock.elapsedRealtime() - mLastClickTime < Constants.TIME_WAIT_FOR_CLICK_SENTENCE) {
                return;
            }
            mLastClickTime = SystemClock.elapsedRealtime();
            if (mListTimeBegin != null && mListTimeBegin.size() > 0) { // hasAudio
                nextSentence();
            } else if (mPositionSentence < mListStringText.size() - 1) {
                mTts.stop();
                return;
            } else {
                nextSection();
            }
            try {
//            Preconditions.checkArgument(mPositionSentence < mListTimeBegin.size() - 1);
                Validate.isTrue(mPositionSentence < mListTimeBegin.size() - 1);
                mPositionSentence += 1;
                mHandler.removeCallbacks(mRunnalbe);
                mIsRunable = true;
                autoHighlightAndScroll();
            } catch (Exception e) {
                PrivateException ex = new PrivateException(e,
                        DaisyEbookReaderVisualModeActivity.this);
                ex.writeLogException();
            }

        }
    };

    /**
     * Go to next sentence by seek to time of clip end nearest position.
     */
    private void nextSentence() {
        try {
            if (isFormat202) {
                nextSentenceDaisy202();
            }
            // For daisy format 3.0
            else {
                nextSentenceDaisy30();
            }

        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyEbookReaderVisualModeActivity.this);
            ex.writeLogException();
        }
    }

    /**
     * Next sentence daisy202.
     */
    private void nextSentenceDaisy202() {
        int currentTime = mPlayer.getCurrentPosition();
        if (mCurrent != null) {
            mIsEndOf = mCurrent.getAtTheEnd();
        }
        // this case for user press next sentence at the end of book.
        if (currentTime == 0 && !mNavigator.hasNext() && mPositionSentence == mListTimeBegin.size()
                || mIsEndOf) {
            onReachedEndOfBook();
        }
        // this case for user press next sentence.
        else if (mPositionSentence < mListTimeBegin.size() - 1) {
            mPlayer.seekTo(mListTimeBegin.get(mPositionSentence + 1));
        }
        // this case for user press next sentence at the end of section.
        else {
            mStartOfSentence = 0;
            nextSection();
            mPositionSentence -= 1;
        }
    }

    /**
     * Next sentence daisy30.
     */
    private void nextSentenceDaisy30() {
        // this case for user press next sentence at the end of book
        if (mCurrent != null) {
            mIsEndOf = mCurrent.getAtTheEnd();
        }
        if (mPlayer.getCurrentPosition() == 0 && !mNavigator.hasNext()
                && mPositionSentence == mListTimeBegin.size() || mIsEndOf) {
            onReachedEndOfBook();
        }
        // this case for user press next sentence.
        else if (mPositionSentence < mListTimeBegin.size() - 1) {
            // boolean isBreak = false;
            int currentTimeBegin = mListTimeBegin.get(mPositionSentence + 1);
            int currentTimeEnd = mListTimeEnd.get(mPositionSentence + 1);
            // Find and play the next audio (If daisy book has many audio files
            // on 1 chapter).
            playFileSegmentForDaisy30(currentTimeBegin, currentTimeEnd, 1);
            mPlayer.seekTo(mListTimeBegin.get(mPositionSentence + 1));
        }
        // this case for user press next sentence at the end of section.
        else {
            mStartOfSentence = 0;
            nextSection();
            mPositionSentence -= 1;
        }
    }

    /**
     * Play file segment for daisy30.
     * 
     * @param currentTimeBegin the current time begin
     * @param currentTimeEnd the current time end
     * @param number the 1 if next sentence and -1 if previous sentence
     */
    private void playFileSegmentForDaisy30(int currentTimeBegin, int currentTimeEnd, int number) {
        boolean isBreak = false;
        for (Entry<String, List<Integer>> entry : mHashMapBegin.entrySet()) {
            List<Integer> listValue = entry.getValue();
            if (!isBreak) {
                for (int value : listValue) {
                    if (value == currentTimeBegin) {
                        if (!entry.getKey().equals(listAudio.get(countAudio).getAudioFilename())) {
                            List<Integer> listValueEnd = mHashMapEnd.get(entry.getKey());
                            if (listValueEnd.contains(currentTimeEnd)) {
                                isBreak = true;
                                countAudio = countAudio + number;
                                mAudioPlayer.playFileSegment(listAudio.get(countAudio));
                            }
                        }
                        if (isBreak) {
                            break;
                        }
                    }
                }
            }
        }
    }

    private OnClickListener btnPreviousSentenceClick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            // do not allow user press button many times at the same time.
            if (SystemClock.elapsedRealtime() - mLastClickTime < Constants.TIME_WAIT_FOR_CLICK_SENTENCE) {
                return;
            }
            mLastClickTime = SystemClock.elapsedRealtime();
            previousSentence();
            if (mPositionSentence > 0) {
                mPositionSentence -= 1;
                mHandler.removeCallbacks(mRunnalbe);
                mIsRunable = true;
                autoHighlightAndScroll();
            }

            // auto scroll when user press previous sentence.
            int lineCurrent = mContents.getLayout().getLineForOffset(mStartOfSentence);
            int positionOfScrollView = mContents.getLayout().getLineTop(lineCurrent);
            int sizeOfValueScroll = mListValueScroll.size();

            for (int i = 0; i < sizeOfValueScroll; i++) {
                int valueOfScroll = mListValueScroll.get(i);
                if (positionOfScrollView < valueOfScroll) {
                    mPositionOfScrollView = mListValueScroll.get(i - 1);
                    mTotalLineOnScreen = mListValueLine.get(i - 1);
                    break;
                }
            }
        }
    };

    /**
     * Go to previous sentence by seek to time of clip end before two units.
     */
    private void previousSentence() {
        boolean isPlaying = mPlayer.isPlaying();
        if (mCurrent != null) {
            mIsEndOf = mCurrent.getAtTheEnd();
        }
        try {
            if (isFormat202) {
                previousSentenceDaisy202();
            }
            // For daisy format 3.0
            else {
                previousSentenceDaisy30();
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyEbookReaderVisualModeActivity.this);
            ex.writeLogException();
        }
        // keep current state media player.
        if (!isPlaying) {
            setMediaPause();
        }
    }

    /**
     * Previous sentence daisy202.
     */
    private void previousSentenceDaisy202() {
        int lengthOfSpace = 2;
        // this case for user press previous sentence at the begin of
        // book.
        if (mPositionSection == 1 && mPositionSentence == 0) {
            onReachedBeginOfBook();
        }
        // this case for user press previous sentence at the end of
        // book.
        else if (mIsEndOf) {
            // It is code to resolve previous sentence when the end
            // of the book.
            mCurrent = mSql.getCurrentInformation();
            if (mCurrent != null) {
                mCurrent.setAtTheEnd(false);
                mSql.updateCurrentInformation(mCurrent);
            }
            mIsRunable = true;
            Navigable n = mNavigator.previous();
            n = mNavigator.next();
            presenter.onNavigationNext((Section) n);
            mIsEndOf = false;

            mPlayer.seekTo(mListTimeBegin.get(mListTimeBegin.size() - 1));
            mPositionSentence = mListTimeBegin.size() - 1;
        }
        // this case for user press previous sentence.
        else if (mPositionSentence > 0) {
            int lengthOfCurrentSentence = mListStringText.get(mPositionSentence - 1).length();
            mStartOfSentence = mStartOfSentence - lengthOfCurrentSentence - lengthOfSpace;
            mPlayer.seekTo(mListTimeBegin.get(mPositionSentence - 1));
        }
        // this case for user press previous sentence at the begin of
        // section.
        else {
            mHandler.removeCallbacks(mRunnalbe);
            presenter.previousSection();
            mPositionSection = presenter.getPositionSection();
            mPositionSentence = mListTimeBegin.size() - 1;
            if (mListTimeEnd.size() > 1) {
                // get all text of text view
                mFullTextOfBook = mContents.getText().toString();
                int lengthOfCurrentSentence = mListStringText.get(mListTimeBegin.size() - 1)
                        .length();
                mStartOfSentence = mFullTextOfBook.length() - lengthOfCurrentSentence
                        - lengthOfSpace;
                mPlayer.seekTo(mListTimeEnd.get(mListTimeEnd.size() - 2));
            }
        }
    }

    /**
     * Previous sentence daisy30.
     */
    private void previousSentenceDaisy30() {
        int lengthOfSpace = 2;
        // this case for user press previous sentence at the begin of book.
        if (mPositionSection == 1 && mPositionSentence == 0) {
            onReachedBeginOfBook();
        }
        // this case for user press previous sentence at the end of book.
        else if (mIsEndOf) {
            // It is code to resolve previous sentence when the end
            // of the book.
            mCurrent = mSql.getCurrentInformation();
            if (mCurrent != null) {
                mCurrent.setAtTheEnd(false);
                mSql.updateCurrentInformation(mCurrent);
            }
            mIsRunable = true;
            Navigable n = mNavigator.previous();
            n = mNavigator.next();
            presenter.onNavigationNext((Section) n);
            mIsEndOf = false;

            mAudioPlayer.playFileSegment(listAudio.get(listAudio.size() - 1));
            countAudio = listAudio.size() - 1;
            mPlayer.seekTo(mListTimeBegin.get(mListTimeBegin.size() - 1));
            mPositionSentence = mListTimeBegin.size() - 1;
        } else if (mPositionSentence > 0) {
            // boolean isBreak = false;
            int currentTimeBegin = mListTimeBegin.get(mPositionSentence - 1);
            int currentTimeEnd = mListTimeEnd.get(mPositionSentence - 1);
            // Find and play the next audio (If daisy book has many audio files
            // on 1 chapter).
            playFileSegmentForDaisy30(currentTimeBegin, currentTimeEnd, -1);
            int lengthOfCurrentSentence = mListStringText.get(mPositionSentence - 1).length();
            mStartOfSentence = mStartOfSentence - lengthOfCurrentSentence - lengthOfSpace;
            mPlayer.seekTo(mListTimeBegin.get(mPositionSentence - 1));
        }
        // this case for user press previous sentence at the begin of section.
        else {
            mHandler.removeCallbacks(mRunnalbe);
            presenter.previousSection();
            mPositionSection = presenter.getPositionSection();
            mPositionSentence = mListTimeBegin.size() - 1;
            if (mListTimeEnd.size() > 1) {
                // get all text of text view
                mFullTextOfBook = mContents.getText().toString();
                int lengthOfCurrentSentence = mListStringText.get(mListTimeBegin.size() - 1)
                        .length();
                mStartOfSentence = mFullTextOfBook.length() - lengthOfCurrentSentence
                        - lengthOfSpace;
                mAudioPlayer.playFileSegment(listAudio.get(listAudio.size() - 1));
                countAudio = listAudio.size() - 1;
                mPlayer.seekTo(mListTimeEnd.get(mListTimeEnd.size() - 2));
            }
        }
    }

    /**
     * Go to next section.
     */
    private void nextSection() {
        mStartOfSentence = 0;
        if (mTts.isSpeaking()) {
            mTts.stop();
        }
        boolean isPlaying = mPlayer.isPlaying();
        mHandler.removeCallbacks(mRunnalbe);
        presenter.nextSection();
        mPositionSection = presenter.getPositionSection();
        mPositionSentence = 0;
        if (!isPlaying && !mIsEndOf) {
            setMediaPause();
        }
    }

    private OnClickListener btnNextSectionClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // do not allow user press button many times at the same time.
            if (SystemClock.elapsedRealtime() - mLastClickTime < Constants.TIME_WAIT_FOR_CLICK_SECTION) {
                return;
            }
            mLastClickTime = SystemClock.elapsedRealtime();
            nextSection();
        }
    };

    /**
     * Go to next section.
     */
    private void previousSection() {
        mCurrent = mSql.getCurrentInformation();
        mStartOfSentence = 0;
        if (mTts.isSpeaking()) {
            mTts.stop();
        }
        mIsEndOf = false;
        if (mCurrent != null) {
            mCurrent.setAtTheEnd(false);
            mSql.updateCurrentInformation(mCurrent);
        }
        boolean isPlaying = mPlayer.isPlaying();
        presenter.previousSection();
        if (!isPlaying) {
            setMediaPause();
        }
    }

    private OnClickListener btnPreviousSectionClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // do not allow user press button many times at the same time.
            if (SystemClock.elapsedRealtime() - mLastClickTime < Constants.TIME_WAIT_FOR_CLICK_SECTION) {
                return;
            }
            mLastClickTime = SystemClock.elapsedRealtime();
            previousSection();
        }
    };

    // ========================================================================
    // ReaderView 実装
    // ========================================================================

    @Override
    public String getActivityName() {
        return getString(R.string.title_activity_daisy_ebook_reader_visual_mode);
    }

    @Override
    public void showPlayingState() {
        mImgButton.setImageResource(R.drawable.media_pause);
    }

    @Override
    public void showPausedState() {
        mImgButton.setImageResource(R.drawable.media_play);
    }

    @Override
    public void onReachedEndOfBook() {
        mIsRunable = false;
        mIsEndOf = true;
        if (mCurrent != null) {
            mCurrent.setAtTheEnd(true);
            mSql.updateCurrentInformation(mCurrent);
        }
        mIntentController.pushToDialog(
                getString(R.string.atEnd),
                getString(R.string.atEnd), R.raw.error, false, false, null);
    }

    @Override
    public void onReachedBeginOfBook() {
        mIntentController.pushToDialog(
                getString(R.string.atBegin),
                getString(R.string.atBegin), R.raw.error, false, false, null);
    }

    @Override
    public void onSectionLoaded() {
        // Presenterからデータを同期
        mListStringText = presenter.getListStringText();
        mListTimeBegin = presenter.getListTimeBegin();
        mListTimeEnd = presenter.getListTimeEnd();
        listAudio = presenter.getListAudio();
        countAudio = presenter.getCountAudio();
        mCurrent = presenter.getCurrent();
        mPositionSentence = 0;

        // ハイライト用のスクロール値リストを初期化
        mListValueScroll = new ArrayList<>();
        mListValueScroll.add(0);
        mListValueLine = new ArrayList<>();
        mListValueLine.add(0);
        mTotalLineOnScreen = 0;
        mNumberOfChar = 0;
        mPositionOfScrollView = 0;
        mStartOfSentence = 0;

        // VisualMode: ハイライト/スクロールの開始
        if (mListTimeEnd != null && mListTimeEnd.size() > 0) {
            autoHighlightAndScroll();
            if (mCurrent != null) {
                mSql.updateCurrentInformation(mCurrent);
                if (mPlayer != null) {
                    if (mCurrent.getPlaying()) {
                        setMediaPlay();
                    } else {
                        setMediaPause();
                    }
                }
            } else {
                // 初回起動時（CurrentInformationがない場合）は自動再生
                if (mPlayer != null) {
                    setMediaPlay();
                }
            }
        } else if (mListStringText != null && !mListStringText.isEmpty()) {
            // オーディオなし → TTS読み上げにフォールバック
            readAloud();
        }
    }

    @Override
    public void onSentenceChanged(int positionSentence) {
        mPositionSentence = positionSentence;
    }

    @Override
    public void displayContent(String content) {
        if (mContents != null) {
            mContents.setText(content, TextView.BufferType.SPANNABLE);
        }
    }
}
