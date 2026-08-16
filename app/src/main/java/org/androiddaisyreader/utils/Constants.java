/**
 * This is constants
 * @author LogiGear
 * @date 2013.03.05
 */

package org.androiddaisyreader.utils;

/**
 * The Class Constants.
 */
public class Constants {
    /** The Constant MY_DATA_CHECK_CODE. */
    public static final int MY_DATA_CHECK_CODE = 1234;
    /** The Constant REQUEST_DIRECTORY. */
    public static final int REQUEST_DIRECTORY = 5678;
    /**
     * This key will help you to get value of number recent books from
     * SharedPreferences
     */
    public static final String NUMBER_OF_RECENT_BOOKS = "numberOfRecentBooks";
    /**
     * This key will help you to get value of number bookmarks from
     * SharedPreferences
     */
    public static final String NUMBER_OF_BOOKMARKS = "numberOfBookmarks";
    /** This key will help you to get value of daisy path from SharedPreferences */
    public static final String DAISY_PATH = "daisyPath";
    /**
     * This key will help you to get value of list content from
     * SharedPreferences
     */
    public static final String LIST_CONTENTS = "listContents";
    /**
     * This key will help you to get value of position section from
     * SharedPreferences
     */
    public static final String POSITION_SECTION = "positionSection";
    /**
     * This key will help you to get value of position sentence from
     * SharedPreferences
     */
    public static final String POSITION_SENTENCE = "positionSentence";
    /**
     * This key will help you to get value of audio file name from
     * SharedPreferences
     */
    public static final String AUDIO_FILE_NAME = "audioFileName";
    /**
     * This key will help you to get value of target activity from
     * SharedPreferences
     */
    public static final String TARGET_ACTIVITY = "targetActivity";
    /** This key will help you to get value of sentence from SharedPreferences */
    public static final String SENTENCE = "sentence";
    /** This key will help you to get value of time from SharedPreferences */
    public static final String TIME = "time";
    /** This key will help you to get value of section from SharedPreferences */
    public static final String SECTION = "section";
    /** This key will help you to get value of position from SharedPreferences */
    public static final String POSITION = "position";
    /** This key will help you to get value of brightness from SharedPreferences */
    public static final String BRIGHTNESS = "brightness";
    /**
     * This key will help you to get value of current brightness from
     * SharedPreferences
     */
    public static final String CURRRENT_BRIGHTNESS = "currentBrightness";
    /** This key will help you to get value of font size from SharedPreferences */
    public static final String FONT_SIZE = "fontsize";
    /** This key will help you to get value of text color from SharedPreferences */
    public static final String TEXT_COLOR = "textColor";
    /**
     * This key will help you to get value of background color from
     * SharedPreferences
     */
    public static final String BACKGROUND_COLOR = "backgroundColor";
    /**
     * This key will help you to get value of high light color from
     * SharedPreferences
     */
    public static final String HIGHLIGHT_COLOR = "highlightColor";
    /** This key will help you to get value of night mode from SharedPreferences */
    public static final String NIGHT_MODE = "nightMode";
    /** storage root */
    public static final String STORAGE_ROOT = "storageRoot";
    /** SAF scan folder URI (persistable) */
    public static final String SAF_SCAN_FOLDER_URI = "safScanFolderUri";
    /** File ncc of daisy book format 2.02 not cap */
    public static final String FILE_NCC_NAME_NOT_CAPS = "ncc.html";
    /** File ncc of daisy book format 2.02 with cap */
    public static final String FILE_NCC_NAME_CAPS = "NCC.HTML";
    /** The prefix of audio temp file */
    public static final String PREFIX_AUDIO_TEMP_FILE = "_DAISYTEMPAUDIO_";
    /** The suffix of audio temp file */
    public static final String SUFFIX_AUDIO_TEMP_FILE = ".mp3";
    /** The suffix of audio zip file */
    public static final String SUFFIX_ZIP_FILE = ".zip";
    /** The suffix of epub file */
    public static final String SUFFIX_EPUB_FILE = ".epub";
    /** The prefix of content */
    public static final String PREFIX_CONTENT_SCHEME = "content:";
    /**
     * This key will help you to get value of link website from
     * SharedPreferences
     */
    public static final String LINK_WEBSITE = "link";
    /**
     * This key will help you to get value of name website from
     * SharedPreferences
     */
    public static final String NAME_WEBSITE = "name";
    /** This key will help you to get value of service when it is done. */
    public static final String SERVICE_DONE = "serviceDone";
    /** the fontsize default */
    public static final int FONTSIZE_DEFAULT = 20;
    /** The number bookmark default */
    public static final int NUMBER_OF_BOOKMARK_DEFAULT = 10;
    /** the number recent book default */
    public static final int NUMBER_OF_RECENTBOOK_DEFAULT = 10;
    /** The Constant TIME_WAIT_FOR_CLICK_SECTION. */
    public static final int TIME_WAIT_FOR_CLICK_SECTION = 2000;
    /** The Constant TIME_WAIT_FOR_CLICK_SENTENCE. */
    public static final int TIME_WAIT_FOR_CLICK_SENTENCE = 300;
    /** The Constant TIME_WAIT_TO_EXIT_APPLICATION. */
    public static final int TIME_WAIT_TO_EXIT_APPLICATION = 2300;
    /** The Constant DAISY_202_FORMAT. */
    public static final int DAISY_202_FORMAT = 2;
    /** The Constant DAISY_30_FORMAT. */
    public static final int DAISY_30_FORMAT = 3;
    /** The Constant DAISY_30_FORMAT. */
    public static final int EPUB3_FORMAT = 31;

