package org.androiddaisyreader.mykohoconvert.pdf;

import java.util.List;

/**
 * PDFのテキストブロックとMP3の音声セクションを、文字数と時間の比例配分で突き合わせる。
 *
 * <p>各ブロックの文字数を集計し、音声セクションの総発話時間を文字数で按分して、
 * ブロックごとの開始・終了時刻を計算する。</p>
 */
public class TextAudioAligner {

    private TextAudioAligner() {
    }

    /**
     * ブロックごとの開始・終了時刻を計算して設定する。
     *
     * @param blocks   テキストブロック一覧（読み順）
     * @param sections 音声セクション一覧（時系列順）
     */
    public static void align(List<TextBlock> blocks, List<AudioSection> sections) {
        if (blocks.isEmpty() || sections.isEmpty()) {
            return;
        }

        double totalSpeech = 0.0;
        for (AudioSection s : sections) {
            totalSpeech += s.getDurationSeconds();
        }
        double totalDuration = sections.get(sections.size() - 1).getEndSeconds();

        int totalChars = 0;
        for (TextBlock b : blocks) {
            totalChars += b.getCharCount();
        }
        double rate = totalChars > 0 ? totalSpeech / totalChars : 0.0;

        int offset = 0;
        for (TextBlock b : blocks) {
            int startOffset = offset;
            offset += b.getCharCount();
            int endOffset = offset;
            b.setStartTime(speechToAbsolute(startOffset * rate, sections, totalDuration));
            b.setEndTime(speechToAbsolute(endOffset * rate, sections, totalDuration));
        }
    }

    private static double speechToAbsolute(double speechOffset, List<AudioSection> sections, double totalDuration) {
        double cum = 0;
        for (AudioSection s : sections) {
            double dur = s.getDurationSeconds();
            if (speechOffset < cum + dur) {
                return s.getStartSeconds() + (speechOffset - cum);
            }
            cum += dur;
        }
        return totalDuration;
    }
}
