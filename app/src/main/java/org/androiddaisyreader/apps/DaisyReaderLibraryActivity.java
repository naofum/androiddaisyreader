package org.androiddaisyreader.apps;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.androiddaisyreader.base.DaisyEbookReaderBaseActivity;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.service.DaisyEbookReaderService;
import org.androiddaisyreader.utils.Constants;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.github.naofum.androiddaisyreader.R;

/**
 * The Class DaisyReaderLibraryActivity.
 * 
 * @author LogiGear
 * @date Jul 5, 2013
 */

public class DaisyReaderLibraryActivity extends DaisyEbookReaderBaseActivity {

    private IntentController mIntentController;
    private long mLastPressTime = 0;
    private boolean mIsExit = true;
    private static final int BYTE_VALUE = 1024;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);
        mIntentController = new IntentController(this);

        // set listener for view
        findViewById(R.id.btnRecentBooks).setOnClickListener(this);
        findViewById(R.id.btnScanBooks).setOnClickListener(this);
        findViewById(R.id.btnDownloadBooks).setOnClickListener(this);

// 20180710
//        Constants.folderContainMetadata = Environment.getExternalStorageDirectory().toString()
//                + "/" + Constants.FOLDER_NAME + "/";
        createFolderContainXml();
        deleteCurrentInformation();
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        // set title of this screen
        getSupportActionBar().setTitle(R.string.title_activity_daisy_reader_library);
        Intent serviceIntent = new Intent(DaisyReaderLibraryActivity.this,
                DaisyEbookReaderService.class);
        startService(serviceIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int order = 1;
        SubMenu subMenu = menu.addSubMenu(0, Constants.SUBMENU_MENU, order++, R.string.menu_title);
        subMenu.add(0, Constants.SUBMENU_SETTINGS, order++, R.string.submenu_settings).setIcon(
                R.raw.settings);
        subMenu.add(0, Constants.SUBMENU_ABOUT, order++, R.string.submenu_about);

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
        switch (item.getItemId()) {
            // go to settings
            case Constants.SUBMENU_SETTINGS:
                mIntentController.pushToDaisyReaderSettingIntent();
                return true;
            case Constants.SUBMENU_ABOUT:
                String version = "";
                try {
                    PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
                    version = pInfo.versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
                new AlertDialog.Builder(DaisyReaderLibraryActivity.this)
                        .setTitle(R.string.submenu_about)
                        .setMessage(getText(R.string.app_name) + "\nVersion: " + version + "\nLicense: GPLv3")
                        .setPositiveButton("OK", null)
                        .show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Check to see if the sdCard is mounted and create a directory in it
     **/
    private void createFolderContainXml() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File directory = new File(Constants.folderContainMetadata);
            boolean result = true;
            if (!directory.exists()) {
                // Create a File object for the parent directory
                result = directory.mkdirs();
            }
            // Then run the method to copy the file.
            if (result) {
                copyFileFromAssets();
            }

        } else if (Environment.getExternalStorageState()
                .equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            IntentController mIntentController = new IntentController(this);
            mIntentController.pushToDialog(getString(R.string.sd_card_not_present),
                    getString(R.string.error_title), R.raw.error, false, false, null);
        }

    }

    /**
     * Copy the file from the assets folder to the sdCard
     **/
    private void copyFileFromAssets() {
        File file = new File(Constants.folderContainMetadata + Constants.META_DATA_FILE_NAME);
        if (!file.exists()) {
            AssetManager assetManager = getAssets();
            InputStream in = null;
            OutputStream out = null;
            try {
                in = assetManager.open(Constants.META_DATA_FILE_NAME);
                out = new FileOutputStream(Constants.folderContainMetadata
                        + Constants.META_DATA_FILE_NAME);
                copyFile(in, out);
                in.close();
                in = null;
                out.flush();
                out.close();
                out = null;
            } catch (Exception e) {
                PrivateException ex = new PrivateException(e, DaisyReaderLibraryActivity.this);
                ex.writeLogException();
            }
        }
    }

    /**
     * Copy file.
     * 
     * @param in the in
     * @param out the out
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BYTE_VALUE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    @SuppressLint("HandlerLeak")
    @Override
    public void onClick(View v) {
        if (v instanceof Button) {
            Button button = (Button) v;
            final String buttonText = button.getText().toString();
            boolean isDoubleTap = handleClickItem(v.getId());
            if (isDoubleTap) {
                pushToScreen(v.getId());
            } else {
                speakTextOnHandler(buttonText);
            }
        }
    }

    /**
     * Push to other screen.
     * 
     * @param activityID the activity id
     */
    private void pushToScreen(int activityID) {
        Intent intent = null;
        switch (activityID) {
        // push to Recent Books Screen.
        case R.id.btnRecentBooks:
            intent = new Intent(this, DaisyReaderRecentBooksActivity.class);
            break;
        // push to Scan Books Screen.
        case R.id.btnScanBooks:
            intent = new Intent(this, DaisyReaderScanBooksActivity.class);
            break;
        // push to Download Books Screen.
        case R.id.btnDownloadBooks:
            intent = new Intent(this, DaisyReaderDownloadSiteActivity.class);
            break;
        default:
            break;
        }
        this.startActivity(intent);
    }

    @Override
    protected void onRestart() {
        deleteCurrentInformation();
        super.onRestart();
    }

    @Override
    public void onBackPressed() {
        // do not allow user press button many times at the same time.
        if (SystemClock.elapsedRealtime() - mLastPressTime < Constants.TIME_WAIT_TO_EXIT_APPLICATION
                && mIsExit) {
            moveTaskToBack(true);
            mIsExit = false;
            Intent serviceIntent = new Intent(DaisyReaderLibraryActivity.this,
                    DaisyEbookReaderService.class);
            stopService(serviceIntent);
        } else {
            Toast.makeText(DaisyReaderLibraryActivity.this,
                    this.getString(R.string.message_exit_application), Toast.LENGTH_SHORT).show();
            speakText(this.getString(R.string.message_exit_application));
            mIsExit = true;
        }
        mLastPressTime = SystemClock.elapsedRealtime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        speakText(getString(R.string.title_activity_daisy_reader_library));
// 20180710
//        Constants.folderContainMetadata = Environment.getExternalStorageDirectory().toString()
//                + "/" + Constants.FOLDER_NAME + "/";
        createFolderContainXml();
        deleteCurrentInformation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mTts != null) {
                if (mTts.isSpeaking()) {
                    mTts.stop();
                }
                mTts.shutdown();
            }
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyReaderLibraryActivity.this);
            ex.writeLogException();
        }

    }

}