    /** Id item menu of sub menu */
    public static final int SUBMENU_MENU = 1;
    /** Id item menu library of sub menu */
    public static final int SUBMENU_LIBRARY = 2;
    /** Id item menu bookmark of sub menu */
    public static final int SUBMENU_BOOKMARKS = 3;
    /** Id item menu table of content of sub menu */
    public static final int SUBMENU_TABLE_OF_CONTENTS = 4;
    /** Id item menu simple mode of sub menu */
    public static final int SUBMENU_SIMPLE_MODE = 5;
    /** Id item menu search of sub menu */
    public static final int SUBMENU_SEARCH = 6;
    /** Id item menu setting of sub menu */
    public static final int SUBMENU_SETTINGS = 7;
    /** Id item menu about of sub menu */
    public static final int SUBMENU_ABOUT = 8;
    /** Id item menu contact of sub menu */
    public static final int SUBMENU_GOOGLE_PLAY = 9;
    /** Id item menu contact of sub menu */
    public static final int SUBMENU_CONTACT = 10;
    /** Id item menu send log of sub menu */
    public static final int SUBMENU_SEND_LOG = 11;
    /** Id item menu privacy policy of sub menu */
    public static final int SUBMENU_PRIVACY_POLICY = 12;
    /** Id item menu license of sub menu */
    public static final int SUBMENU_LICENSE = 13;

    // All message on simple mode activity.
    /** Id of message "simple mode" to speak by tts on simple mode */
    public static final int SIMPLE_MODE = 3;
    /** Id of message "error wrong format audio" to speak by tts on simple mode */
    public static final int ERROR_WRONG_FORMAT_AUDIO = 4;
    /** Id of message "error no audio found" to speak by tts on simple mode */
    public static final int ERROR_NO_AUDIO_FOUND = 5;
    /** Id of message "at the end" to speak by tts on simple mode */
    public static final int AT_THE_END = 6;
    /** Id of message "at the begin" to speak by tts on simple mode */
    public static final int AT_THE_BEGIN = 7;
    /** Id of message "next section" to speak by tts on simple mode */
    public static final int NEXT_SECTION = 8;
    /** Id of message "previous section" to speak by tts on simple mode */
    public static final int PREVIOUS_SECTION = 9;
    /** Id of message "next sentence" to speak by tts on simple mode */
    public static final int NEXT_SENTENCE = 10;
    /** Id of message "previous sentence" to speak by tts on simple mode */
    public static final int PREVIOUS_SENTENCE = 11;
    /** Id of message "play" to speak by tts on simple mode */
    public static final int PLAY = 12;
    /** Id of message "pause" to speak by tts on simple mode */
    public static final int PAUSE = 13;
    /** Id of clear cache on recent book actvity */
    public static final int CLEAR_RECENT = 14;
    // The type of daisy book in file metadata.xml will help us to distinguish
    // when we load daisy book from metadata file
    public static final String TYPE_DOWNLOAD_BOOK = "1";
    public static final String TYPE_RECENT_BOOK = "2";
    public static final String TYPE_SCAN_BOOK = "3";
    public static final String TYPE_DOWNLOADED_BOOK = "4";
    public static final String TYPE_CHATTYLIB_BOOK = "5";

