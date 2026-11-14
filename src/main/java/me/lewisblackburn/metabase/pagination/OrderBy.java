package me.lewisblackburn.metabase.pagination;

import java.util.Objects;

public record OrderBy(Field field, Direction direction) {
    public OrderBy {
        Objects.requireNonNull(field, "Order field must not be null");
        Objects.requireNonNull(direction, "Order direction must not be null");
    }

    public enum Field {
        ID,
        CREATED_AT,
        UPDATED_AT,
    }

    public enum Direction {
        ASC,
        DESC,
    }
}
