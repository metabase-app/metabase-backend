package me.lewisblackburn.metabase.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CursorPageRequestTest {
    @Test
    void suppliesDefaultSizeAndOneExtraRowForNextPageDetection() {
        // Given a caller has not specified pagination arguments.
        var page = CursorPageRequest.builder().build();

        // When the shared request supplies its defaults and SQL limit.
        // Then callers receive twenty nodes and fetch one extra row to detect the next page.
        assertThat(page.first()).isEqualTo(20);
        assertThat(page.after()).isNull();
        assertThat(page.fetchLimit()).isEqualTo(21);
    }

    @Test
    void acceptsTheMaximumPageAndRejectsAnInvalidCursor() {
        // Given a caller asks for the maximum page after a valid positive ID.
        var page = CursorPageRequest.builder()
                .first(100)
                .after(IdCursor.encode(10L))
                .build();

        // When the fetch limit is computed, it remains bounded even at the maximum size.
        assertThat(page.fetchLimit()).isEqualTo(101);

        // Then an invalid cursor is rejected even when the page size is omitted.
        assertThatThrownBy(() -> CursorPageRequest.builder().after("MA==").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundTripsOpaqueCursorsAndRejectsMalformedPositions() {
        // Given a valid numeric position encoded for the client.
        String cursor = IdCursor.encode(42L);

        // When the cursor is returned in a later request.
        var page = CursorPageRequest.builder()
                .after(cursor)
                .build();

        // Then the original position is recovered without exposing a raw ID cursor.
        assertThat(cursor).isNotEqualTo("42");
        assertThat(page.afterId()).isEqualTo(42L);

        // When invalid base64 or nonnumeric decoded positions are supplied, they are rejected.
        for (String invalid : new String[] {"!invalid", "", "YWJj", "LTE="}) {
            assertThatThrownBy(() -> CursorPageRequest.builder().after(invalid).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("after must be a valid cursor");
        }
    }
}
