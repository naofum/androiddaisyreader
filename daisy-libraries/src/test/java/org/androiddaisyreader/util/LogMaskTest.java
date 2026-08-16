package org.androiddaisyreader.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogMaskTest {

    @Test
    void mask_keepsFirstTwoCharacters() {
        assertEquals("ab***", LogMask.mask("abc12345"));
        assertEquals("ログ***", LogMask.mask("ログインID"));
    }

    @Test
    void mask_shortValuesBecomeAsterisks() {
        assertEquals("***", LogMask.mask("ab"));
        assertEquals("***", LogMask.mask("a"));
    }

    @Test
    void mask_nullOrEmptyReturnsEmpty() {
        assertEquals("", LogMask.mask(null));
        assertEquals("", LogMask.mask(""));
    }
}
