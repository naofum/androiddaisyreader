package com.github.library.dodp.model;

/**
 * Input types a reading system may support, declared in
 * {@code readingSystemAttributes}.
 */
public enum InputType {

    TEXT_NUMERIC,
    TEXT_ALPHANUMERIC,
    AUDIO;

    public static InputType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
