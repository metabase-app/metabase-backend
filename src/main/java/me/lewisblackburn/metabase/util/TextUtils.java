package me.lewisblackburn.metabase.util;

public final class TextUtils {

    private TextUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Returns null for missing or blank text, preserving nonblank text unchanged. */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
