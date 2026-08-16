package org.androiddaisyreader.machiiroconvert.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 依存ライブラリを増やさずにJSONを解析するための最小限のパーサー。
 * <p>
 * 解析結果は次のJava型へ変換される。
 * <ul>
 *   <li>オブジェクト → {@link Map}&lt;String, Object&gt;（順序保持）</li>
 *   <li>配列 → {@link List}&lt;Object&gt;</li>
 *   <li>文字列 → {@link String}</li>
 *   <li>数値 → {@link Double}</li>
 *   <li>true / false → {@link Boolean}</li>
 *   <li>null → {@code null}</li>
 * </ul>
 */
public final class JsonParser {

    private final String json;
    private int pos;

    private JsonParser(String json) {
        this.json = json;
    }

    /**
     * JSON文字列を解析する。
     *
     * @param json JSON文字列
     * @return 解析結果（Map / List / String / Double / Boolean / null のいずれか）
     * @throws IllegalArgumentException JSONとして不正な場合
     */
    public static Object parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON文字列がnullです");
        }
        JsonParser parser = new JsonParser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < json.length()) {
            throw parser.error("JSONの末尾に余分な文字があります");
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= json.length()) {
            throw error("値が見つかりません");
        }
        char c = json.charAt(pos);
        switch (c) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return parseNumber();
                }
                throw error("予期しない文字です: " + c);
        }
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (consumeIf('}')) {
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            if (consumeIf('}')) {
                break;
            }
            expect(',');
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (consumeIf(']')) {
            return list;
        }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            if (consumeIf(']')) {
                break;
            }
            expect(',');
        }
        return list;
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= json.length()) {
                throw error("文字列の終端がありません");
            }
            char c = json.charAt(pos++);
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                if (pos >= json.length()) {
                    throw error("文字列の終端がありません");
                }
                char esc = json.charAt(pos++);
                switch (esc) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > json.length()) {
                            throw error("不正なユニコードエスケープです");
                        }
                        try {
                            sb.append((char) Integer.parseInt(json.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException e) {
                            throw error("不正なユニコードエスケープです");
                        }
                        pos += 4;
                        break;
                    default:
                        throw error("不正なエスケープです: \\" + esc);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Double parseNumber() {
        int start = pos;
        if (peekRaw() == '-') {
            pos++;
        }
        while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
            pos++;
        }
        if (pos < json.length() && json.charAt(pos) == '.') {
            pos++;
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                pos++;
            }
        }
        if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
            pos++;
            if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
                pos++;
            }
        }
        return Double.parseDouble(json.substring(start, pos));
    }

    private void expect(String literal) {
        if (json.startsWith(literal, pos)) {
            pos += literal.length();
        } else {
            throw error("キーワード '" + literal + "' を期待しました");
        }
    }

    private void expect(char c) {
        if (pos < json.length() && json.charAt(pos) == c) {
            pos++;
        } else {
            throw error("'" + c + "' を期待しました");
        }
    }

    private boolean consumeIf(char c) {
        if (pos < json.length() && json.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private char peekRaw() {
        if (pos >= json.length()) {
            throw error("予期しない終端です");
        }
        return json.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " (位置 " + pos + ")");
    }
}