    // ChattyLib
    public static final String CHATTYLIB_SITE_NAME = "ChattyLib";
    public static final String CHATTYLIB_SITE_URL = "chattylib://library";

    // 青空文庫
    public static final String TYPE_AOZORA_BOOK = "6";
    public static final String AOZORA_SITE_NAME = "青空文庫";
    public static final String AOZORA_SITE_URL = "aozora://library";
    public static final String AOZORA_CATALOG_URL = "https://www.aozora.gr.jp/index_pages/list_person_all_utf8.zip";

    // 広報紙
    public static final String TYPE_KOHO_BOOK = "7";
    public static final String KOHO_SITE_NAME = "広報紙";
    public static final String KOHO_SITE_URL = "koho://library";

    // マチイロ
    public static final String TYPE_MACHIIRO_BOOK = "8";
    public static final String MACHIIRO_SITE_NAME = "マチイロ";
    public static final String MACHIIRO_SITE_URL = "machiiro://library";

    // MY広報
    public static final String TYPE_MYKOHO_BOOK = "9";
    public static final String MYKOHO_SITE_NAME = "MY広報";
    public static final String MYKOHO_SITE_URL = "mykoho://library";

    // サピエ図書館
    public static final String TYPE_SAPIE_BOOK = "10";
    public static final String SAPIE_SITE_NAME = "サピエ図書館";
    public static final String SAPIE_SITE_URL = "sapie://library";

    // All node in metadata.xml file.
    public static final String ATT_BOOKS = "books";
    public static final String ATT_WEBSITE = "website";
    public static final String ATT_URL = "url";
    public static final String ATT_NAME = "name";
    public static final String ATT_BOOK = "book";
    public static final String ATT_LINK = "link";
    public static final String ATT_PATH = "path";
    public static final String ATT_TITLE = "title";
    public static final String ATT_AUTHOR = "author";
    public static final String ATT_PUBLISHER = "publisher";
    public static final String ATT_DATE = "date";
    public static final String ATT_TYPE = "type";
    /** The name of file was saved to help load all books to download */
    public static final String META_DATA_FILE_NAME = "metadata.xml";
    /**
     * The name of file was saved to help increase performance when we load book
     * from sdcard
     */
    public static final String META_DATA_SCAN_BOOK_FILE_NAME = "metadata_scanbook.xml";
    /** The name of folder contains all book downloaded */
    public static final String FOLDER_DOWNLOADED = "/download";
    /** the name of folder contains metadata.xml, metadata_scanbook.xml */
    public static final String FOLDER_NAME = "dataDaisyBooks";
    /** the full path of folder contains metadata.xml, metadata_scanbook.xml */
    public static String folderContainMetadata = "";
    /** the full path of root folder for storage*/
    public static String folderRoot = "";
    /** The Constant DAISY_TEMP_MP3. */
    public static final String DAISY_TEMP_MP3 = "daisyTemp.mp3";
    /** If device connected by wifi, the value is 1 */
    public static final int CONNECT_TYPE_WIFI = 1;
    /** If device connected by cellular, the value is 2 */
    public static final int CONNECT_TYPE_MOBILE = 2;
    /** If device is not connected, the value is 2 */
    public static final int CONNECT_TYPE_NOT_CONNECTED = 0;

    // The following section are used to record statistical data for Analytics
    public static final String RECORD_BOOK_DOWNLOAD_COMPLETED = "BookDownloadCompleted";
    public static final String RECORD_BOOK_DOWNLOAD_FAILED = "BookDownloadFailed";

    /** 読み上げ言語設定キー（オーディオなし書籍のTTS用） */
    public static final String TTS_READ_ALOUD_LANGUAGE = "ttsReadAloudLanguage";
    /** 読み上げ速度設定キー（オーディオなし書籍のTTS用） 0.5〜3.0 */
    public static final String TTS_READ_ALOUD_SPEED = "ttsReadAloudSpeed";
    public static final float TTS_SPEED_DEFAULT = 1.0f;

    /** ルビ表示モード設定キー: "ruby"=ルビを使用, "base"=底字を使用 */
    public static final String RUBY_DISPLAY_MODE = "rubyDisplayMode";
    public static final String RUBY_MODE_RUBY = "ruby";
    public static final String RUBY_MODE_BASE = "base";
}
