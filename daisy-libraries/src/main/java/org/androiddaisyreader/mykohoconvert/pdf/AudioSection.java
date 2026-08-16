package org.androiddaisyreader.mykohoconvert.pdf;

/**
 * MP3の無音検出から得られた1つの音声セクション（発話区間）を表すデータクラス。
 */
public class AudioSection {

    private final double startSeconds;
    private final double endSeconds;

    public AudioSection(double startSeconds, double endSeconds) {
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
    }

    public double getStartSeconds() {
        return startSeconds;
    }

    public double getEndSeconds() {
        return endSeconds;
    }

    public double getDurationSeconds() {
        return endSeconds - startSeconds;
    }

    @Override
    public String toString() {
        return "AudioSection{start=" + startSeconds + ", end=" + endSeconds + "}";
    }
}
