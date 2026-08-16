package org.androiddaisyreader.apps;

import org.androiddaisyreader.base.DaisyEbookReaderBaseActivity;
import org.androiddaisyreader.utils.Constants;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings.System;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.github.naofum.androiddaisyreader.R;

import net.rdrei.android.dirchooser.DirectoryChooserActivity;
import net.rdrei.android.dirchooser.DirectoryChooserConfig;

/**
 * This activity is setting. It have some functions such as: change text color,
 * change highlight color, etc.
 * 
 * @author LogiGear
 * @date 2013.03.05
 */

@SuppressWarnings("deprecation")
public class DaisyReaderSettingActivity extends DaisyEbookReaderBaseActivity {

    private Window mWindow;
    private SharedPreferences mPreferences;
    private SharedPreferences.Editor mEditor;
    private TextView mFontSize;
    private TextView mTextColor;
    private TextView mBackgroundColor;
    private TextView mHighlightColor;
    private int mFontsize;
    // some basic colors
    private static final int COLOR_TALBE[] = { 0xffffffff, 0xffc0c0c0, 0xff808080, 0xff000000, 0xffffc0c0,
            0xffff6060, 0xffff0000, 0xff800000, 0xffffe0c0, 0xffffb060, 0xffff8000, 0xff804000,
            0xffffffc0, 0xffffff60, 0xffffff00, 0xff808000, 0xffe0ffc0, 0xffb0ff60, 0xff80ff00,
            0xff408000, 0xffc0ffc0, 0xff60ff60, 0xff00ff00, 0xff008000, 0xffc0ffe0, 0xff60ffb0,
            0xff00ff80, 0xff008040, 0xffc0ffff, 0xff60ffff, 0xff00ffff, 0xff008080, 0xffc0e0ff,
            0xff60b0ff, 0xff0080ff, 0xff004480, 0xffc0c0ff, 0xff6060ff, 0xff0000ff, 0xff000080,
            0xffe0c0ff, 0xffb060ff, 0xff8000ff, 0xff400080, 0xffffc0ff, 0xffff60ff, 0xffff00ff,
            0xff800080, 0xffffc0e0, 0xffff60b0, 0xffff0080, 0xff800040 };

