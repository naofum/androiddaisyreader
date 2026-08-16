import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mp3SectionDetector が保存したRMSエンベロープ（.rms.bin）を読み込み、
 * 無音のしきい値（最小無音時間）をスイープしてセクション数を調べる。
 *
 * <p>使い方:
 * <pre>
 *   java SectionSweep [rms.bin] [無音しきい値(RMS)] [目標セクション数(省略可)]
 * </pre>
 */
public class SectionSweep {

    public static void main(String[] args) throws Exception {
        String rmsPath = args.length > 0 ? args[0] : "10861541.rms.bin";
        double threshold = args.length > 1 ? Double.parseDouble(args[1]) : 80.0;
        int target = args.length > 2 ? Integer.parseInt(args[2]) : -1;

        RmsData data = loadRms(new File(rmsPath));
        double windowSeconds = data.windowSamples / (double) data.sampleRate;
        double totalSeconds = data.totalSamples / (double) data.sampleRate;

        System.out.println("ファイル: " + rmsPath);
        System.out.println("サンプリング周波数: " + data.sampleRate + " Hz, ウィンドウ: " + data.windowSamples
                + " samples (" + String.format(Locale.ROOT, "%.3f", windowSeconds) + "秒), 総時間: "
                + formatTime(totalSeconds));
        System.out.println("無音しきい値(RMS): " + threshold);
        System.out.println();

        System.out.println("最小無音時間 -> セクション数");
        for (int ms = 100; ms <= 3000; ms += 100) {
            int sections = countSections(data.rms, threshold, ms, windowSeconds);
            System.out.printf(Locale.ROOT, "  %5d ms -> %4d セクション%n", ms, sections);
        }

        if (target > 0) {
            double lo = 0.0;
            double hi = 60000.0;
            for (int i = 0; i < 80; i++) {
                double mid = (lo + hi) / 2.0;
                int sections = countSections(data.rms, threshold, mid, windowSeconds);
                if (sections >= target) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            int bestMs = (int) Math.round(lo);
            System.out.println();
            System.out.println("目標 " + target + " セクション -> 最小無音時間 約 " + bestMs + " ms");
            System.out.println("（そのときのセクション数: " + countSections(data.rms, threshold, bestMs, windowSeconds) + "）");
        }
    }

    static int countSections(double[] rms, double threshold, double minSilenceMs, double windowSeconds) {
        int minGapWindows = Math.max(1, (int) Math.ceil(minSilenceMs / 1000.0 / windowSeconds));
        int n = rms.length;
        List<int[]> gaps = new ArrayList<>();
        int i = 0;
        while (i < n) {
            if (rms[i] >= threshold) {
                i++;
                continue;
            }
            int start = i;
            while (i < n && rms[i] < threshold) {
                i++;
            }
            int end = i;
            if (end - start >= minGapWindows) {
                gaps.add(new int[]{start, end});
            }
        }
        int sections = 0;
        int sectionStart = 0;
        for (int[] g : gaps) {
            if (g[0] > sectionStart) {
                sections++;
            }
            sectionStart = g[1];
        }
        if (sectionStart < n) {
            sections++;
        }
        return sections;
    }

    private static RmsData loadRms(File file) throws IOException {
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int sampleRate = dis.readInt();
            int windowSamples = dis.readInt();
            int count = dis.readInt();
            long totalSamples = dis.readLong();
            double[] rms = new double[count];
            for (int i = 0; i < count; i++) {
                rms[i] = dis.readDouble();
            }
            return new RmsData(sampleRate, windowSamples, totalSamples, rms);
        }
    }

    private static String formatTime(double seconds) {
        long whole = (long) seconds;
        int h = (int) (whole / 3600);
        int m = (int) ((whole % 3600) / 60);
        int s = (int) (whole % 60);
        int ms = (int) Math.round((seconds - whole) * 1000);
        return String.format(Locale.ROOT, "%d:%02d:%02d.%03d", h, m, s, ms);
    }

    static class RmsData {
        final int sampleRate;
        final int windowSamples;
        final long totalSamples;
        final double[] rms;

        RmsData(int sampleRate, int windowSamples, long totalSamples, double[] rms) {
            this.sampleRate = sampleRate;
            this.windowSamples = windowSamples;
            this.totalSamples = totalSamples;
            this.rms = rms;
        }
    }
}
