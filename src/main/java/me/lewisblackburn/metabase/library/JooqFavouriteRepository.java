package me.lewisblackburn.metabase.library;

import static me.lewisblackburn.metabase.jooq.tables.Entities.ENTITIES;
import static me.lewisblackburn.metabase.jooq.tables.UserFavourites.USER_FAVOURITES;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.entity.Entity;
import me.lewisblackburn.metabase.entity.EntityType;
import me.lewisblackburn.metabase.pagination.JooqOrderBy;
import me.lewisblackburn.metabase.pagination.OrderBy;
import me.lewisblackburn.metabase.pagination.Pagination;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JooqFavouriteRepository implements FavouriteRepository {

    private static final JooqOrderBy.Mapping[] ORDER_FIELDS = {
        JooqOrderBy.mapping(OrderBy.Field.ID, ENTITIES.ID),
        JooqOrderBy.mapping(OrderBy.Field.CREATED_AT, USER_FAVOURITES.CREATED_AT),
        JooqOrderBy.mapping(OrderBy.Field.UPDATED_AT, ENTITIES.UPDATED_AT),
    };

    private final DSLContext dsl;

    @Override
    public List<Entity> findAll(Long userId, int offset, int limit, OrderBy orderBy) {
        Pagination.validate(offset, limit);
        return dsl
            .select(
                ENTITIES.ID,
                ENTITIES.ENTITY_TYPE,
                ENTITIES.DISPLAY_NAME,
                ENTITIES.SLUG,
                ENTITIES.OVERVIEW,
                ENTITIES.ORIGINAL_LANGUAGE_CODE,
                ENTITIES.CREATED_AT,
                ENTITIES.UPDATED_AT
            )
            .from(USER_FAVOURITES)
            .join(ENTITIES)
            .on(ENTITIES.ID.eq(USER_FAVOURITES.ENTITY_ID))
            .where(USER_FAVOURITES.USER_ID.eq(userId))
            .orderBy(JooqOrderBy.toSortField(orderBy, ORDER_FIELDS))
            .offset(offset)
            .limit(limit)
            .fetch(record ->
                Entity.builder()
                    .id(record.get(ENTITIES.ID))
                    .type(EntityType.valueOf(record.get(ENTITIES.ENTITY_TYPE)))
                    .displayName(record.get(ENTITIES.DISPLAY_NAME))
                    .slug(record.get(ENTITIES.SLUG))
                    .overview(record.get(ENTITIES.OVERVIEW))
                    .originalLanguageCode(record.get(ENTITIES.ORIGINAL_LANGUAGE_CODE))
                    .createdAt(record.get(ENTITIES.CREATED_AT))
                    .updatedAt(record.get(ENTITIES.UPDATED_AT))
                    .build()
            );
    }

    @Override
    public boolean favourite(Long userId, Long entityId) {
        return (
            dsl
                .insertInto(USER_FAVOURITES)
                .set(USER_FAVOURITES.USER_ID, userId)
                .set(USER_FAVOURITES.ENTITY_ID, entityId)
                .onConflict(USER_FAVOURITES.USER_ID, USER_FAVOURITES.ENTITY_ID)
                .doNothing()
                .execute() == 1
        );
    }

    @Override
    public boolean unfavourite(Long userId, Long entityId) {
        return (
            dsl
                .deleteFrom(USER_FAVOURITES)
                .where(USER_FAVOURITES.USER_ID.eq(userId))
                .and(USER_FAVOURITES.ENTITY_ID.eq(entityId))
                .execute() == 1
        );
    }
}