    private int mBrightness;
    private int mCurrentTextColor;
    private int mCurrentBackgroundColor;
    private int mCurrentHighlightColor;
    private static final int DEFAULT_FONT_SIZE = 6;
    private static final int DEFAULT_BRIGHTNESS = 20;
    private static final int BRIGHT_BAR = 255;
    private Boolean mChangeText;
    private Boolean mChangeBackground;
    private Boolean mChangeHighlight;
    private EditText mNumberOfRecentBooks;
    private EditText mNumberOfBookmarks;
    private LayoutParams layoutpars;
    private ActivityResultLauncher<Uri> mScanFolderPickerLauncher;
    private TextView mScanFolderPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daisy_reader_setting);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mPreferences = PreferenceManager
                .getDefaultSharedPreferences(DaisyReaderSettingActivity.this);
        mEditor = mPreferences.edit();
        settingBrightness();

        mFontSize = (TextView) findViewById(R.id.tvFontSize);
        settingFontsize();
        // setting text color
        mTextColor = (TextView) findViewById(R.id.tvTextColor);
        settingTextColor();
        // setting background color
        mBackgroundColor = (TextView) findViewById(R.id.tvBackgroundColor);
        settingBackgroundColor();
        // setting current highlight color
        mHighlightColor = (TextView) findViewById(R.id.tvHighlightColor);
        settingHighlightColor();
        // setting current number of rencent books
        mNumberOfRecentBooks = (EditText) findViewById(R.id.edtNumberOfRecentBooks);
        settingCurrentRecentBook();
        // setting current number of bookmarks
        mNumberOfBookmarks = (EditText) findViewById(R.id.edtNumberOfBookmarks);
        settingCurrentBookmark();
        // setting night mode
        settingNightmode();
        // TTS言語・速度設定はTTS初期化完了後に構築する（onInitで呼ばれる）
        // setting TTS read aloud speed (TTS不要なのでここで設定)
        settingTtsSpeed();
        // setting ruby display mode
        settingRubyMode();
        // setting storage root
        settingStorageRoot();
        // setting scan folder (SAF)
        settingScanFolder();
        // setting ChattyLib credentials
        settingChattyLib();
        // setting Sapie credentials
        settingSapie();
        // setting experimental section (折りたたみ)
        settingExperimentalSection();

        // TTSがsuper.onCreate内で既にready状態になった場合、
        // onTtsReady時点ではまだViewが無いのでここで再呼び出し
        if (mTtsReady) {
            settingTtsLanguage();
        }
    }

    @Override
    protected void onTtsReady() {
        super.onTtsReady();
        // TTS初期化完了後にSpinnerを構築
        settingTtsLanguage();
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
     * Setting seekbar brightness.
     */
    private void settingBrightness() {
        SeekBar brightBar = (SeekBar) findViewById(R.id.barBrightness);
        brightBar.setMax(BRIGHT_BAR);
        brightBar.setKeyProgressIncrement(1);
        ContentResolver contentResolver = getContentResolver();
        mWindow = getWindow();
        layoutpars = mWindow.getAttributes();
        try {
            SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(DaisyReaderSettingActivity.this);
            mBrightness = preferences.getInt(Constants.BRIGHTNESS,
                    System.getInt(contentResolver, System.SCREEN_BRIGHTNESS));
            // sets the progress of the seek bar based on the system's
            // brightness
            brightBar.setProgress(mBrightness - DEFAULT_BRIGHTNESS);
            // set the brightness of this window
            layoutpars.screenBrightness = mBrightness / (float) BRIGHT_BAR;
            // apply attribute changes to this window
            mWindow.setAttributes(layoutpars);
        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, DaisyReaderSettingActivity.this);
            ex.writeLogException();
        }
        // register OnSeekBarChangeListener, so it can actually change values
        brightBar.setOnSeekBarChangeListener(seekBarBrightnessListener);
    }

    /**
     * Setting seekbar font size.
     */
    private void settingFontsize() {
        final int maxFontsizeBar = 30;
        SeekBar sizeBar = (SeekBar) findViewById(R.id.barFontSize);
        mFontsize = mPreferences.getInt(Constants.FONT_SIZE, Constants.FONTSIZE_DEFAULT);
        mFontSize.setTextSize(mFontsize);
        sizeBar.setMax(maxFontsizeBar);
        sizeBar.setProgress(mFontsize - DEFAULT_FONT_SIZE);
        sizeBar.setOnSeekBarChangeListener(seekBarSizeListener);
    }

    /**
     * Setting text color.
     */
    private void settingTextColor() {
        final int lightGray = 0xffc0c0c0;
        mCurrentTextColor = mPreferences.getInt(Constants.TEXT_COLOR, lightGray);
        mTextColor.setBackgroundColor(mCurrentTextColor);
        mTextColor.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mChangeHighlight = false;
                mChangeBackground = false;
                mChangeText = true;
                showDialog(0);
            }
        });
    }

    /**
     * Setting background color.
     */
    private void settingBackgroundColor() {
        mCurrentBackgroundColor = mPreferences.getInt(Constants.BACKGROUND_COLOR, Color.BLACK);
        mBackgroundColor.setBackgroundColor(mCurrentBackgroundColor);
        mBackgroundColor.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mChangeBackground = true;
                mChangeText = false;
                mChangeHighlight = false;
                showDialog(0);
            }
        });
    }

    /**
     * Setting highlight color.
     */
    private void settingHighlightColor() {
        mCurrentHighlightColor = mPreferences.getInt(Constants.HIGHLIGHT_COLOR, Color.YELLOW);
        mHighlightColor.setBackgroundColor(mCurrentHighlightColor);
        mHighlightColor.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mChangeBackground = false;
                mChangeText = false;
                mChangeHighlight = true;
                showDialog(0);
            }
        });

    }

    /**
     * Setting number of bookmark.
     */
    private void settingCurrentBookmark() {
        int currentNumberOfBookmarks = mPreferences.getInt(Constants.NUMBER_OF_BOOKMARKS,
                Constants.NUMBER_OF_BOOKMARK_DEFAULT);
        mNumberOfBookmarks.setText(String.valueOf(currentNumberOfBookmarks));
        mNumberOfBookmarks.addTextChangedListener(bookmarkTextWatcher);
    }

    /**
     * Setting number of recent book
     */
    private void settingCurrentRecentBook() {
        int currentNumberOfRecentBooks = mPreferences.getInt(Constants.NUMBER_OF_RECENT_BOOKS,
                Constants.NUMBER_OF_RECENTBOOK_DEFAULT);
        mNumberOfRecentBooks.setText(String.valueOf(currentNumberOfRecentBooks));
        mNumberOfRecentBooks.addTextChangedListener(recentBooksTextWatcher);
    }

    /**
     * Setting night mode.
     */
    private void settingNightmode() {
        final ToggleButton toogleNightMode = (ToggleButton) findViewById(R.id.toggleNightMode);
        boolean isCheckNightMode = mPreferences.getBoolean(Constants.NIGHT_MODE, false);
        toogleNightMode.setChecked(isCheckNightMode);
        toogleNightMode.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                mEditor.putBoolean(Constants.NIGHT_MODE, toogleNightMode.isChecked());
                mEditor.commit();
            }
        });
    }

    /**
     * Setting TTS read aloud language for books without audio.
     */
    private void settingTtsLanguage() {
        android.widget.Spinner spinner = (android.widget.Spinner) findViewById(R.id.spinnerTtsLanguage);
        if (spinner == null) {
            // setContentViewがまだ呼ばれていない場合はスキップ
            return;
        }

        // 利用可能な言語一覧を構築
        final java.util.List<java.util.Locale> availableLocales = new java.util.ArrayList<>();
        final java.util.List<String> displayNames = new java.util.ArrayList<>();

        // 先頭に「端末の既定」を追加
        displayNames.add(getString(R.string.tts_language_default));
        availableLocales.add(null);

        // TTSがサポートする言語を列挙
        java.util.Locale[] locales = java.util.Locale.getAvailableLocales();
        java.util.Set<String> added = new java.util.HashSet<>();
        for (java.util.Locale locale : locales) {
            if (locale.getLanguage().isEmpty()) continue;
            // 疑似ロケール (en_XA, ar_XB 等) をスキップ
            String country = locale.getCountry();
            if ("XA".equals(country) || "XB".equals(country)) continue;
            String key = locale.getLanguage() + "_" + locale.getCountry();
            if (added.contains(key)) continue;
            if (mTts != null) {
                try {
                    int availability = mTts.isLanguageAvailable(locale);
                    if (availability == android.speech.tts.TextToSpeech.LANG_AVAILABLE
                            || availability == android.speech.tts.TextToSpeech.LANG_COUNTRY_AVAILABLE
                            || availability == android.speech.tts.TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
                        added.add(key);
                        availableLocales.add(locale);
                        displayNames.add(locale.getDisplayName());
                    }
                } catch (Exception e) {
                    // 疑似ロケール (en_XA等) で MissingResourceException が発生する場合はスキップ
                }
            }
        }

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // 現在の設定を復元
        String savedLanguage = mPreferences.getString(Constants.TTS_READ_ALOUD_LANGUAGE, "");
        if (!savedLanguage.isEmpty()) {
            for (int i = 1; i < availableLocales.size(); i++) {
                java.util.Locale loc = availableLocales.get(i);
                if (loc != null && loc.toString().equals(savedLanguage)) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }

        // 選択変更時に保存
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
                                       int position, long id) {
                if (position == 0) {
                    mEditor.putString(Constants.TTS_READ_ALOUD_LANGUAGE, "");
                } else {
                    java.util.Locale selected = availableLocales.get(position);
                    mEditor.putString(Constants.TTS_READ_ALOUD_LANGUAGE, selected.toString());
                }
                mEditor.apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    /**
     * Setting TTS read aloud speed.
     * SeekBar: 0〜25 → speed: 0.5〜3.0 (step 0.1)
     */
    private void settingTtsSpeed() {
        android.widget.SeekBar seekBar = (android.widget.SeekBar) findViewById(R.id.barTtsSpeed);
        final android.widget.TextView tvValue = (android.widget.TextView) findViewById(R.id.tvTtsSpeedValue);

        float savedSpeed = mPreferences.getFloat(Constants.TTS_READ_ALOUD_SPEED, Constants.TTS_SPEED_DEFAULT);
        int progress = Math.round((savedSpeed - 0.5f) * 10);
        seekBar.setProgress(progress);
        tvValue.setText(String.format(java.util.Locale.US, "%.1fx", savedSpeed));

        seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                float speed = 0.5f + progress * 0.1f;
                tvValue.setText(String.format(java.util.Locale.US, "%.1fx", speed));
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar bar) {
                float speed = 0.5f + bar.getProgress() * 0.1f;
                mEditor.putFloat(Constants.TTS_READ_ALOUD_SPEED, speed);
                mEditor.apply();
            }
        });
    }

    /**
     * Setting ruby display mode (ルビ/底字の選択)
     */
    private void settingRubyMode() {
        android.widget.RadioGroup radioGroup = findViewById(R.id.radioGroupRuby);
        android.widget.RadioButton radioRuby = findViewById(R.id.radioRuby);
        android.widget.RadioButton radioBase = findViewById(R.id.radioBase);

        String currentMode = mPreferences.getString(Constants.RUBY_DISPLAY_MODE, Constants.RUBY_MODE_RUBY);
        if (Constants.RUBY_MODE_BASE.equals(currentMode)) {
            radioBase.setChecked(true);
        } else {
            radioRuby.setChecked(true);
        }

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mode;
            if (checkedId == R.id.radioBase) {
                mode = Constants.RUBY_MODE_BASE;
            } else {
                mode = Constants.RUBY_MODE_RUBY;
            }
            mEditor.putString(Constants.RUBY_DISPLAY_MODE, mode);
            mEditor.apply();
            org.androiddaisyreader.model.RubyConfig.setMode(mode);
        });
    }


    /**
     * Setting storage root
     */
    private void settingStorageRoot() {
        final TextView storage = (TextView) findViewById(R.id.textView10);
        Constants.folderRoot = mPreferences.getString(Constants.STORAGE_ROOT, Environment.getExternalStorageDirectory().getAbsolutePath());
//        Constants.folderContainMetadata = Constants.folderRoot + "/" + Constants.FOLDER_NAME + "/";
        Constants.folderContainMetadata = getFilesDir().getAbsolutePath() + "/";
        final TextView description = (TextView) findViewById(R.id.textView11);
        description.setText(Constants.folderRoot);
        storage.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                final Intent chooserIntent = new Intent(getApplication(), DirectoryChooserActivity.class);
                final DirectoryChooserConfig config = DirectoryChooserConfig.builder()
                        .newDirectoryName("DirChooserSample")
                        .initialDirectory(Constants.folderRoot)
                        .allowReadOnlyDirectory(true)
                        .allowNewDirectoryNameModification(true)
                        .build();
                chooserIntent.putExtra(DirectoryChooserActivity.EXTRA_CONFIG, config);
                startActivityForResult(chooserIntent, Constants.REQUEST_DIRECTORY);
            }
        });
    }

    /**
     * Setting scan folder (SAF).
     * ユーザーがスキャン対象フォルダを選択・変更できる。
     */
    private void settingScanFolder() {
        mScanFolderPath = (TextView) findViewById(R.id.textViewScanFolderPath);
        Button changeFolderButton = (Button) findViewById(R.id.buttonChangeScanFolder);

        // 現在の設定を表示
        String savedUri = mPreferences.getString(Constants.SAF_SCAN_FOLDER_URI, null);
        if (savedUri != null) {
            mScanFolderPath.setText(getFolderDisplayName(Uri.parse(savedUri)));
        } else {
            mScanFolderPath.setText(getString(R.string.scan_folder_not_set));
        }

        // SAF フォルダピッカー launcher
        mScanFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        // 古いパーミッションを解放
                        String oldUri = mPreferences.getString(Constants.SAF_SCAN_FOLDER_URI, null);
                        if (oldUri != null) {
                            try {
                                getContentResolver().releasePersistableUriPermission(
                                        Uri.parse(oldUri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException e) {
                                // 既に解放済みの場合は無視
                            }
                        }
                        // 新しいパーミッションを取得
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        // 保存
                        mEditor.putString(Constants.SAF_SCAN_FOLDER_URI, uri.toString());
                        mEditor.apply();
                        // 表示更新
                        mScanFolderPath.setText(getFolderDisplayName(uri));
                    }
                });

        changeFolderButton.setOnClickListener(v -> {
            mScanFolderPickerLauncher.launch(null);
        });
    }

    /**
     * URI からフォルダの表示名を取得する。
     */
    private String getFolderDisplayName(Uri uri) {
        // tree URI の場合、最後のパスセグメントからフォルダ名を推測
        String lastSegment = uri.getLastPathSegment();
        if (lastSegment != null) {
            // "primary:Download" → "Download" のように変換
            int colonIndex = lastSegment.indexOf(':');
            if (colonIndex >= 0 && colonIndex < lastSegment.length() - 1) {
                return lastSegment.substring(colonIndex + 1);
            }
            return lastSegment;
        }
        return uri.toString();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        speakText(getString(R.string.title_activity_daisy_reader_setting));
    }

    /**
     * ChattyLib の認証情報設定。
     */
    private void settingChattyLib() {
        EditText edtLoginId = findViewById(R.id.edt_chattylib_login_id);
        EditText edtPassword = findViewById(R.id.edt_chattylib_password);
        Button btnLoginCheck = findViewById(R.id.btn_chattylib_login_check);

        if (edtLoginId == null || edtPassword == null || btnLoginCheck == null) return;

        // 現在の値を表示
        String savedId = org.androiddaisyreader.utils.ChattyLibPreferences.getLoginId(this);
        String savedPw = org.androiddaisyreader.utils.ChattyLibPreferences.getPassword(this);
        if (savedId != null && !savedId.isEmpty()) {
            edtLoginId.setText(savedId);
        }
        if (savedPw != null && !savedPw.isEmpty()) {
            edtPassword.setText(savedPw);
        }

        // フォーカスが外れた時に保存
        View.OnFocusChangeListener saveListener = (v, hasFocus) -> {
            if (!hasFocus) {
                String loginId = edtLoginId.getText().toString().trim();
                String password = edtPassword.getText().toString().trim();
                if (!loginId.isEmpty() && !password.isEmpty()) {
                    org.androiddaisyreader.utils.ChattyLibPreferences.save(
                            getApplicationContext(), loginId, password);
                }
            }
        };
        edtLoginId.setOnFocusChangeListener(saveListener);
        edtPassword.setOnFocusChangeListener(saveListener);

        // 確認ボタン: ログイン確認して成功/失敗のダイアログを表示
        btnLoginCheck.setOnClickListener(v -> {
            String loginId = edtLoginId.getText().toString().trim();
            String password = edtPassword.getText().toString().trim();
            if (loginId.isEmpty() || password.isEmpty()) {
                showChattyLibLoginDialog(getString(R.string.chattylib_login_failure));
                return;
            }
            org.androiddaisyreader.utils.ChattyLibPreferences.save(
                    getApplicationContext(), loginId, password);

            Toast.makeText(DaisyReaderSettingActivity.this,
                    getString(R.string.chattylib_login_checking), Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                final String message = checkChattyLibLogin(loginId, password);
                runOnUiThread(() -> showChattyLibLoginDialog(message));
            }).start();
        });
    }

    private String checkChattyLibLogin(String loginId, String password) {
        try (org.androiddaisyreader.chattyconvert.ChattyLibraryClient client =
                     new org.androiddaisyreader.chattyconvert.ChattyLibraryClient(loginId, password)) {
            client.login();
            return getString(R.string.chattylib_login_success);
        } catch (Exception e) {
            return getString(R.string.chattylib_login_failure);
        }
    }

    private void showChattyLibLoginDialog(String message) {
        new AlertDialog.Builder(DaisyReaderSettingActivity.this)
                .setTitle(R.string.chattylib_settings_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    /**
     * サピエ図書館の認証情報設定。
     */
    private void settingSapie() {
        EditText edtLoginId = findViewById(R.id.edt_sapie_login_id);
        EditText edtPassword = findViewById(R.id.edt_sapie_password);
        Button btnLoginCheck = findViewById(R.id.btn_sapie_login_check);

        if (edtLoginId == null || edtPassword == null || btnLoginCheck == null) return;

        // 現在の値を表示
        String savedId = org.androiddaisyreader.utils.SapiePreferences.getLoginId(this);
        String savedPw = org.androiddaisyreader.utils.SapiePreferences.getPassword(this);
        if (savedId != null && !savedId.isEmpty()) {
            edtLoginId.setText(savedId);
        }
        if (savedPw != null && !savedPw.isEmpty()) {
            edtPassword.setText(savedPw);
        }

        // フォーカスが外れた時に保存
        View.OnFocusChangeListener saveListener = (v, hasFocus) -> {
            if (!hasFocus) {
                String loginId = edtLoginId.getText().toString().trim();
                String password = edtPassword.getText().toString().trim();
                if (!loginId.isEmpty() && !password.isEmpty()) {
                    org.androiddaisyreader.utils.SapiePreferences.save(
                            getApplicationContext(), loginId, password);
                }
            }
        };
        edtLoginId.setOnFocusChangeListener(saveListener);
        edtPassword.setOnFocusChangeListener(saveListener);

        // 確認ボタン: ログイン確認して成功/失敗のダイアログを表示
        btnLoginCheck.setOnClickListener(v -> {
            String loginId = edtLoginId.getText().toString().trim();
            String password = edtPassword.getText().toString().trim();
            if (loginId.isEmpty() || password.isEmpty()) {
                showSapieLoginDialog(getString(R.string.sapie_login_failure));
                return;
            }
            org.androiddaisyreader.utils.SapiePreferences.save(
                    getApplicationContext(), loginId, password);

            Toast.makeText(DaisyReaderSettingActivity.this,
                    getString(R.string.sapie_login_checking), Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                final String message = checkSapieLogin(loginId, password);
                runOnUiThread(() -> showSapieLoginDialog(message));
            }).start();
        });
    }

    private String checkSapieLogin(String loginId, String password) {
        try (org.androiddaisyreader.sapieconvert.SapieLibraryClient client =
                     new org.androiddaisyreader.sapieconvert.SapieLibraryClient(loginId, password)) {
            client.login();
            return getString(R.string.sapie_login_success);
        } catch (Exception e) {
            return getString(R.string.sapie_login_failure);
        }
    }

    private void showSapieLoginDialog(String message) {
        new AlertDialog.Builder(DaisyReaderSettingActivity.this)
                .setTitle(R.string.sapie_settings_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    /**
     * 実験的機能セクション（ChattyLib / サピエ図書館）の折りたたみを設定する。
     * デフォルトは折りたたみ（非表示）。
     */
    private void settingExperimentalSection() {
        View header = findViewById(R.id.textViewExperimentalHeader);
        View content = findViewById(R.id.layoutExperimentalContent);
        if (header == null || content == null) return;

        header.setOnClickListener(v -> {
            if (content.getVisibility() == View.VISIBLE) {
                content.setVisibility(View.GONE);
            } else {
                content.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_DIRECTORY) {
            if (resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED) {
                mEditor.putString(Constants.STORAGE_ROOT, data
                        .getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR));
                mEditor.commit();
                Constants.folderRoot = data
                        .getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR);
//                Constants.folderContainMetadata = Constants.folderRoot + "/" + Constants.FOLDER_NAME + "/";
                Constants.folderContainMetadata = getFilesDir().getAbsolutePath() + "/";
                final TextView description = (TextView) findViewById(R.id.textView11);
                description.setText(Constants.folderRoot);
            } else {
                // Nothing selected
            }
        }
    }

    /**
     * Show table of colors.
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == 0) {
            LayoutInflater inflater = LayoutInflater.from(this);
            View dialogView1 = inflater.inflate(R.layout.dialog_selecter, null);

            GridView gridView = (GridView) dialogView1.findViewById(R.id.colorSelectGridView);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                    android.R.layout.simple_list_item_1) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    view.setBackgroundColor(COLOR_TALBE[position]);
                    return view;
                }
            };
            int sizeOfColorTable = COLOR_TALBE.length;
            for (int i = 0; i < sizeOfColorTable; i++) {
                adapter.add("");
            }
            gridView.setAdapter(adapter);
            gridView.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                    mCurrentTextColor = COLOR_TALBE[arg2];
                    mCurrentBackgroundColor = COLOR_TALBE[arg2];
                    mCurrentHighlightColor = COLOR_TALBE[arg2];
                    if (mChangeText) {
                        mTextColor.setBackgroundColor(mCurrentTextColor);
                        mEditor.putInt(Constants.TEXT_COLOR, mCurrentTextColor);
                    }
                    if (mChangeBackground) {
                        mBackgroundColor.setBackgroundColor(mCurrentBackgroundColor);
                        mEditor.putInt(Constants.BACKGROUND_COLOR, mCurrentBackgroundColor);
                    }
                    if (mChangeHighlight) {
                        mHighlightColor.setBackgroundColor(mCurrentHighlightColor);
                        mEditor.putInt(Constants.HIGHLIGHT_COLOR, mCurrentHighlightColor);
                    }
                    mEditor.commit();
                    dismissDialog(0);
                }
            });

            return new AlertDialog.Builder(this).setView(dialogView1)
                    .setNegativeButton(getString(R.string.cancel_bookmark), null).create();
        }
        return super.onCreateDialog(id);
    }

    /**
     * Handle input value for bookmark
     */
    private TextWatcher bookmarkTextWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            String strEnteredVal = "";
            if (mNumberOfBookmarks.getText() != null) {
                strEnteredVal = mNumberOfBookmarks.getText().toString();
            }

            if (!strEnteredVal.equals("")) {
                int num = Integer.parseInt(strEnteredVal);
                if (num <= 0) {
                    mNumberOfBookmarks.setText("");
                }
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            int value = 1;
            Editable numberOfBookmarks = mNumberOfBookmarks.getText();
            if (numberOfBookmarks != null && !numberOfBookmarks.toString().equals("")) {
                value = Integer.valueOf(mNumberOfBookmarks.getText().toString());
            }
            mEditor.putInt(Constants.NUMBER_OF_BOOKMARKS, value);
            mEditor.commit();
        }
    };

    /**
     * Handle input value for recent book
     */
    private TextWatcher recentBooksTextWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            String strEnteredVal = "";
            if (mNumberOfRecentBooks.getText() != null) {
                strEnteredVal = mNumberOfRecentBooks.getText().toString();
            }
            if (!strEnteredVal.equals("")) {
                int num = Integer.parseInt(strEnteredVal);
                if (num <= 0) {
                    mNumberOfRecentBooks.setText("");
                }
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            int value = 1;
            Editable numberOfRecentBooks = mNumberOfRecentBooks.getText();
            if (numberOfRecentBooks != null && !numberOfRecentBooks.toString().equals("")) {
                value = Integer.valueOf(mNumberOfRecentBooks.getText().toString());
            }
            mEditor.putInt(Constants.NUMBER_OF_RECENT_BOOKS, value);
            mEditor.commit();
        }
    };

    /**
     * Handle value seek bar brightness.
     */
    private OnSeekBarChangeListener seekBarBrightnessListener = new OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // set the brightness of this window
            layoutpars.screenBrightness = mBrightness / (float) BRIGHT_BAR;
            mEditor.putInt(Constants.BRIGHTNESS, mBrightness);
            mEditor.commit();
            // apply attribute changes to this window
            mWindow.setAttributes(layoutpars);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            // sets brightness variable based on the progress bar
            mBrightness = progress + DEFAULT_BRIGHTNESS;
        }
    };

    /**
     * Handle value seek bar size of text.
     */
    private OnSeekBarChangeListener seekBarSizeListener = new OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mEditor.putInt(Constants.FONT_SIZE, mFontsize);
            mEditor.commit();
            mFontSize.setTextSize(mFontsize);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mFontsize = progress + DEFAULT_FONT_SIZE;
        }
    };

}
