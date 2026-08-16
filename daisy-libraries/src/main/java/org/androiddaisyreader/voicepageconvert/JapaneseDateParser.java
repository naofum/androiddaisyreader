package org.androiddaisyreader.voicepageconvert;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日本語テキストから日付を抽出するユーティリティ。
 *
 * <p>対応形式:
 * <ul>
 *   <li>西暦: {@code 2026年8月5日}（月・日は1桁可）</li>
 *   <li>和暦: {@code 令和8年8月5日} / {@code 平成31年…} / {@code 昭和63年…}（元年対応）</li>
 *   <li>月日のみ: {@code 8月5日}（年は現在年を仮定）</li>
 * </ul>
 * 「号」などの接尾は無視する。日付が見つからなければ null を返す。</p>
 */
public final class JapaneseDateParser {

    private static final Pattern WESTERN =
            Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern JAPANESE_ERA =
            Pattern.compile("(令和|平成|昭和)(\\d{1,2}|元)年(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern MONTH_DAY =
            Pattern.compile("(\\d{1,2})月(\\d{1,2})日");

    private JapaneseDateParser() {
    }

    /**
     * テキストから日付を抽出する。見つからなければ null。
     *
     * @param text 対象テキスト
     * @return 抽出された日付。見つからない場合は null
     */
    public static LocalDate parse(String text) {
        if (text == null) {
            return null;
        }
        Matcher western = WESTERN.matcher(text);
        if (western.find()) {
            return safeDate(
                    Integer.parseInt(western.group(1)),
                    Integer.parseInt(western.group(2)),
                    Integer.parseInt(western.group(3)));
        }
        Matcher era = JAPANESE_ERA.matcher(text);
        if (era.find()) {
            int year = eraToWestern(era.group(1), eraYearToInt(era.group(2)));
            return safeDate(year,
                    Integer.parseInt(era.group(3)),
                    Integer.parseInt(era.group(4)));
        }
        Matcher monthDay = MONTH_DAY.matcher(text);
        if (monthDay.find()) {
            int year = LocalDate.now().getYear();
            return safeDate(year,
                    Integer.parseInt(monthDay.group(1)),
                    Integer.parseInt(monthDay.group(2)));
        }
        return null;
    }

    private static int eraYearToInt(String value) {
        if ("元".equals(value)) {
            return 1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static int eraToWestern(String era, int eraYear) {
        switch (era) {
            case "令和":
                return 2018 + eraYear;
            case "平成":
                return 1988 + eraYear;
            case "昭和":
                return 1925 + eraYear;
            default:
                return LocalDate.now().getYear();
        }
    }

    private static LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }
}
