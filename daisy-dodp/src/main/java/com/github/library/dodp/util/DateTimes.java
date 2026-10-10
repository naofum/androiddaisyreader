package com.github.library.dodp.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

/**
 * Parses {@code xs:dateTime} values that appear in DODP responses. The DODP
 * specification sends ISO-8601 timestamps that may or may not carry a timezone
 * offset; values without an offset are interpreted as UTC.
 */
public final class DateTimes {

    private DateTimes() {
    }

    /**
     * Parses an {@code xs:dateTime} value into a {@link ZonedDateTime}.
     *
     * @return the parsed value, or {@code null} if {@code value} is null/blank
     */
    public static ZonedDateTime parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String v = value.trim();
        try {
            return OffsetDateTime.parse(v).toZonedDateTime();
        } catch (DateTimeParseException ignored) {
            // no timezone offset present
        }
        try {
            return LocalDateTime.parse(v).atZone(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
