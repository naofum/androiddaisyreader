package org.androiddaisyreader.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.androiddaisyreader.aozoraconvert.AozoraClient;
import org.androiddaisyreader.aozoraconvert.model.AozoraBook;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * 青空文庫の図書一覧をバックグラウンドで同期する Worker。
 * MainActivity 起動時に OneTimeWorkRequest で実行される。
 * カタログCSV（約17,000件）をダウンロードし、DBに差分保存する。
 */
public class AozoraSyncWorker extends Worker {

    private static final String TAG = "AozoraSyncWorker";

    public AozoraSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        try (AozoraClient client = new AozoraClient()) {
            // カタログCSVダウンロード + パース
            List<AozoraBook> books = client.downloadCatalog();

            // DaisyBookInfo に変換
            List<DaisyBookInfo> daisyBooks = new ArrayList<>();
            int sort = 1;
            for (AozoraBook book : books) {
                DaisyBookInfo info = new DaisyBookInfo(
                        book.getUniqueId(),       // id: "{人物ID}_{作品ID}"
                        book.getWorkName(),        // title: 作品名
                        book.getPlaceholderPath(), // path: "aozora://{人物ID}/{作品ID}"
                        book.getAuthorName(),      // author: 著者名
                        book.getPublisher(),       // publisher: 出版社名
                        book.getStatusDate(),      // date: 状態の開始日
                        sort++
                );
                daisyBooks.add(info);
            }

            // DB に保存（トランザクション内で一括置換）
            SQLiteDaisyBookHelper sqlHelper = SQLiteDaisyBookHelper.getInstance(context);
            sqlHelper.replaceAllDaisyBooks(daisyBooks, Constants.TYPE_AOZORA_BOOK);

            Log.i(TAG, "Aozora sync completed: " + daisyBooks.size() + " books");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Aozora sync failed", e);
            if (getRunAttemptCount() >= 2) {
                Log.w(TAG, "Max retry attempts reached, giving up");
                return Result.failure();
            }
            return Result.retry();
        }
    }
}
