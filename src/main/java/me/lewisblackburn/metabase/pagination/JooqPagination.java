package me.lewisblackburn.metabase.pagination;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jooq.Field;
import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.graphql.data.pagination.Subrange;

public final class JooqPagination {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private JooqPagination() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** The query must select the cursor field and return one row per positive, unique ID. */
    public static <R extends Record, T> Window<T> fetch(
            SelectConditionStep<R> query, Field<Long> cursorField,
            Subrange<ScrollPosition> page, Function<R, T> mapper) {
        int size = pageSize(page);
        Condition afterCursor = afterCursor(page, cursorField);

        // Fetch one extra row to determine whether another page exists.
        var rows = query
                .and(afterCursor)
                .orderBy(cursorField.asc())
                .limit(size + 1)
                .fetch();
        boolean hasMore = rows.size() > size;
        var pageRows = List.copyOf(rows.subList(0, Math.min(size, rows.size())));

        return Window.from(pageRows,
                index -> ScrollPosition.forward(Map.of(cursorField.getName(),
                        pageRows.get(index).get(cursorField))),
                hasMore)
                .map(mapper);
    }

    private static int pageSize(Subrange<ScrollPosition> page) {
        int size = page.count().orElse(DEFAULT_PAGE_SIZE);
        if (size < 0 || size > MAX_PAGE_SIZE || !page.forward()) {
            throw new IllegalArgumentException(
                    "Use forward pagination with first between 0 and 100");
        }
        return size;
    }

    private static Condition afterCursor(Subrange<ScrollPosition> page, Field<Long> cursorField) {
        ScrollPosition position = page.position().orElse(ScrollPosition.keyset());
        if (!(position instanceof KeysetScrollPosition keyset) || !keyset.scrollsForward()) {
            throw new IllegalArgumentException("Use a forward keyset cursor");
        }
        if (keyset.isInitial()) {
            return DSL.noCondition();
        }
        if (keyset.getKeys().size() != 1
                || !(keyset.getKeys().get(cursorField.getName()) instanceof Long id)
                || id < 1) {
            throw new IllegalArgumentException("Invalid cursor for this query");
        }
        return cursorField.gt(id);
    }

    public static <T> Window<T> empty() {
        return Window.from(List.of(), index -> ScrollPosition.keyset());
    }
}
