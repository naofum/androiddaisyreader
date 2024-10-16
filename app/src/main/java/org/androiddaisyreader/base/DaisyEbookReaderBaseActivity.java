package org.androiddaisyreader.base;

import java.util.Locale;

import org.androiddaisyreader.apps.DaisyReaderLibraryActivity;
import org.androiddaisyreader.apps.PrivateException;
import org.androiddaisyreader.model.CurrentInformation;
import org.androiddaisyreader.sqlite.SQLiteCurrentInformationHelper;
import org.androiddaisyreader.utils.Constants;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings.System;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

//import com.google.firebase.analytics.FirebaseAnalytics;

/**
 * 
 * @author LogiGear
 * @date Jul 19, 2013
 */

public class DaisyEbookReaderBaseActivity extends AppCompatActivity implements OnClickListener,
        TextToSpeech.OnInitListener {
    protected static TextToSpeech mTts;
    private static final long DOUBLE_PRESS_INTERVAL = 1000;
    private static final long DELAY_MILLIS = 500;
    private static long lastPressTime;
    private static int lastPositionClick = -1;
    private static boolean mHasDoubleClicked = false;
//    protected FirebaseAnalytics mFirebaseAnalytics;


    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Obtain the FirebaseAnalytics instance.
//        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        // initial TTS
        startTts();

        if (Build.VERSION.SDK_INT >= 23) { // Build.VERSION_CODES.M
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                android.Manifest.permission.READ_EXTERNAL_STORAGE},
                        1);
            }
        }

        SharedPreferences mPreferences = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        Constants.folderRoot = mPreferences.getString(Constants.STORAGE_ROOT,
                Environment.getExternalStorageDirectory().getAbsolutePath());
