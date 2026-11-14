package me.lewisblackburn.metabase.entity;

import static me.lewisblackburn.metabase.jooq.tables.Entities.ENTITIES;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EntityLookup {

    private final DSLContext dsl;

    public void requireExistingEntity(Long entityId) {
        if (entityId == null || entityId < 1) {
            throw new IllegalArgumentException("Entity ID must be positive");
        }

        boolean exists = dsl.fetchExists(
            dsl.selectOne().from(ENTITIES).where(ENTITIES.ID.eq(entityId))
        );
        if (!exists) {
            throw new IllegalArgumentException("Entity not found");
        }
    }
}
