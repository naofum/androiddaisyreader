import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MP3ファイルをJLayerでデコードし、無音期間を検出してセクション情報をテキスト出力する。
 *
 * <p>使い方:
 * <pre>
 *   java Mp3SectionDetector [mp3パス] [無音しきい値(RMS)] [最小無音時間(ms)] [ウィンドウ(ms)]
 * </pre>
 * 出力は mp3 と同じフォルダに「&lt;ファイル名&gt;_sections.txt」として保存される。
 */
public class Mp3SectionDetector {

    public static void main(String[] args) throws Exception {
        String mp3Path = "10861541.mp3";
        boolean dump = false;
        double silenceThreshold = 100.0;
        int minSilenceMillis = 500;
        int windowMillis = 20;

        List<String> pos = new ArrayList<>();
        for (String a : args) {
            if (a.equals("-dump")) {
                dump = true;
            } else {
                pos.add(a);
            }
        }
        if (pos.size() > 0) mp3Path = pos.get(0);
        if (pos.size() > 1) silenceThreshold = Double.parseDouble(pos.get(1));
        if (pos.size() > 2) minSilenceMillis = Integer.parseInt(pos.get(2));
        if (pos.size() > 3) windowMillis = Integer.parseInt(pos.get(3));

        File mp3File = new File(mp3Path);
        AnalysisResult result = analyze(mp3File, windowMillis);

        if (dump) {
            File rmsFile = new File(mp3File.getParentFile(), baseName(mp3File) + ".rms.bin");
            dumpRms(rmsFile, result);
            System.out.println("RMSエンベロープを保存: " + rmsFile.getAbsolutePath());
            return;
        }

        List<SilenceGap> gaps = detectGaps(result, silenceThreshold, minSilenceMillis);
        List<Section> sections = buildSections(result, gaps);

        File outFile = new File(mp3File.getParentFile(), baseName(mp3File) + "_sections.txt");
        writeResult(outFile, mp3File, result, silenceThreshold, minSilenceMillis, windowMillis, gaps, sections);
        System.out.println("出力: " + outFile.getAbsolutePath());
    }

