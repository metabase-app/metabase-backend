package me.lewisblackburn.metabase.pagination;

import lombok.Builder;

/** A forward page ordered by a positive, unique numeric ID. */
@Builder
public record CursorPageRequest(
        Integer first,
        String after
) {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public CursorPageRequest {
        first = first == null ? DEFAULT_PAGE_SIZE : first;
        if (first < 0 || first > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("first must be between 0 and " + MAX_PAGE_SIZE);
        }
        if (after != null) {
            IdCursor.decode(after);
        }
    }

    public Long afterId() {
        return after == null ? null : IdCursor.decode(after);
    }

    public int fetchLimit() {
        return first + 1;
    }
}
