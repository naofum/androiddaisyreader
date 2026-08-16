package org.androiddaisyreader.machiiroconvert.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
class JsonParserTest {

    @Test
    void parse_object() {
        Object result = JsonParser.parse("{\"a\": 1, \"b\": \"text\"}");
        assertTrue(result instanceof Map);
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals(2, map.size());
        assertEquals(1.0, map.get("a"));
        assertEquals("text", map.get("b"));
    }

    @Test
    void parse_array() {
        Object result = JsonParser.parse("[1, 2, 3]");
        assertTrue(result instanceof List);
        List<Object> list = (List<Object>) result;
        assertEquals(3, list.size());
        assertEquals(1.0, list.get(0));
        assertEquals(3.0, list.get(2));
    }

    @Test
    void parse_nested() {
        Object result = JsonParser.parse("{\"items\": [{\"id\": 1}, {\"id\": 2}]}");
        Map<String, Object> map = (Map<String, Object>) result;
        List<Object> items = (List<Object>) map.get("items");
        assertEquals(2, items.size());
    }

    @Test
    void parse_primitives() {
        assertEquals("abc", JsonParser.parse("\"abc\""));
        assertEquals(12.5, JsonParser.parse("12.5"));
        assertEquals(-3.0, JsonParser.parse("-3"));
        assertEquals(Boolean.TRUE, JsonParser.parse("true"));
        assertEquals(Boolean.FALSE, JsonParser.parse("false"));
        assertNull(JsonParser.parse("null"));
    }

    @Test
    void parse_stringEscapes() {
        assertEquals("a\"b\\c/d", JsonParser.parse("\"a\\\"b\\\\c\\/d\""));
        assertEquals("改行\n", JsonParser.parse("\"改行\\n\""));
        assertEquals("\u65e5", JsonParser.parse("\"\\u65e5\""));
    }

    @Test
    void parse_invalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("{"));
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("{\"a\": }"));
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse(null));
    }

    @Test
    void parse_trailingCharactersThrows() {
        assertThrows(IllegalArgumentException.class, () -> JsonParser.parse("{} extra"));
    }
}
