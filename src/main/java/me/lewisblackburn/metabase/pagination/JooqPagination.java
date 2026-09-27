package me.lewisblackburn.metabase.pagination;

import graphql.relay.Connection;
import graphql.relay.DefaultConnection;
import graphql.relay.DefaultConnectionCursor;
import graphql.relay.DefaultEdge;
import graphql.relay.DefaultPageInfo;
import graphql.relay.Edge;
import java.util.List;
import java.util.function.Function;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

public final class JooqPagination {
    private JooqPagination() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** The query must select the cursor field and return one row per positive, unique ID. */
    public static <R extends Record, T> Connection<T> fetch(
            SelectConditionStep<R> query, Field<Long> cursorField,
            CursorPageRequest page, Function<R, T> mapper) {
        Long afterId = page.afterId();
        List<R> rows = query
                .and(afterId == null ? DSL.noCondition() : cursorField.gt(afterId))
                .orderBy(cursorField.asc())
                .limit(page.fetchLimit())
                .fetch();

        List<Edge<T>> edges = rows.stream()
                .limit(page.first())
                .<Edge<T>>map(row -> new DefaultEdge<>(mapper.apply(row),
                        new DefaultConnectionCursor(IdCursor.encode(row.get(cursorField)))))
                .toList();
        var startCursor = edges.isEmpty() ? null : edges.getFirst().getCursor();
        var endCursor = edges.isEmpty() ? null : edges.getLast().getCursor();
        // Relay allows false for hasPreviousPage when only forward pagination is supported.
        var pageInfo =
                new DefaultPageInfo(startCursor, endCursor, false, rows.size() > page.first());
        return new DefaultConnection<>(edges, pageInfo);
    }

    public static <T> Connection<T> empty() {
        return new DefaultConnection<>(List.of(), new DefaultPageInfo(null, null, false, false));
    }
}
