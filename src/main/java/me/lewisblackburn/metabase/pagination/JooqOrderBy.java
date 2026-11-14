package me.lewisblackburn.metabase.pagination;

import java.util.Objects;
import org.jooq.Field;
import org.jooq.SortField;

public final class JooqOrderBy {

    private JooqOrderBy() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Mapping mapping(OrderBy.Field orderField, Field<?> databaseField) {
        return new Mapping(
            Objects.requireNonNull(orderField, "Order field must not be null"),
            Objects.requireNonNull(databaseField, "Database field must not be null")
        );
    }

    public static SortField<?> toSortField(OrderBy orderBy, Mapping... mappings) {
        Objects.requireNonNull(orderBy, "Order by must not be null");
        for (Mapping mapping : mappings) {
            if (mapping.orderField() == orderBy.field()) {
                return orderBy.direction() == OrderBy.Direction.ASC
                    ? mapping.databaseField().asc()
                    : mapping.databaseField().desc();
            }
        }
        throw new IllegalArgumentException("Unsupported order field: " + orderBy.field());
    }

    public record Mapping(OrderBy.Field orderField, Field<?> databaseField) {}
}
