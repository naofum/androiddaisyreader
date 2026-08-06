package org.androiddaisyreader.apps;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.model.ZippedBookInfo;
import org.androiddaisyreader.player.IntentController;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.CacheHelper;
import org.androiddaisyreader.utils.Constants;
import org.androiddaisyreader.utils.DaisyBookUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

public class DaisyEbookLauncher extends AppCompatActivity {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String uri = getIntent().getDataString();
        if (uri == null) {
            return;
        }

        String pathToOpen = uri;
        try {
            if (uri.startsWith(Constants.PREFIX_CONTENT_SCHEME)) {
                // content:// URI はキャッシュにコピーし、キャッシュのローカルパスで渡す
                File cachedFile = CacheHelper.copyToCache(getApplicationContext(), uri);
                pathToOpen = cachedFile.getAbsolutePath();
            } else if (uri.startsWith("file://")) {
                // file:// URI はローカルパスに変換
                pathToOpen = android.net.Uri.parse(uri).getPath();
            }
            // それ以外はローカルパスとしてそのまま使用

            // 書籍メタデータを読み取り、最近の書籍としてDBに登録
            registerBookMetadata(pathToOpen);

        } catch (Exception e) {
            PrivateException ex = new PrivateException(e, getApplicationContext(), uri);
            ex.writeLogException();
        }

        // push to reader activity
        IntentController intentController = new IntentController(
                DaisyEbookLauncher.this);
        intentController.pushToDaisyEbookReaderIntent(pathToOpen);
    }

    /**
     * 書籍メタデータをZIPから読み取り、最近の書籍としてDBに登録する。
     */
    private void registerBookMetadata(String path) {
        try {
            DaisyBookInfo bookInfo = null;

            // ZIPストリームからメタデータを読み取り
            if (path.endsWith(Constants.SUFFIX_ZIP_FILE) || path.endsWith(Constants.SUFFIX_EPUB_FILE)) {
                try (InputStream input = new BufferedInputStream(new FileInputStream(path))) {
                    bookInfo = ZippedBookInfo.readFromZipStream(input, Charset.forName("MS932"));
                } catch (IllegalArgumentException iae) {
                    try (InputStream input = new BufferedInputStream(new FileInputStream(path))) {
                        bookInfo = ZippedBookInfo.readFromZipStream(input, Charset.defaultCharset());
                    }
                }
            }

            if (bookInfo != null) {
                bookInfo.setPath(path);

                // 最近の書籍に登録
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                int numberOfRecentBooks = prefs.getInt(Constants.NUMBER_OF_RECENT_BOOKS,
                        Constants.NUMBER_OF_RECENTBOOK_DEFAULT);
                SQLiteDaisyBookHelper sql = SQLiteDaisyBookHelper.getInstance(this);
                DaisyBookUtil.addRecentBookToSQLite(bookInfo, numberOfRecentBooks, sql);
            }
        } catch (Exception e) {
            // メタデータ登録失敗は致命的ではない - 書籍は開ける
            PrivateException ex = new PrivateException(e, getApplicationContext(), path);
            ex.writeLogException();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
