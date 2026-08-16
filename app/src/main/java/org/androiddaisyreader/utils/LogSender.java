package org.androiddaisyreader.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.github.naofum.androiddaisyreader.R;

import java.io.File;

/**
 * ログファイルをメールなどで共有するユーティリティ。
 */
public final class LogSender {

    private static final String[] RECIPIENTS = {"naofum@gmail.com"};

    private LogSender() {
    }

    /**
     * ログファイルを共有インテント（メール等）で送信する。
     *
     * @param context コンテキスト
     */
    public static void shareLog(Context context) {
        File logFile = LogFile.getFile();
        if (logFile == null || !logFile.exists()) {
            Toast.makeText(context, R.string.send_log_no_log, Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(
                context, context.getPackageName() + ".fileprovider", logFile);

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.send_log_subject));
        intent.putExtra(Intent.EXTRA_EMAIL, RECIPIENTS);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(Intent.createChooser(intent, context.getString(R.string.send_log)));
    }
}