//        Constants.folderContainMetadata = Constants.folderRoot
//                + "/" + Constants.FOLDER_NAME + "/";
        Constants.folderContainMetadata = getFilesDir().getAbsolutePath() + "/";
    }

    @Override
    protected void onResume() {
        super.onResume();
        final int numberToConvert = 255;
        Window window = getWindow();
        ContentResolver cResolver = getContentResolver();
        int valueScreen = 0;
        try {
            SharedPreferences mPreferences = PreferenceManager
                    .getDefaultSharedPreferences(getApplicationContext());
            valueScreen = mPreferences.getInt(Constants.BRIGHTNESS,
                    System.getInt(cResolver, System.SCREEN_BRIGHTNESS));
            LayoutParams layoutpars = window.getAttributes();
            layoutpars.screenBrightness = valueScreen / (float) numberToConvert;
            // apply attribute changes to this window
            window.setAttributes(layoutpars);
            Constants.folderRoot = mPreferences.getString(Constants.STORAGE_ROOT,
                    Environment.getExternalStorageDirectory().getAbsolutePath());
//            Constants.folderContainMetadata = Constants.folderRoot
//                    + "/" + Constants.FOLDER_NAME + "/";
            Constants.folderContainMetadata = getFilesDir().getAbsolutePath() + "/";
            startTts();
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, getApplicationContext());
            ex.writeLogException();
        }
    }

    @Override
    protected void onDestroy() {
//        try {
//            if (mTts != null) {
//                if (mTts.isSpeaking()) {
//                    mTts.stop();
//                }
//                mTts.shutdown();
//            }
//        } catch (Exception e) {
//            PrivateException ex = new PrivateException(e, DaisyEbookReaderBaseActivity.this);
//            ex.writeLogException();
//        }
        super.onDestroy();
    }

    /**
     * Make sure TTS installed on your device.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.MY_DATA_CHECK_CODE
                && !(resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS)) {
            // missing data, install it
            Intent installIntent = new Intent();
            installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            startActivity(installIntent);
        }
        if (requestCode == Constants.MY_DATA_CHECK_CODE
                && !(resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS)) {
            // missing data, install it
            Intent installIntent = new Intent();
            installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            startActivity(installIntent);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            mTts.setLanguage(checkTTSSupportLanguage() ? Locale.getDefault() : Locale.US);
        }
    }

    @Override
    public void onClick(View arg0) {

    }

    /**
     * Start TTS.
     */
    private void startTts() {
        if (mTts == null) {
            mTts = new TextToSpeech(getApplicationContext(), this);
            Intent checkIntent = new Intent();
            checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            // startActivityForResult(checkIntent, RESULT_OK);
            startActivityForResult(checkIntent, Constants.MY_DATA_CHECK_CODE);
        }
    }

    /**
     * Check TTS support language.
     * 
     * @return true, if locale is available and supported
     */
    public boolean checkTTSSupportLanguage() {
        Locale currentLocale = Locale.getDefault();
        return mTts.isLanguageAvailable(currentLocale) == TextToSpeech.LANG_MISSING_DATA
                || mTts.isLanguageAvailable(currentLocale) == TextToSpeech.LANG_NOT_SUPPORTED ? false
                : true;
    }

    /**
     * Check keyguard screen is showing or in restricted key input mode .
     * 
     * @return true, if in keyguard restricted input mode
     */
    public boolean checkKeyguardMode() {
        getApplicationContext();
        KeyguardManager kgMgr = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return kgMgr.inKeyguardRestrictedInputMode();
    }

    /**
     * Interrupts the current utterance if speaking and speak new text
     * 
     * @param textToSpeech the text to speech
     */
    public void speakText(String textToSpeech) {
        if (mTts != null) {
            if (mTts.isSpeaking()) {
                mTts.stop();
            }
            if (checkTTSSupportLanguage() && !checkKeyguardMode()) {
                mTts.speak(textToSpeech, TextToSpeech.QUEUE_FLUSH, null);
            }
        } else {
            startTts();
        }
    }

    public void speakText(String textToSpeech, int queue) {
        if (mTts != null) {
            if (checkTTSSupportLanguage() && !checkKeyguardMode()) {
                mTts.speak(textToSpeech, queue, null);
            }
        } else {
            startTts();
        }
    }

    /**
     * Speak text on handler.
     * 
     * @param textToSpeech the text to speech
     */
    @SuppressLint("HandlerLeak")
    public void speakTextOnHandler(final String textToSpeech) {
        Handler myHandler = new Handler() {
            public void handleMessage(Message m) {
                if (!mHasDoubleClicked) {
                    speakText(textToSpeech);
                }
            }
        };
        Message m = new Message();
        myHandler.sendMessageDelayed(m, DELAY_MILLIS);
    }

    /**
     * Back to top screen.
     */
    public void backToTopScreen() {
        Intent intent = new Intent(this, DaisyReaderLibraryActivity.class);
        // Removes other Activities from stack
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    /**
     * Delete current information.
     */
    public void deleteCurrentInformation() {
        SQLiteCurrentInformationHelper sql = new SQLiteCurrentInformationHelper(
                getApplicationContext());
        CurrentInformation current = sql.getCurrentInformation();
        if (current != null) {
            sql.deleteCurrentInformation(current.getId());
        }
    }

    /**
     * Restart activity when changing configuration.
     */
    private void restartActivity() {
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        restartActivity();
    }

    /**
     * Handle click item is double tap or single tap
     * 
     * @param position the position
     * @return true, if double tap on item
     */
    public boolean handleClickItem(final int position) {
        // Get current time in nano seconds.
        long pressTime = java.lang.System.currentTimeMillis();

        // If double click...
        if (pressTime - lastPressTime <= DOUBLE_PRESS_INTERVAL && lastPositionClick == position) {
            mHasDoubleClicked = true;
            // If not double click....
        } else {
            mHasDoubleClicked = false;
        }
        // record the last time the menu button was pressed.
        lastPressTime = pressTime;
        lastPositionClick = position;
        return mHasDoubleClicked;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