    /**
     * 解析結果（RMSエンベロープ）をバイナリで保存する。
     * SectionSweep から閾値スイープに再利用する。
     */
    private static void dumpRms(File file, AnalysisResult result) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {
            dos.writeInt(result.sampleRate);
            dos.writeInt(result.windowSamples);
            dos.writeInt(result.rms.length);
            dos.writeLong(result.totalSamples);
            for (double r : result.rms) {
                dos.writeDouble(r);
            }
        }
    }

    private static AnalysisResult analyze(File file, int windowMillis) throws IOException, BitstreamException {
        List<Double> rmsList = new ArrayList<>();
        int sampleRate = 0;
        int channels = 0;
        int windowSamples = 0;
        long totalSamples = 0;

        double sumSquares = 0.0;
        long windowCount = 0;

        try (InputStream in = new FileInputStream(file)) {
            Bitstream bitstream = new Bitstream(in);
            Decoder decoder = new Decoder();

            Header header = bitstream.readFrame();
            if (header != null) {
                sampleRate = header.frequency();
                channels = header.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
                windowSamples = Math.max(1, sampleRate * windowMillis / 1000);
            }

            long processed = 0;
            while (header != null) {
                SampleBuffer output;
                try {
                    output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                } catch (DecoderException e) {
                    break;
                }
                short[] buffer = output.getBuffer();
                int len = output.getBufferLength();
                for (int i = 0; i < len; i++) {
                    double s = buffer[i];
                    sumSquares += s * s;
                    windowCount++;
                    processed++;
                    if (windowCount == windowSamples) {
                        rmsList.add(Math.sqrt(sumSquares / windowCount));
                        totalSamples += windowCount;
                        sumSquares = 0.0;
                        windowCount = 0;
                    }
                }
                bitstream.closeFrame();
                try {
                    header = bitstream.readFrame();
                } catch (BitstreamException e) {
                    header = null;
                }
            }
            bitstream.close();

            if (windowCount > 0) {
                rmsList.add(Math.sqrt(sumSquares / windowCount));
                totalSamples += windowCount;
            }
        }

        double[] rms = new double[rmsList.size()];
        for (int i = 0; i < rmsList.size(); i++) {
            rms[i] = rmsList.get(i);
        }
        return new AnalysisResult(sampleRate, channels, windowSamples, totalSamples, rms);
    }

    private static List<SilenceGap> detectGaps(AnalysisResult result, double threshold, int minSilenceMillis) {
        int windowCount = result.rms.length;
        boolean[] silent = new boolean[windowCount];
        for (int i = 0; i < windowCount; i++) {
            silent[i] = result.rms[i] < threshold;
        }

        double windowSeconds = result.windowSamples / (double) result.sampleRate;
        int minGapWindows = Math.max(1, (int) Math.ceil((minSilenceMillis / 1000.0) / windowSeconds));

        List<SilenceGap> gaps = new ArrayList<>();
        int i = 0;
        while (i < windowCount) {
            if (!silent[i]) {
                i++;
                continue;
            }
            int start = i;
            while (i < windowCount && silent[i]) {
                i++;
            }
            int end = i;
            if (end - start >= minGapWindows) {
                gaps.add(new SilenceGap(
                        start * windowSeconds, end * windowSeconds, start, end));
            }
        }
        return gaps;
    }

    private static List<Section> buildSections(AnalysisResult result, List<SilenceGap> gaps) {
        double windowSeconds = result.windowSamples / (double) result.sampleRate;
        List<Section> sections = new ArrayList<>();
        int sectionStart = 0;
        for (SilenceGap gap : gaps) {
            if (gap.startWindow > sectionStart) {
                sections.add(new Section(sections.size() + 1,
                        sectionStart * windowSeconds, gap.startWindow * windowSeconds));
            }
            sectionStart = gap.endWindow;
        }
        if (sectionStart < result.rms.length) {
            sections.add(new Section(sections.size() + 1,
                    sectionStart * windowSeconds, result.rms.length * windowSeconds));
        }
        return sections;
    }

    private static void writeResult(File outFile, File mp3File, AnalysisResult result,
                                    double threshold, int minSilenceMillis, int windowMillis,
                                    List<SilenceGap> gaps, List<Section> sections) throws IOException {
        double totalSeconds = result.totalSamples / (double) result.sampleRate;

        double minRms = Double.POSITIVE_INFINITY;
        double maxRms = 0.0;
        double sumRms = 0.0;
        for (double r : result.rms) {
            if (r < minRms) minRms = r;
            if (r > maxRms) maxRms = r;
            sumRms += r;
        }
        double avgRms = result.rms.length > 0 ? sumRms / result.rms.length : 0.0;

        try (PrintWriter pw = new PrintWriter(outFile, StandardCharsets.UTF_8.name())) {
            pw.println("MP3セクション解析結果");
            pw.println("ファイル: " + mp3File.getName());
            pw.println("サンプリング周波数: " + result.sampleRate + " Hz");
            pw.println("チャンネル数: " + result.channels);
            pw.println("総サンプル数: " + result.totalSamples);
            pw.println("総時間: " + formatTime(totalSeconds));
            pw.println("解析ウィンドウ: " + windowMillis + " ms (" + result.windowSamples + " samples)");
            pw.println("無音しきい値(RMS): " + threshold);
            pw.println("最小無音時間: " + minSilenceMillis + " ms");
            pw.println();
            pw.println("RMS統計:");
            pw.println(String.format(Locale.ROOT, "  最小: %.3f", minRms));
            pw.println(String.format(Locale.ROOT, "  最大: %.3f", maxRms));
            pw.println(String.format(Locale.ROOT, "  平均: %.3f", avgRms));
            pw.println();

            pw.println("[無音期間] (" + gaps.size() + "件)");
            pw.println("No.\t開始\t終了\t長さ(秒)");
            int n = 1;
            for (SilenceGap gap : gaps) {
                pw.printf(Locale.ROOT, "%d\t%s\t%s\t%.3f%n",
                        n++, formatTime(gap.startSeconds), formatTime(gap.endSeconds),
                        gap.endSeconds - gap.startSeconds);
            }
            pw.println();

            pw.println("[セクション] (" + sections.size() + "件)");
            pw.println("No.\t開始\t終了\t長さ(秒)");
            for (Section section : sections) {
                pw.printf(Locale.ROOT, "%d\t%s\t%s\t%.3f%n",
                        section.index, formatTime(section.startSeconds), formatTime(section.endSeconds),
                        section.endSeconds - section.startSeconds);
            }
            pw.println();
            pw.println("セクション数: " + sections.size());
        }
    }

    private static String formatTime(double seconds) {
        if (seconds < 0 || Double.isNaN(seconds)) {
            seconds = 0;
        }
        long whole = (long) seconds;
        int hours = (int) (whole / 3600);
        int minutes = (int) ((whole % 3600) / 60);
        int secs = (int) (whole % 60);
        int millis = (int) Math.round((seconds - whole) * 1000);
        if (millis == 1000) {
            millis = 0;
            secs++;
        }
        return String.format(Locale.ROOT, "%d:%02d:%02d.%03d", hours, minutes, secs, millis);
    }

    private static String baseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static class AnalysisResult {
        final int sampleRate;
        final int channels;
        final int windowSamples;
        final long totalSamples;
        final double[] rms;

        AnalysisResult(int sampleRate, int channels, int windowSamples, long totalSamples, double[] rms) {
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.windowSamples = windowSamples;
            this.totalSamples = totalSamples;
            this.rms = rms;
        }
    }

    private static class SilenceGap {
        final double startSeconds;
        final double endSeconds;
        final int startWindow;
        final int endWindow;

        SilenceGap(double startSeconds, double endSeconds, int startWindow, int endWindow) {
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
            this.startWindow = startWindow;
            this.endWindow = endWindow;
        }
    }

    private static class Section {
        final int index;
        final double startSeconds;
        final double endSeconds;

        Section(int index, double startSeconds, double endSeconds) {
            this.index = index;
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
        }
    }
}
