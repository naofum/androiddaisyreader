package org.androiddaisyreader.util;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Header;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * MP3の総再生時間を取得する軽量ユーティリティ。
 *
 * <p>JLayerのフレームヘッダだけを走査して各フレームの長さを合算するため、
 * PCMデコードを行わず高速に再生時間を求められる（VBRにも対応）。</p>
 */
public final class Mp3Duration {

    private Mp3Duration() {
    }

    /**
     * MP3ファイルの総再生時間（秒）を返す。
     *
     * @param mp3File MP3ファイル
     * @return 再生時間（秒）。ヘッダが読めない場合は 0。
     */
    public static double readSeconds(File mp3File) {
        Bitstream bitstream = null;
        try {
            InputStream in = new FileInputStream(mp3File);
            bitstream = new Bitstream(in);
            double totalMs = 0.0;
            Header header = readFrameSafe(bitstream);
            while (header != null) {
                totalMs += header.ms_per_frame();
                bitstream.closeFrame();
                header = readFrameSafe(bitstream);
            }
            return totalMs / 1000.0;
        } catch (IOException e) {
            return 0.0;
        } finally {
            if (bitstream != null) {
                try {
                    bitstream.close();
                } catch (BitstreamException ignored) {
                    // クローズ失敗は無視
                }
            }
        }
    }

    private static Header readFrameSafe(Bitstream bitstream) {
        try {
            return bitstream.readFrame();
        } catch (BitstreamException e) {
            return null;
        }
    }
}
