package com.github.library.dodp.model;

/**
 * Optional operations a DODP service may advertise via
 * {@code getServiceAttributes}.
 */
public enum OptionalOperation {

    SET_BOOKMARKS,
    GET_BOOKMARKS,
    DYNAMIC_MENUS,
    SERVICE_ANNOUNCEMENTS,
    PDTB2_KEY_PROVISION;

    public static OptionalOperation fromString(String value) {
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
