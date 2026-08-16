package org.androiddaisyreader.chattyconvert.converter;

/**
 * smil.jsのcAudioItemsから解決された、単一の読み上げスパンに対応する音声クリップ。
 */
public class AudioItem {

    private final String audioFile;
    private final String clipBegin;
    private final String clipEnd;
    private final double endSeconds;

    /**
     * @param audioFile   音声ファイルのパス（例: sounds/sound00001.mp3）
     * @param clipBegin   SMIL clock-value形式のクリップ開始位置（例: 0.030s）
     * @param clipEnd     SMIL clock-value形式のクリップ終了位置（例: 2.820s）
     * @param endSeconds  終了位置の秒（章のduration計算用）
     */
    public AudioItem(String audioFile, String clipBegin, String clipEnd, double endSeconds) {
        this.audioFile = audioFile;
        this.clipBegin = clipBegin;
        this.clipEnd = clipEnd;
        this.endSeconds = endSeconds;
    }

    public String getAudioFile() {
        return audioFile;
    }

    public String getClipBegin() {
        return clipBegin;
    }

    public String getClipEnd() {
        return clipEnd;
    }

    public double getEndSeconds() {
        return endSeconds;
    }

    @Override
    public String toString() {
        return "AudioItem{audioFile='" + audioFile + "', clipBegin='" + clipBegin
                + "', clipEnd='" + clipEnd + "'}";
    }
}
