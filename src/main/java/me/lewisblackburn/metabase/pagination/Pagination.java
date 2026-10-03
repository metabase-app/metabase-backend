package me.lewisblackburn.metabase.pagination;

public final class Pagination {
    private Pagination() {
    }

    public static void validate(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "Offset must be non-negative and limit between 1 and 100");
        }
    }
}
