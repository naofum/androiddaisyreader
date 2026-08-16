package org.androiddaisyreader.mykohoconvert.pdf;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * MP3ファイルをJLayerでデコードし、無音期間を検出して音声セクション（タイムライン）を返す。
 */
public class Mp3SectionDetector {

    private static final Logger logger = LoggerFactory.getLogger(Mp3SectionDetector.class);

    private Mp3SectionDetector() {
    }

    /**
     * MP3ファイルを解析し、無音期間で区切られた音声セクションの一覧を返す。
     *
     * @param mp3File          MP3ファイル
     * @param silenceThreshold 無音とみなすRMSしきい値
     * @param minSilenceMillis セクション区切りとみなす最小無音時間（ms）
     * @param windowMillis     解析ウィンドウ（ms）
     * @return 音声セクションの一覧（開始・終了時刻は秒）
     * @throws IOException デコードに失敗した場合
     */
    public static List<AudioSection> detect(File mp3File, double silenceThreshold,
                                            int minSilenceMillis, int windowMillis) throws IOException {
        AnalysisResult result;
        try {
            result = analyze(mp3File, windowMillis);
        } catch (BitstreamException e) {
            throw new IOException("MP3のデコードに失敗しました: " + mp3File, e);
        }
        List<SilenceGap> gaps = detectGaps(result, silenceThreshold, minSilenceMillis);
        List<AudioSection> sections = buildSections(result, gaps);
        logger.info("MP3セクション解析完了: {}セクション ({}件の無音期間)",
                sections.size(), gaps.size());
        return sections;
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

    private static List<AudioSection> buildSections(AnalysisResult result, List<SilenceGap> gaps) {
        double windowSeconds = result.windowSamples / (double) result.sampleRate;
        List<AudioSection> sections = new ArrayList<>();
        int sectionStart = 0;
        for (SilenceGap gap : gaps) {
            if (gap.startWindow > sectionStart) {
                sections.add(new AudioSection(
                        sectionStart * windowSeconds, gap.startWindow * windowSeconds));
            }
            sectionStart = gap.endWindow;
        }
        if (sectionStart < result.rms.length) {
            sections.add(new AudioSection(
                    sectionStart * windowSeconds, result.rms.length * windowSeconds));
        }
        return sections;
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
}
