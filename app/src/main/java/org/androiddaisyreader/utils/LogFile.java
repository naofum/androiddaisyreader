package org.androiddaisyreader.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * アプリ内ファイル（filesDir/logs/app.log）にログを追記するユーティリティ。
 * ログは Logcat にも同時出力する。ファイルは約1MBでローテーションする。
 *
 * <p>カスタム slf4j バインディング（org.slf4j.impl）と PrivateException から利用される。</p>
 */
public final class LogFile {

    private static final String TAG = "LogFile";
    private static final long MAX_SIZE = 1024 * 1024;

    private static File logFile;
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private LogFile() {
    }

    /**
     * ログファイルを初期化する。初回呼び出し時にヘッダー（端末情報）を書き込む。
     */
    public static synchronized void init(Context context) {
        if (logFile != null) {
            return;
        }
        File dir = new File(context.getFilesDir(), "logs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        logFile = new File(dir, "app.log");
        writeHeader(context);
    }

    /**
     * 現在のログファイルを返す。未初期化の場合はnull。
     */
    public static synchronized File getFile() {
        return logFile;
    }

    public static void v(String tag, String msg) {
        log(Log.VERBOSE, tag, msg, null);
    }

    public static void d(String tag, String msg) {
        log(Log.DEBUG, tag, msg, null);
    }

    public static void i(String tag, String msg) {
        log(Log.INFO, tag, msg, null);
    }

    public static void w(String tag, String msg) {
        log(Log.WARN, tag, msg, null);
    }

    public static void e(String tag, String msg) {
        log(Log.ERROR, tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        log(Log.ERROR, tag, msg, t);
    }

    private static void log(int level, String tag, String msg, Throwable t) {
        // Logcat 出力（従来動作を維持）
        switch (level) {
            case Log.VERBOSE:
                Log.v(tag, msg, t);
                break;
            case Log.DEBUG:
                Log.d(tag, msg, t);
                break;
            case Log.INFO:
                Log.i(tag, msg, t);
                break;
            case Log.WARN:
                Log.w(tag, msg, t);
                break;
            case Log.ERROR:
            default:
                Log.e(tag, msg, t);
                break;
        }

        // ファイル出力
        appendToFile(level, tag, msg, t);
    }

    private static synchronized void appendToFile(int level, String tag, String msg, Throwable t) {
        if (logFile == null) {
            return;
        }
        try {
            rotateIfNeeded();
            StringBuilder sb = new StringBuilder(256);
            sb.append(TIME_FORMAT.format(new Date())).append(' ')
                    .append(levelTag(level)).append(' ')
                    .append(tag).append(": ").append(msg == null ? "" : msg);
            if (t != null) {
                sb.append('\n').append(stackTrace(t));
            }
            sb.append('\n');
            try (FileOutputStream fos = new FileOutputStream(logFile, true)) {
                fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // ログ書き込み失敗は無視（ログのために処理を落とさない）
        }
    }

    private static void rotateIfNeeded() {
        if (logFile.length() > MAX_SIZE) {
            File old = new File(logFile.getParentFile(), "app.log.1");
            if (old.exists()) {
                old.delete();
            }
            logFile.renameTo(old);
        }
    }

    private static String levelTag(int level) {
        switch (level) {
            case Log.VERBOSE:
                return "V";
            case Log.DEBUG:
                return "D";
            case Log.INFO:
                return "I";
            case Log.WARN:
                return "W";
            case Log.ERROR:
            default:
                return "E";
        }
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static void writeHeader(Context context) {
        String version = "unknown";
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = pi.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        String header = "=== DaisyReader " + version + " | " + Build.MANUFACTURER + " " + Build.MODEL
                + " | Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ") ===";
        appendToFile(Log.INFO, TAG, header, null);
    }
}
