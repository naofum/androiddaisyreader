package org.androiddaisyreader.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.androiddaisyreader.machiiroconvert.MachiiroClient;
import org.androiddaisyreader.machiiroconvert.model.Municipality;
import org.androiddaisyreader.mykohoconvert.MyKohoClient;
import org.androiddaisyreader.mykohoconvert.MyKohoInfo;
import org.androiddaisyreader.model.DaisyBookInfo;
import org.androiddaisyreader.sqlite.SQLiteDaisyBookHelper;
import org.androiddaisyreader.utils.Constants;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 広報紙の図書一覧をバックグラウンドで同期する Worker。
 * MainActivity 起動時に OneTimeWorkRequest で実行される。
 */
public class KohoSyncWorker extends Worker {

    private static final String TAG = "KohoSyncWorker";

    public KohoSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();

        // Machiiro (TYPE 1)
        try (MachiiroClient client = new MachiiroClient()) {
            // 自治体一覧取得
            List<Municipality> municipalities = client.getMunicipalities();

            // DaisyBookInfo に変換
            List<DaisyBookInfo> daisyBooks = new ArrayList<>();
            int sort = 1;
            for (Municipality municipalitiy : municipalities) {
                DaisyBookInfo info = new DaisyBookInfo(
                        String.valueOf(municipalitiy.getId()),
                        municipalitiy.getName(),
                        "machiiro://" + municipalitiy.getId(),  // ダウンロード前のプレースホルダパス
                        municipalitiy.getName(),
                        Constants.MACHIIRO_SITE_NAME,  // publisher
                        "",
                        sort++
                );
                daisyBooks.add(info);
            }

            // DB に保存（トランザクション内で一括置換）
            SQLiteDaisyBookHelper sqlHelper = SQLiteDaisyBookHelper.getInstance(context);
            sqlHelper.replaceAllDaisyBooks(daisyBooks, Constants.TYPE_KOHO_BOOK);

            Log.i(TAG, "Machiiro sync completed: " + daisyBooks.size() + " books");
        } catch (Exception e) {
            Log.e(TAG, "Machiiro sync failed", e);
            if (getRunAttemptCount() >= 2) {
                Log.w(TAG, "Max retry attempts reached, giving up");
                return Result.failure();
            }
            return Result.retry();
        }

        // My広報 (TYPE 2)
        try {
            // municipalities.xml をリソースから読み込む
            InputStream xmlStream = context.getAssets().open("municipalities.xml");
            List<MyKohoInfo> myKohoMunicipalities = MyKohoClient.parseMunicipalitiesXml(xmlStream);
            xmlStream.close();

            // DaisyBookInfo に変換
            List<DaisyBookInfo> daisyBooks = new ArrayList<>();
            int sort = 1;
            for (MyKohoInfo info : myKohoMunicipalities) {
                DaisyBookInfo bookInfo = new DaisyBookInfo(
                        info.getLgCode(),
                        info.getMunicipality(),
                        info.getLgUrl(),  // XMLのuri要素をそのまま使用（mykoho:// または https://）
                        info.getMunicipality(),
                        (info.getLgUrl().startsWith("mykoho://") ? Constants.MYKOHO_SITE_NAME : Constants.KOHO_SITE_NAME),  // publisher
                        "",
                        sort++
                );
                daisyBooks.add(bookInfo);
            }

            // DB に保存（マチイロで置き換えた後の追記）
            SQLiteDaisyBookHelper sqlHelper = SQLiteDaisyBookHelper.getInstance(context);
            sqlHelper.appendAllDaisyBooks(daisyBooks, Constants.TYPE_KOHO_BOOK);

            Log.i(TAG, "MyKoho sync completed: " + daisyBooks.size() + " books");

        } catch (Exception e) {
            Log.e(TAG, "MyKoho sync failed", e);
            if (getRunAttemptCount() >= 2) {
                Log.w(TAG, "Max retry attempts reached, giving up");
                return Result.failure();
            }
            return Result.retry();
        }

        // Self published zip (TYPE 3)

        // Self published site (TYPE 4)

        // Tailored (TYPE 9)

        return Result.success();

    }
}
