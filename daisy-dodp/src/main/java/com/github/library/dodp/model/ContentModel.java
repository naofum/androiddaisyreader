package com.github.library.dodp.model;

/**
 * The two service models supported by DODP v1.
 */
public enum ContentModel {

    /** requiresReturn = true: lending model (library). */
    BORROWABLE(true),

    /** requiresReturn = false: purchase / permanent ownership model. */
    PURCHASABLE(false);

    private final boolean requiresReturn;

    ContentModel(boolean requiresReturn) {
        this.requiresReturn = requiresReturn;
    }

    public boolean requiresReturn() {
        return requiresReturn;
    }

    public static ContentModel fromRequiresReturn(boolean requiresReturn) {
        return requiresReturn ? BORROWABLE : PURCHASABLE;
    }
}
