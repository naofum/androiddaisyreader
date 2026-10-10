package org.androiddaisyreader.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * アプリ内ファイル（filesDir/logs/app.log）にログを追記するユーティリティ。
 * ログは Logcat にも同時出力する。ファイルは約1MBでローテーションする。
 *
 * <p>エラー発生時には、メモリ上のリングバッファを使って「直近の操作履歴 + スナップショット」を
 * {@code error_snapshot.log} に書き出し、メール送信に利用できる。</p>
 *
 * <p>カスタム slf4j バインディング（org.slf4j.impl）と PrivateException から利用される。</p>
 */
public final class LogFile {

    private static final String TAG = "LogFile";
    private static final long MAX_SIZE = 1024 * 1024;
    private static final String SNAPSHOT_NAME = "error_snapshot.log";

    /** リングバッファに保持する最大エントリ数。 */
    private static final int RING_BUFFER_SIZE = 200;

    private static File logFile;
    private static File snapshotFile;
    private static final CircularLogBuffer ringBuffer = new CircularLogBuffer(RING_BUFFER_SIZE);

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
        snapshotFile = new File(dir, SNAPSHOT_NAME);
        writeHeader(context);
    }

    /**
     * 現在のログファイルを返す。未初期化の場合はnull。
     */
    public static synchronized File getFile() {
        return logFile;
    }

    /**
     * 直近のエラー時スナップショットファイルを返す。未初期化の場合はnull。
     */
    public static synchronized File getSnapshotFile() {
        return snapshotFile;
    }

    public static void v(String tag, String msg) {
        log(Log.VERBOSE, tag, msg, null, null);
    }

    public static void d(String tag, String msg) {
        log(Log.DEBUG, tag, msg, null, null);
    }

    public static void i(String tag, String msg) {
        log(Log.INFO, tag, msg, null, null);
    }

    public static void w(String tag, String msg) {
        log(Log.WARN, tag, msg, null, null);
    }

    public static void e(String tag, String msg) {
        log(Log.ERROR, tag, msg, null, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        log(Log.ERROR, tag, msg, t, null);
    }

    /**
     * 追加のキー・バリュー情報を同時に記録するログ。
     * ファイルとリングバッファの両方に出力される。
     *
     * @param tag    ログタグ
     * @param msg    メッセージ
     * @param extras 追加情報（プライバシーに注意。パスワード等は含めないこと）
     */
    public static void logContext(String tag, String msg, Map<String, String> extras) {
        log(Log.INFO, tag, msg, null, extras);
    }

    /**
     * エラー発生時のスナップショットを {@code error_snapshot.log} に上書き書き出す。
     * 直近のリングバッファ内容も含まれる。
     *
     * @param context    コンテキスト
     * @param screenName エラーが発生した画面名（Activity名等）
     * @param t          例外（任意）
     * @param extras     追加情報（URL、HTTPステータス等。プライバシー情報は含めない）
     */
    public static synchronized void writeErrorSnapshot(Context context, String screenName,
                                                        Throwable t, Map<String, String> extras) {
        if (snapshotFile == null) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder(4096);
            sb.append("=== Error Snapshot ===\n");
            sb.append("Time: ").append(TIME_FORMAT.format(new Date())).append('\n');
            sb.append("Screen: ").append(screenName != null ? screenName : "unknown").append('\n');

            appendEnvironmentInfo(context, sb);

            if (extras != null && !extras.isEmpty()) {
                sb.append("Extras:\n");
                for (Map.Entry<String, String> entry : extras.entrySet()) {
                    sb.append("  ").append(entry.getKey()).append("=")
                            .append(entry.getValue() != null ? entry.getValue() : "").append('\n');
                }
            }

            if (t != null) {
                sb.append("\nException: ").append(t.getClass().getName()).append('\n');
                sb.append("Message: ").append(t.getMessage() != null ? t.getMessage() : "null").append('\n');
                sb.append("Stack trace:\n").append(stackTrace(t)).append('\n');
            }

            sb.append("\n=== Recent logs (ring buffer) ===\n");
            for (LogEntry entry : ringBuffer.snapshot()) {
                sb.append(formatEntry(entry)).append('\n');
            }

            try (FileOutputStream fos = new FileOutputStream(snapshotFile, false)) {
                fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // スナップショット書き込み失敗は無視
        }
    }

    private static void log(int level, String tag, String msg, Throwable t, Map<String, String> extras) {
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

        // リングバッファ + ファイル出力
        LogEntry entry = new LogEntry(System.currentTimeMillis(), level, tag, msg, t, extras);
        ringBuffer.add(entry);
        appendToFile(entry);
    }

    private static synchronized void appendToFile(LogEntry entry) {
        if (logFile == null) {
            return;
        }
        try {
            rotateIfNeeded();
            String line = formatEntry(entry) + "\n";
            try (FileOutputStream fos = new FileOutputStream(logFile, true)) {
                fos.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // ログ書き込み失敗は無視（ログのために処理を落とさない）
        }
    }

    private static String formatEntry(LogEntry entry) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(TIME_FORMAT.format(new Date(entry.timestamp))).append(' ')
                .append(levelTag(entry.level)).append(' ')
                .append(entry.tag).append(": ")
                .append(entry.message == null ? "" : entry.message);
        if (entry.extras != null && !entry.extras.isEmpty()) {
            sb.append(" |");
            for (Map.Entry<String, String> e : entry.extras.entrySet()) {
                sb.append(' ').append(e.getKey()).append("=")
                        .append(e.getValue() != null ? e.getValue() : "");
            }
        }
        if (entry.throwable != null) {
            sb.append('\n').append(stackTrace(entry.throwable));
        }
        return sb.toString();
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
        appendToFile(new LogEntry(System.currentTimeMillis(), Log.INFO, TAG, header, null, null));
    }

    private static void appendEnvironmentInfo(Context context, StringBuilder sb) {
        if (context == null) {
            return;
        }
        sb.append("Locale: ").append(Locale.getDefault().toString()).append('\n');

        try {
            DisplayMetrics dm = new DisplayMetrics();
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                wm.getDefaultDisplay().getMetrics(dm);
                sb.append("Display: ").append(dm.widthPixels).append("x").append(dm.heightPixels)
                        .append(" density=").append(dm.densityDpi).append('\n');
            }
        } catch (Exception ignored) {
        }

        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo active = cm.getActiveNetworkInfo();
                String type = (active != null && active.isConnected()) ? active.getTypeName() : "disconnected";
                sb.append("Network: ").append(type).append('\n');
            }
        } catch (Exception ignored) {
        }

        try {
            Runtime runtime = Runtime.getRuntime();
            long memMb = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024);
            sb.append("AvailableMemoryMB: ").append(memMb).append('\n');
        } catch (Exception ignored) {
        }
    }

    /**
     * URL からクエリ文字列を除去し、プライバシー情報が漏れないようにする。
     * 不正な URL の場合は元の文字列を返す。
     */
    public static String sanitizeUrl(String urlString) {
        if (urlString == null) {
            return null;
        }
        try {
            URL url = new URL(urlString);
            StringBuilder sb = new StringBuilder();
            sb.append(url.getProtocol()).append("://").append(url.getHost());
            if (url.getPort() != -1) {
                sb.append(':').append(url.getPort());
            }
            sb.append(url.getPath());
            return sb.toString();
        } catch (MalformedURLException e) {
            return urlString;
        }
    }

    /**
     * 1件分のログエントリ。
     */
    private static final class LogEntry {
        final long timestamp;
        final int level;
        final String tag;
        final String message;
        final Throwable throwable;
        final Map<String, String> extras;

        LogEntry(long timestamp, int level, String tag, String message,
                 Throwable throwable, Map<String, String> extras) {
            this.timestamp = timestamp;
            this.level = level;
            this.tag = tag;
            this.message = message;
            this.throwable = throwable;
            this.extras = (extras != null) ? new LinkedHashMap<>(extras) : null;
        }
    }

    /**
     * スレッドセーフなリングバッファ。
     */
    private static final class CircularLogBuffer {
        private final int capacity;
        private final Deque<LogEntry> buffer;

        CircularLogBuffer(int capacity) {
            this.capacity = capacity;
            this.buffer = new ArrayDeque<>(capacity);
        }

        synchronized void add(LogEntry entry) {
            if (buffer.size() >= capacity) {
                buffer.pollFirst();
            }
            buffer.offerLast(entry);
        }

        synchronized Deque<LogEntry> snapshot() {
            return new ArrayDeque<>(buffer);
        }

        synchronized Iterator<LogEntry> iterator() {
            return new ArrayDeque<>(buffer).iterator();
        }
    }
}
