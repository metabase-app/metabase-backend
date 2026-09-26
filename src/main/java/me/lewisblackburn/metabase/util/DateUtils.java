package me.lewisblackburn.metabase.util;

import java.time.LocalDate;

public final class DateUtils {

    private DateUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Parses an ISO local date, returning {@code null} for missing or blank values.
     *
     * @throws java.time.format.DateTimeParseException if a nonblank value is not a valid ISO local
     *         date
     */
    public static LocalDate parseOptionalLocalDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }
}
