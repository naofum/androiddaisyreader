package org.androiddaisyreader.apps;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.androiddaisyreader.base.DaisyEbookReaderBaseModeActivity;
import org.androiddaisyreader.base.DaisyEbookReaderBaseMode;
import org.androiddaisyreader.controller.AudioPlayerController;
import org.androiddaisyreader.model.Audio;
import org.androiddaisyreader.model.BookContext;

import java.io.InputStream;
import org.androiddaisyreader.model.CurrentInformation;
import org.androiddaisyreader.model.DaisyBook;
import org.androiddaisyreader.model.DaisySnippet;
import org.androiddaisyreader.model.Navigable;
import org.androiddaisyreader.model.Navigator;
import org.androiddaisyreader.model.Part;
import org.androiddaisyreader.model.Section;
import org.androiddaisyreader.player.AndroidAudioPlayer;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sqlite.SQLiteCurrentInformationHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

import com.github.naofum.androiddaisyreader.R;
import com.google.marvin.widget.GestureOverlay;
import com.google.marvin.widget.GestureOverlay.Gesture;
import com.google.marvin.widget.GestureOverlay.GestureListener;

/**
 * This activity is simple mode which play audio.
 * 
 * @author LogiGear
 * @date 2013.03.05
 */

public class DaisyEbookReaderSimpleModeActivity extends DaisyEbookReaderBaseModeActivity {
    private boolean mIsFirstNext = false;
    private boolean mIsFirstPrevious = true;
    private DaisyBook mBook;
    private boolean mNeedReadBook = true;
    private Navigator mNavigator;
    private Navigator mNavigatorOfTableContents;
    private AudioPlayerController mAudioPlayer;
    private MediaPlayer mPlayer;
    private List<String> mListStringText;
    private List<Integer> mListTimeEnd;
    private List<Integer> mListTimeBegin;
    private int mTime = -1;
    private int mPositionSentence = 0;
    private boolean mIsRunable = true;
    private boolean mIsEndOf = false;
    private static final int TIME_FOR_PROCESS = 400;
    private float mPlaybackSpeed = 1.0f;
    private boolean mIsFound = true;
    private int mOldMessage;
    private CurrentInformation mCurrent;
    private int mPositionSection = 0;
    private boolean mIsPlaying = false;
    private List<Audio> listAudio;
    private int countAudio = 0;
    private Map<String, List<Integer>> mHashMapBegin;
    private Map<String, List<Integer>> mHashMapEnd;
    private String prevImg = "";
    private String imgSrc;
    private long mTimePause = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daisy_ebook_reader_simple_mode);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mIntentController = new IntentController(DaisyEbookReaderSimpleModeActivity.this);

        setHelpButton();

        RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.daisyReaderSimpleModeLayout);
        GestureOverlay mGestureOverlay = new GestureOverlay(this, gestureListener);
        relativeLayout.addView(mGestureOverlay);
        setContentView(relativeLayout);
        mPath = getIntent().getStringExtra(Constants.DAISY_PATH);
        if (!validatePath(mPath)) {
            return;
        }
        // content:// URI はキャッシュのローカルパスに変換する
        if (mPath.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
            try {
                java.io.File cachedFile = org.androiddaisyreader.utils.CacheHelper
                        .copyToCache(getApplicationContext(), mPath);
                mPath = cachedFile.getAbsolutePath();
            } catch (java.io.IOException e) {
                showErrorDialog(e);
                finish();
                return;
            }
        }
        isFormat202 = DaisyBookUtil.findDaisyFormat(mPath, getApplicationContext()) == Constants.DAISY_202_FORMAT;

        // Presenter初期化
        baseMode = new DaisyEbookReaderBaseMode(mPath, this);
        presenter = new org.androiddaisyreader.base.ReaderPresenter(this, baseMode, mSql, mPath, isFormat202);
        presenter.openBook();

        // Presenterから参照を取得
        mBook = presenter.getBook();
        mNavigator = presenter.getNavigator();
        mNavigatorOfTableContents = presenter.getNavigatorOfTableContents();
        mAudioPlayer = presenter.getAudioPlayer();
        mPlayer = presenter.getPlayer();
        // 再生速度をPreferencesから取得
        mPlaybackSpeed = android.preference.PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext())
                .getFloat(Constants.TTS_READ_ALOUD_SPEED, Constants.TTS_SPEED_DEFAULT);

        if (mBook != null && isFormat202 && !mBook.hasTotalTime()) {
            mIntentController.pushToDialog(getString(R.string.error_wrong_format_audio),
                    getString(R.string.error_title), R.raw.error, false, false, null);
        }

        // readBookはonResumeで画面名読み上げ完了後に呼ばれる
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            onBackPressed();
            break;
        default:
            return super.onOptionsItemSelected(item);
        }
        return false;
    }

    /**
     * Start reading book.
     */
    private void readBook() {
        String section = getStringExtraSafely(Constants.POSITION_SECTION, null);
        int intentTime = getIntent().getIntExtra(Constants.TIME, -1);
        String audioFileName = "";
        presenter.readBookFromIntent(section, intentTime, audioFileName);
        mCurrent = presenter.getCurrent();
    }

    private void handleCurrentInformation(CurrentInformation current) {
        presenter.setCurrent(current);
        presenter.handleCurrentInformation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mNeedReadBook) {
            mNeedReadBook = false;
            // 初回: 画面名を読み上げ、完了後に再生開始
            speakTextWithCallback(
                    getString(R.string.title_activity_daisy_ebook_reader_simple_mode),
                    () -> {
                        if (!isFinishing()) {
                            readBook();
                        }
                    });
        } else {
            // 画面復帰: 画面名を読み上げ、完了後に再生状態を復元
            speakTextWithCallback(
                    getString(R.string.title_activity_daisy_ebook_reader_simple_mode),
                    () -> {
                        if (!isFinishing()) {
                            restorePlaybackState();
                        }
                    });
        }

        if (mBook != null) {
            mNavigatorOfTableContents = new Navigator(mBook);
        }
    }

    /**
     * 画面復帰時に再生状態を復元する。
     */
    private void restorePlaybackState() {
        dbExecutor.execute(() -> {
            final CurrentInformation current = mSql.getCurrentInformation();
            mainHandler.post(() -> {
                if (isFinishing()) return;
                presenter.setCurrent(current);
                if (current != null) {
                    if (current.getPlaying()) {
                        presenter.setMediaPlay();
                    } else {
                        presenter.setMediaPause();
                    }
                    if (!current.getActivity().equals(getActivityName())) {
                        presenter.readBook();
                    }
                }
            });
        });
    }

    @Override
    public void onClick(View v) {
    }

    /**
     * Handle all gesture actions.
     */
    private GestureListener gestureListener = new GestureListener() {

        @Override
        public void onGestureStart(int g) {
        }

        @Override
        public void onGestureFinish(int g) {
            String gesture = "GESTURE";
            try {
                // If user double tap will go to table of contents.
                boolean isDoubleTap = handleClickItem(0);
                if (isDoubleTap) {
                    Log.i(gesture, "Action: Double Tap");
                    mIsPlaying = mPlayer.isPlaying();
                    if (mIsPlaying) {
                        setMediaPause();
                    }
                    handleCurrentInformation(mCurrent);
                    mIntentController.pushToTableOfContentsIntent(mPath, mNavigatorOfTableContents,
                            getString(R.string.simple_mode));
                } else {
                    switch (g) {
                    case Gesture.CENTER:
                        Log.i(gesture, "Action: CENTER");
                        togglePlay();
                        break;
                    case Gesture.DOWN:
                        Log.i(gesture, "Action: DOWN");
                        if (mNavigator.hasNext()) {
                            speakOut(Constants.NEXT_SECTION);
                        }
                        nextSection();
                        break;
                    case Gesture.UP:
                        Log.i(gesture, "Action: UP");

                        if (mNavigator.hasPrevious()) {
                            speakOut(Constants.PREVIOUS_SECTION);
                        }
                        previousSection();
                        break;
                    case Gesture.LEFT:
                        Log.i(gesture, "Action: LEFT");
                        speakOut(Constants.PREVIOUS_SENTENCE);
                        previousSentence();
                        if (mPositionSentence > 0) {
                            mPositionSentence -= 1;
                            mHandler.removeCallbacks(mRunnable);
                            mIsRunable = true;
                            getCurrentPositionSentence();
                        }
                        break;
                    case Gesture.RIGHT:
                        Log.i(gesture, "Action: RIGHT");
                        speakOut(Constants.NEXT_SENTENCE);
                        nextSentence();
                        if (mPositionSentence < mListTimeBegin.size() - 1) {
                            mPositionSentence += 1;
                            mHandler.removeCallbacks(mRunnable);
                            mIsRunable = true;
                            getCurrentPositionSentence();
                        }
                        break;
                    default:
                        break;
                    }
                }
            } catch (Exception e) {
                PrivateException ex = new PrivateException(e,
                        DaisyEbookReaderSimpleModeActivity.this);
                ex.writeLogException();
            }
        }

        @Override
        public void onGestureChange(int g) {
        }
    };

    /**
     * Go to next sentence by seek to time of clip end nearest position.
     */
    private void nextSentence() {
        if (presenter.isReadAloudMode()) {
            presenter.nextSentence();
            return;
        }
        try {
            if (isFormat202) {
                nextSentenceDaisy202();
            }
            // For daisy format 3.0
            else {
                nextSentenceDaisy30();
            }

        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyEbookReaderSimpleModeActivity.this);
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
            nextSection();
            mPositionSentence -= 1;
        }
    }

    private void nextSentenceDaisy30() {
        if (mCurrent != null) {
            mIsEndOf = mCurrent.getAtTheEnd();
        }
        // this case for user press next sentence at the end of book
        if (mPlayer.getCurrentPosition() == 0 && !mNavigator.hasNext()
                && mPositionSentence == mListTimeBegin.size() || mIsEndOf) {
            onReachedEndOfBook();
        }
        // this case for user press next sentence.
        else if (mPositionSentence < mListTimeBegin.size() - 1) {
            int currentTimeBegin = mListTimeBegin.get(mPositionSentence + 1);
            int currentTimeEnd = mListTimeEnd.get(mPositionSentence + 1);
            // Find and play the next audio (If daisy book has many audio files
            // on 1 chapter).
            playFileSegmentForDaisy30(currentTimeBegin, currentTimeEnd, 1);
            mPlayer.seekTo(mListTimeBegin.get(mPositionSentence + 1));
        }
        // this case for user press next sentence at the end of section.
        else {
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

    /**
     * Go to next section.
     */
    private void nextSection() {
        boolean isPlaying = mPlayer != null && mPlayer.isPlaying();
        mHandler.removeCallbacks(mRunnable);
        mIsRunable = true;
        presenter.nextSection();
        mPositionSection = presenter.getPositionSection();
        mPositionSentence = 0;
        if (!isPlaying && !mIsEndOf) {
            setMediaPause();
        }
    }

    /**
     * Go to previous sentence by seek to time of clip end before two units.
     */
    private void previousSentence() {
        if (presenter.isReadAloudMode()) {
            presenter.previousSentence();
            return;
        }
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
            PrivateException ex = new PrivateException(e, DaisyEbookReaderSimpleModeActivity.this);
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
            Navigable n = mNavigator.previous();
            n = mNavigator.next();
            presenter.onNavigationNext((Section) n);
            mIsEndOf = false;
            mPlayer.seekTo(mListTimeBegin.get(mListTimeBegin.size() - 1));
            mPositionSentence = mListTimeBegin.size() - 1;
        }
        // this case for user press previous sentence.
        else if (mPositionSentence > 0) {
            mPlayer.seekTo(mListTimeBegin.get(mPositionSentence - 1));
        }
        // this case for user press previous sentence at the begin of
        // section.
        else {
            mHandler.removeCallbacks(mRunnable);
            mIsRunable = true;
            presenter.previousSection();
            mPositionSection = presenter.getPositionSection();
            int sizeOfListEnd = mListTimeEnd.size();
            mPositionSentence = sizeOfListEnd - 1;
            if (sizeOfListEnd > 1) {
                mPlayer.seekTo(mListTimeEnd.get(sizeOfListEnd - 2));
            }
        }
    }

    /**
     * Previous sentence daisy30.
     */
    private void previousSentenceDaisy30() {
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
            int currentTimeBegin = mListTimeBegin.get(mPositionSentence - 1);
            int currentTimeEnd = mListTimeEnd.get(mPositionSentence - 1);
            // Find and play the next audio (If daisy book has many audio files
            // on 1 chapter).
            playFileSegmentForDaisy30(currentTimeBegin, currentTimeEnd, -1);
            mPlayer.seekTo(mListTimeBegin.get(mPositionSentence - 1));
        }
        // this case for user press previous sentence at the begin of section.
        else {
            mHandler.removeCallbacks(mRunnable);
            mIsRunable = true;
            presenter.previousSection();
            mPositionSection = presenter.getPositionSection();
            mPositionSentence = mListTimeBegin.size() - 1;
            if (mListTimeEnd.size() > 1) {
                // get all text of text view
                mAudioPlayer.playFileSegment(listAudio.get(listAudio.size() - 1));
                countAudio = listAudio.size() - 1;
                mPlayer.seekTo(mListTimeEnd.get(mListTimeEnd.size() - 2));
            }
        }
    }

    /**
     * Go to previous section.
     */
    private void previousSection() {
        mCurrent = mSql.getCurrentInformation();
        boolean isPlaying = mPlayer != null && mPlayer.isPlaying();
        mIsEndOf = false;
        if (mCurrent != null) {
            mCurrent.setAtTheEnd(false);
            mSql.updateCurrentInformation(mCurrent);
            presenter.setCurrent(mCurrent);
        }
        mHandler.removeCallbacks(mRunnable);
        mIsRunable = true;
        presenter.previousSection();
        mPositionSection = presenter.getPositionSection();
        mPositionSentence = 0;
        if (!isPlaying) {
            setMediaPause();
        }
    }

    /**
     * Set media pause and remove call back
     */
    private void setMediaPause() {
        // TTS読み上げモードの場合はPresenter経由で制御
        if (presenter.isReadAloudMode()) {
            presenter.setMediaPause();
            return;
        }
        mHandler.removeCallbacks(mRunnable);
        if (mPlayer != null) {
            mPlayer.pause();
        }
        mIsRunable = false;
    }

    /**
     * Set media play and post runnable
     */
    private void setMediaPlay() {
        // TTS読み上げモードの場合はPresenter経由で制御
        if (presenter.isReadAloudMode()) {
            presenter.setMediaPlay();
            return;
        }
        mCurrent = mSql.getCurrentInformation();
        if (mCurrent != null) {
            mIsEndOf = mCurrent.getAtTheEnd();
        }
        if (mIsEndOf) {
            onReachedEndOfBook();
        } else {
            mPlayer.start();
            if (mCurrent != null) {
                mCurrent.setPlaying(true);
                mSql.updateCurrentInformation(mCurrent);
            }
            mIsRunable = true;
            if (mListTimeEnd != null && mListTimeEnd.size() > 0) {
                if (mPlayer.getCurrentPosition() != 0) {
                    // if you pause while audio playing. You need to know time
                    // pause
                    // to high light text more correctly.
                    mTimePause = mListTimeEnd.get(mPositionSentence) - mPlayer.getCurrentPosition();
                }
                // create call backs when you touch button start.
                mHandler.post(mRunnable);
            }
        }
    }

    /**
     * Toggles the Media Player between Play and Pause states.
     */
    private void togglePlay() {
        if (presenter.isReadAloudMode()) {
            presenter.togglePlay();
            return;
        }
        mIsPlaying = mPlayer.isPlaying();
        if (mIsPlaying) {
            setMediaPause();
            speakOut(Constants.PAUSE);
        } else {
            try {
                speakOut(Constants.PLAY);
                setMediaPlay();
            } catch (Exception e) {
                speakOut(Constants.ERROR_WRONG_FORMAT_AUDIO);
                PrivateException ex = new PrivateException(e,
                        DaisyEbookReaderSimpleModeActivity.this);
                ex.writeLogException();

            }
        }
    }

    /**
     * This function help to get current position sentence to support next
     * sentence, previous sentence.
     */
    private void getCurrentPositionSentence() {
        try {
            mRunnable = new Runnable() {
                @Override
                public void run() {
                    if (mIsRunable) {
                        int sizeOfStringText = mListStringText.size();
                        int sizeOfTimeData = Math.min(sizeOfStringText,
                                Math.min(mListTimeBegin.size(), mListTimeEnd.size()));
                        for (int i = mPositionSentence; i < sizeOfTimeData; i++) {
                            int currentPosition = mPlayer.getCurrentPosition();
                            if (mListTimeBegin.get(i) <= currentPosition + TIME_FOR_PROCESS
                                    && currentPosition < mListTimeEnd.get(i)) {
                                mPositionSentence = i;
                                break;
                            }
                            // This case for daisy 3.0. Some audio files won't
                            // play until it finish, it was splitted and move to
                            // the next chapter
                            else if (mPositionSentence + 1 >= sizeOfTimeData && !mIsEndOf) {
                                nextSection();
                            }
                        }
                    }
                    if (mPositionSentence >= mListTimeEnd.size()) {
                        return;
                    }
                    if (mTimePause == 0) {
                        int timeReadSentence = mListTimeEnd.get(mPositionSentence)
                                - mListTimeBegin.get(mPositionSentence);
                        long delay = (long) (timeReadSentence / mPlaybackSpeed);
                        mHandler.postDelayed(this, delay);
                    } else {
                        // If user choose pause and play. 400 is time delay
                        // when
                        // you touch on your phone.
                        long delay = (long) (mTimePause / mPlaybackSpeed) + TIME_FOR_PROCESS;
                        mHandler.postDelayed(this, delay);
                    }
                    mTimePause = 0;
                }
            };
            mHandler.post(mRunnable);
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyEbookReaderSimpleModeActivity.this);
            ex.writeLogException();
        }
    }

    /**
     * This function will speak out message.
     * 
     * @param message
     */
    private void speakOut(int message) {
        switch (message) {
        case Constants.ERROR_NO_AUDIO_FOUND:
            speakText(getString(R.string.error_no_audio_found));
            break;
        case Constants.SIMPLE_MODE:
            speakText(getString(R.string.title_activity_daisy_ebook_reader_simple_mode));
            break;
        case Constants.ERROR_WRONG_FORMAT_AUDIO:
            speakText(getString(R.string.error_wrong_format_audio));
            break;
        case Constants.AT_THE_END:
            speakText(String.format(getString(R.string.atEnd), mBook.getTitle()));
            break;
        case Constants.AT_THE_BEGIN:
            speakText(String.format(getString(R.string.atBegin), mBook.getTitle()));
            break;
        case Constants.NEXT_SECTION:
            speakText(getString(R.string.next_section));
            break;
        case Constants.PREVIOUS_SECTION:
            speakText(getString(R.string.previous_section));
            break;
        case Constants.NEXT_SENTENCE:
            speakText(getString(R.string.next_sentence));
            break;
        case Constants.PREVIOUS_SENTENCE:
            speakText(getString(R.string.previous_sentence));
            break;
        case Constants.PLAY:
            speakText(getString(R.string.play));
            break;
        case Constants.PAUSE:
            speakText(getString(R.string.pause));
            break;
        default:
            break;
        }
    }

    private void setHelpButton() {
        getSupportActionBar().setDisplayOptions(getSupportActionBar().getDisplayOptions() | ActionBar.DISPLAY_SHOW_CUSTOM);
        ImageView imageView = new ImageView(getSupportActionBar().getThemedContext());
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.ic_menu_help);
        ActionBar.LayoutParams layoutParams = new ActionBar.LayoutParams(
                ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        layoutParams.rightMargin = 40;
        imageView.setLayoutParams(layoutParams);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean current = mCurrent.getPlaying();
                setMediaPause();
                String helpText = "この画面の使い方を説明します。画面をタップすると再生と一時停止を切り替えます。ダブルタップすると目次を表示します。下にスライドすると次の章に移動します。うえにスライドすると前の章に移動します。右にスライドすると次のぶんに移動します。左にスライドすると前のぶんに移動します。";
                if (mTts != null) {
                    mTts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                        }

                        @Override
                        public void onDone(String utteranceId) {
                            if ("help_utterance".equals(utteranceId) && current) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        setMediaPlay();
                                    }
                                });
                            }
                        }

                        @Override
                        public void onError(String utteranceId) {
                            if ("help_utterance".equals(utteranceId) && current) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        setMediaPlay();
                                    }
                                });
                            }
                        }
                    });
                    if (mTts != null && mTtsReady && mTts.isSpeaking()) {
                        mTts.stop();
                    }
                    Bundle ttsParams = new Bundle();
                    if (mTts != null && mTtsReady) {
                        mTts.speak(helpText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, ttsParams, "help_utterance");
                    }
                }
            }
        });
        getSupportActionBar().setCustomView(imageView);
    }

    // ========================================================================
    // ReaderView 実装
    // ========================================================================

    @Override
    public String getActivityName() {
        return getString(R.string.title_activity_daisy_ebook_reader_simple_mode);
    }

    @Override
    public void showPlayingState() {
        // SimpleModeではUIボタンなし
    }

    @Override
    public void showPausedState() {
        // SimpleModeではUIボタンなし
    }

    @Override
    public void onReachedEndOfBook() {
        if (presenter.isReadAloudMode()) {
            // TTS読み上げモード: 状態を壊さずダイアログのみ表示
            speakText(getString(R.string.atEnd));
            return;
        }
        speakOut(Constants.AT_THE_END);
        int currentTime = mPlayer.getCurrentPosition();
        if (currentTime == -1 || currentTime == mPlayer.getDuration() || currentTime == 0) {
            mIsRunable = false;
            mIsEndOf = true;
        }
        if (mCurrent != null) {
            mCurrent.setAtTheEnd(mIsEndOf);
            mSql.updateCurrentInformation(mCurrent);
        }
    }

    @Override
    public void onReachedBeginOfBook() {
        speakOut(Constants.AT_THE_BEGIN);
    }

    @Override
    public void onSectionLoaded() {
        // Presenterからデータを同期
        mListStringText = presenter.getListStringText();
        mListTimeBegin = presenter.getListTimeBegin();
        mListTimeEnd = presenter.getListTimeEnd();
        listAudio = presenter.getListAudio();
        countAudio = presenter.getCountAudio();
        mHashMapBegin = presenter.getHashMapBegin();
        mHashMapEnd = presenter.getHashMapEnd();
        mCurrent = presenter.getCurrent();
        mPositionSection = presenter.getPositionSection();

        if ((mListTimeEnd != null) && (mListTimeEnd.size() > 0)) {
            if (mCurrent != null) {
                mSql.updateCurrentInformation(mCurrent);
                if (mPlayer != null) {
                    if (mCurrent.getPlaying()) {
                        setMediaPlay();
                    } else {
                        setMediaPause();
                    }
                }
            }
            getCurrentPositionSentence();
        } else if (mListStringText != null && !mListStringText.isEmpty()) {
            // オーディオなし → TTS読み上げ
            presenter.startReadAloud();
        }
    }

    @Override
    public void onSentenceChanged(int positionSentence) {
        mPositionSentence = positionSentence;
    }

    @Override
    public void displayImage(String imageSrc) {
        if (imageSrc == null || imageSrc.isEmpty() || prevImg.equals(imageSrc)) {
            return;
        }
        this.prevImg = imageSrc;

        // バックグラウンドでBitmap読み込み
        new Thread(() -> {
            try {
                BookContext bookContext = baseMode.getBookContext(mPath);
                try (InputStream input = bookContext.getResource(imageSrc)) {
                    if (input != null) {
                        Bitmap bitmap = BitmapFactory.decodeStream(input);
                        // UIスレッドで表示
                        runOnUiThread(() -> {
                            ImageView imageView = findViewById(R.id.imageView);
                            if (imageView != null && bitmap != null && !isFinishing()) {
                                imageView.setImageBitmap(bitmap);
                                imageView.setVisibility(View.VISIBLE);
                                imageView.setAdjustViewBounds(true);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                // 画像表示失敗は致命的ではない
            }
        }).start();
    }
}