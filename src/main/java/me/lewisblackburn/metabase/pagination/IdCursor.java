package me.lewisblackburn.metabase.pagination;

import org.springframework.graphql.data.pagination.CursorEncoder;

/** Opaque cursors for queries ordered by positive numeric IDs. */
public final class IdCursor {
    private static final CursorEncoder ENCODER = CursorEncoder.base64();

    private IdCursor() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String encode(Long id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Cursor ID must be positive");
        }
        return ENCODER.encode(id.toString());
    }

    public static Long decode(String cursor) {
        try {
            long id = Long.parseLong(ENCODER.decode(cursor));
            if (id < 1) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("after must be a valid cursor", exception);
        }
    }
}
