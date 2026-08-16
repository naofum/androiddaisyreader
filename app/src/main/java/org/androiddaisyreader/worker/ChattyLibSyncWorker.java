package org.androiddaisyreader.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.androiddaisyreader.chattyconvert.ChattyLibraryClient;
import org.androiddaisyreader.chattyconvert.model.Book;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.ChattyLibPreferences;
import org.androiddaisyreader.utils.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * ChattyLib の図書一覧をバックグラウンドで同期する Worker。
 * MainActivity 起動時に OneTimeWorkRequest で実行される。
 */
public class ChattyLibSyncWorker extends Worker {

    private static final String TAG = "ChattyLibSyncWorker";

    public ChattyLibSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        // 認証情報を取得
        if (!ChattyLibPreferences.hasCredentials(context)) {
            Log.d(TAG, "ChattyLib credentials not configured, skipping sync");
            return Result.success();
        }

        String loginId = ChattyLibPreferences.getLoginId(context);
        String password = ChattyLibPreferences.getPassword(context);

        try (ChattyLibraryClient client = new ChattyLibraryClient(loginId, password)) {
            // ログイン + 図書一覧取得
            client.login();
            List<Book> books = client.search();

            // DaisyBookInfo に変換
            List<DaisyBookInfo> daisyBooks = new ArrayList<>();
            int sort = 1;
            for (Book book : books) {
                DaisyBookInfo info = new DaisyBookInfo(
                        String.valueOf(book.getId()),
                        book.getTitle(),
                        "chattylib://" + book.getId(),  // ダウンロード前のプレースホルダパス
                        book.getAuthor(),
                        "",  // publisher
                        book.getRegisteredDate(),
                        sort++
                );
                daisyBooks.add(info);
            }

            // DB に保存（トランザクション内で一括置換）
            SQLiteDaisyBookHelper sqlHelper = SQLiteDaisyBookHelper.getInstance(context);
            sqlHelper.replaceAllDaisyBooks(daisyBooks, Constants.TYPE_CHATTYLIB_BOOK);

            Log.i(TAG, "ChattyLib sync completed: " + daisyBooks.size() + " books");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "ChattyLib sync failed", e);
            if (getRunAttemptCount() >= 2) {
                Log.w(TAG, "Max retry attempts reached, giving up");
                return Result.failure();
            }
            return Result.retry();
        }
    }
}
