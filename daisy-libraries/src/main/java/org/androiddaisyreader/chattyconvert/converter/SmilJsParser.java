package org.androiddaisyreader.chattyconvert.converter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * scripts/smil.js の cAudioItems オブジェクトを解析する。
 * <p>
 * エントリは以下の2種類を持つ。
 * <ul>
 *   <li>src 型: {src:'./sounds/soundNNNNN.mp3', begin:x, end:y} — その章の音声ファイルの定義</li>
 *   <li>ref 型: {ref:'sNNN_00001', begin:x, end:y} — 同一章の src 型エントリの音声ファイルを参照</li>
 * </ul>
 * 解析後、ref を解決して spanId → AudioItem のマップを構築する。
 */
public class SmilJsParser {

    private static final Pattern ENTRY_PATTERN =
            Pattern.compile("(s\\d+_\\d+)\\s*:\\s*\\{([^}]*)\\}");
    private static final Pattern SRC_PATTERN =
            Pattern.compile("src\\s*:\\s*'([^']+)'");
    private static final Pattern REF_PATTERN =
            Pattern.compile("ref\\s*:\\s*'([^']+)'");
    private static final Pattern BEGIN_PATTERN =
            Pattern.compile("begin\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern END_PATTERN =
            Pattern.compile("end\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");

    /**
     * smil.jsの内容を解析して、spanId → AudioItem のマップを返す。
     * refエントリは解決できた場合のみ結果に含まれる。
     *
     * @param smilJsContent scripts/smil.jsの内容
     * @return spanId をキーとする音声クリップのマップ
     */
    public Map<String, AudioItem> parse(String smilJsContent) {
        Map<String, RawEntry> rawEntries = parseRawEntries(smilJsContent);
        Map<String, AudioItem> result = new LinkedHashMap<>();
        for (Map.Entry<String, RawEntry> entry : rawEntries.entrySet()) {
            String key = entry.getKey();
            RawEntry raw = entry.getValue();
            String audioFile = resolveAudioFile(raw, rawEntries);
            if (audioFile == null) {
                continue;
            }
            String clipBegin = toClockValue(raw.begin);
            String clipEnd = toClockValue(raw.end);
            double endSeconds = raw.end;
            result.put(key, new AudioItem(audioFile, clipBegin, clipEnd, endSeconds));
        }
        return result;
    }

    private Map<String, RawEntry> parseRawEntries(String content) {
        Map<String, RawEntry> rawEntries = new LinkedHashMap<>();
        if (content == null) {
            return rawEntries;
        }
        Matcher matcher = ENTRY_PATTERN.matcher(content);
        while (matcher.find()) {
            String key = matcher.group(1);
            String body = matcher.group(2);
            String src = extractGroup(SRC_PATTERN, body);
            String ref = extractGroup(REF_PATTERN, body);
            double begin = extractDouble(BEGIN_PATTERN, body);
            double end = extractDouble(END_PATTERN, body);
            rawEntries.put(key, new RawEntry(src, ref, begin, end));
        }
        return rawEntries;
    }

    private String resolveAudioFile(RawEntry raw, Map<String, RawEntry> rawEntries) {
        if (raw.src != null) {
            return normalizePath(raw.src);
        }
        if (raw.ref != null) {
            RawEntry master = rawEntries.get(raw.ref);
            if (master != null && master.src != null) {
                return normalizePath(master.src);
            }
        }
        return null;
    }

    private static String normalizePath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private static String toClockValue(double seconds) {
        if (seconds == Math.rint(seconds) && !Double.isInfinite(seconds)) {
            return (long) seconds + "s";
        }
        String value = Double.toString(seconds);
        return value + "s";
    }

    private static double extractDouble(Pattern pattern, String body) {
        String value = extractGroup(pattern, body);
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String extractGroup(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static final class RawEntry {
        private final String src;
        private final String ref;
        private final double begin;
        private final double end;

        private RawEntry(String src, String ref, double begin, double end) {
            this.src = src;
            this.ref = ref;
            this.begin = begin;
            this.end = end;
        }
    }
}
