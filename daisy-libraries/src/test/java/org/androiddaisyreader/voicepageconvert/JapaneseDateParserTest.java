package org.androiddaisyreader.voicepageconvert;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JapaneseDateParserTest {

    @Test
    void parse_westernFullDate() {
        assertEquals(LocalDate.of(2026, 8, 5), JapaneseDateParser.parse("2026年8月5日"));
    }

    @Test
    void parse_westernSingleDigitMonthAndDay() {
        assertEquals(LocalDate.of(2026, 1, 7), JapaneseDateParser.parse("2026年1月7日"));
    }

    @Test
    void parse_westernDateWithSuffix() {
        assertEquals(LocalDate.of(2026, 8, 5),
                JapaneseDateParser.parse("市報こだいら2026年8月5日号音声版"));
    }

    @Test
    void parse_reiwa() {
        assertEquals(LocalDate.of(2026, 8, 5), JapaneseDateParser.parse("令和8年8月5日"));
    }

    @Test
    void parse_reiwaGannen() {
        assertEquals(LocalDate.of(2019, 5, 1), JapaneseDateParser.parse("令和元年5月1日"));
    }

    @Test
    void parse_heisei() {
        assertEquals(LocalDate.of(2019, 1, 1), JapaneseDateParser.parse("平成31年1月1日"));
    }

    @Test
    void parse_showa() {
        assertEquals(LocalDate.of(1988, 1, 7), JapaneseDateParser.parse("昭和63年1月7日"));
    }

    @Test
    void parse_monthDayOnly() {
        LocalDate expected = LocalDate.of(LocalDate.now().getYear(), 8, 5);
        assertEquals(expected, JapaneseDateParser.parse("8月5日"));
    }

    @Test
    void parse_noDate() {
        assertNull(JapaneseDateParser.parse("音声版"));
    }

    @Test
    void parse_null() {
        assertNull(JapaneseDateParser.parse(null));
    }

    @Test
    void parse_invalidDate() {
        assertNull(JapaneseDateParser.parse("2026年13月40日"));
    }
}
