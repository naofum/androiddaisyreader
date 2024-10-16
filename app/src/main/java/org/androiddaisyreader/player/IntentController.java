/**
 * This is controller. It will help to change intents.
 * @author LogiGear
 * @date 2013.03.05
 */

package org.androiddaisyreader.player;

import java.util.ArrayList;

import org.androiddaisyreader.apps.DaisyEbookReaderModeChoiceActivity;
import org.androiddaisyreader.apps.DaisyEbookReaderSimpleModeActivity;
import org.androiddaisyreader.apps.DaisyEbookReaderVisualModeActivity;
import org.androiddaisyreader.apps.DaisyReaderBookmarkActivity;
import org.androiddaisyreader.apps.DaisyReaderLibraryActivity;
import org.androiddaisyreader.apps.DaisyReaderSettingActivity;
import org.androiddaisyreader.apps.DaisyReaderTableOfContentsActivity;
import org.androiddaisyreader.model.Bookmark;
import org.androiddaisyreader.model.Navigable;
import org.androiddaisyreader.model.Navigator;
import org.androiddaisyreader.model.Section;
import org.androiddaisyreader.utils.Constants;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.speech.tts.TextToSpeech;

import com.github.naofum.androiddaisyreader.R;

public class IntentController {
    private Context mContext;
    private static final int LEVEL_1 = 1;
    private static final int LEVEL_2 = 2;
    private static final int LEVEL_3 = 3;
    private static final int LEVEL_4 = 4;
    private static final int LEVEL_5 = 5;
    private static final int LEVEL_6 = 6;

    public IntentController(Context context) {
        this.mContext = context;
    }

    /**
     * push to activity setting
     */
    public void pushToDaisyReaderSettingIntent() {
        Intent i = new Intent(mContext, DaisyReaderSettingActivity.class);
        mContext.startActivity(i);
    }

    /**
     * push to activity table of contents
     * 
     * @param path
     * @param navigator
     * @param targetActivity
     */
    public void pushToTableOfContentsIntent(String path, Navigator navigator, String targetActivity) {
        Intent i = new Intent(mContext, DaisyReaderTableOfContentsActivity.class);
        ArrayList<String> listContents = new ArrayList<String>();
        while (navigator.hasNext()) {
            Navigable n = navigator.next();
            if (n instanceof Section) {
                Section section = (Section) n;
                String title = section.getTitle().replace("\n", "");
                switch (section.getLevel()) {
                case LEVEL_1:
                    listContents.add(section.getTitle());
                    break;
                case LEVEL_2:
                    listContents.add(String.format("\t %s", title));
                    break;
                case LEVEL_3:
                    listContents.add(String.format("\t \t %s", title));
                    break;
                case LEVEL_4:
                    listContents.add(String.format("\t \t \t %s", title));
                    break;
                case LEVEL_5:
                    listContents.add(String.format("\t \t \t \t %s", title));
                    break;
                case LEVEL_6:
                    listContents.add(String.format("\t \t \t \t \t %s", title));
                    break;
                default:
                    break;
                }
            }
        }
        i.putStringArrayListExtra(Constants.LIST_CONTENTS, listContents);
        i.putExtra(Constants.DAISY_PATH, path);
        i.putExtra(Constants.TARGET_ACTIVITY, targetActivity);
        mContext.startActivity(i);
    }

    /**
     * push to activity bookmark
     * 
     * @param bookmark
     * @param path
     */

    public void pushToDaisyReaderBookmarkIntent(Bookmark bookmark, String path) {
        Intent i = new Intent(mContext, DaisyReaderBookmarkActivity.class);
        i.putExtra(Constants.DAISY_PATH, path);
        if (bookmark.getText() != null) {
            i.putExtra(Constants.AUDIO_FILE_NAME, bookmark.getAudioFileName());
            i.putExtra(Constants.SENTENCE, bookmark.getText());
            i.putExtra(Constants.TIME, String.valueOf(bookmark.getTime()));
            i.putExtra(Constants.SECTION, String.valueOf(bookmark.getSection()));
        }

        mContext.startActivity(i);
    }

    /**
     * push to activity simple mode
     * 
     * @param path
     */
    public void pushToDaisyEbookReaderSimpleModeIntent(String path) {
        Intent i = new Intent(mContext, DaisyEbookReaderSimpleModeActivity.class);
        i.putExtra(Constants.DAISY_PATH, path);
        mContext.startActivity(i);
    }

    /**
     * push to activity visual mode
     * 
     * @param path
     */
    public void pushToDaisyEbookReaderVisualModeIntent(String path) {
        Intent i = new Intent(mContext, DaisyEbookReaderVisualModeActivity.class);
        i.putExtra(Constants.DAISY_PATH, path);
        mContext.startActivity(i);
    }

    /**
     * Show dialog when application has error.
     * 
     * @param message : this will show for user
     * @param title : title of dialog
     * @param resId : icon message
     * @param isBack : previous screen before if true and otherwise.
     * @param isSpeak : application will speak.
     * @param tts : text to speech
     */
    @SuppressWarnings("deprecation")
    public void pushToDialog(String message, String title, int resId, final boolean isBack,
            boolean isSpeak, TextToSpeech tts) {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(mContext);
        // Setting Dialog Title
        alertDialog.setTitle(title);
        // Setting Dialog Message
        alertDialog.setMessage(message);
        // Setting Icon to Dialog
        alertDialog.setIcon(resId);
        // Setting OK Button
        alertDialog.setPositiveButton(mContext.getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (isBack) {
                            Activity a = (Activity) mContext;
                            a.onBackPressed();
                        }
                    }
                });

        if (isSpeak && tts != null) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null);
        }
        // Showing Alert Message
        AlertDialog alert = alertDialog.create();
        alert.show();
    }

    /**
     * push to activity library
     */
    public void pushToLibraryIntent() {
        Intent i = new Intent(mContext, DaisyReaderLibraryActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivity(i);
    }

    public void pushToDaisyEbookReaderIntent(String path) {
        Intent i = new Intent(mContext, DaisyEbookReaderModeChoiceActivity.class);
        i.putExtra(Constants.DAISY_PATH, path);
        mContext.startActivity(i);
    }
}
