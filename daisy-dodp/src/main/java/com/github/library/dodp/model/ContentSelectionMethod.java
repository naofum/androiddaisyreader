package com.github.library.dodp.model;

/**
 * Content selection methods supported by a DODP service.
 */
public enum ContentSelectionMethod {

    /** Selection happens out of band (e.g. in a web browser). */
    OUT_OF_BAND,

    /** Selection happens in the reading system via Dynamic Menus. */
    BROWSE;

    public static ContentSelectionMethod fromString(String value) {
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
