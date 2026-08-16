package org.androiddaisyreader.mykohoconvert.pdf;

import java.util.ArrayList;
import java.util.List;

/**
 * PDFの1つのテキストブロック（段落）を表すデータクラス。
 * ブロック内の行は読み順（上→下、左→右）に並べ替え済み。
 */
public class TextBlock {

    private final int page;
    private final List<String> lines = new ArrayList<>();
    private double startTime;
    private double endTime;

    public TextBlock(int page) {
        this.page = page;
    }

    public int getPage() {
        return page;
    }

    public List<String> getLines() {
        return lines;
    }

    public void addLine(String line) {
        lines.add(line);
    }

    /** 音声（mp3）上の開始時刻（秒）。TextAudioAlignerで設定される。 */
    public double getStartTime() {
        return startTime;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
    }

    /** 音声（mp3）上の終了時刻（秒）。TextAudioAlignerで設定される。 */
    public double getEndTime() {
        return endTime;
    }

    public void setEndTime(double endTime) {
        this.endTime = endTime;
    }

    /** ブロックの文字数（改行を含む）。 */
    public int getCharCount() {
        int count = 0;
        for (String line : lines) {
            count += line.length() + 1;
        }
        return count;
    }

    @Override
    public String toString() {
        return "TextBlock{page=" + page + ", lines=" + lines.size()
                + ", start=" + startTime + ", end=" + endTime + "}";
    }
}
