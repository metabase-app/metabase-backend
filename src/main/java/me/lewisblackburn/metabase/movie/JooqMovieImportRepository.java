package me.lewisblackburn.metabase.movie;

import static me.lewisblackburn.metabase.jooq.tables.DataSources.DATA_SOURCES;
import static me.lewisblackburn.metabase.jooq.tables.Entities.ENTITIES;
import static me.lewisblackburn.metabase.jooq.tables.Movies.MOVIES;
import static me.lewisblackburn.metabase.jooq.tables.ProviderEntityMappings.PROVIDER_ENTITY_MAPPINGS;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import me.lewisblackburn.metabase.datasource.DataSourceType;
import me.lewisblackburn.metabase.entity.EntityType;
import me.lewisblackburn.metabase.movie.model.Movie;
import me.lewisblackburn.metabase.movie.model.MovieImportData;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Repository
@RequiredArgsConstructor
public class JooqMovieImportRepository implements MovieImportRepository {

    private final DSLContext dsl;
    private final JooqMovieRepository movies;

    @Override
    @Transactional
    public Movie save(String sourceName, String providerEntityType, String externalId,
            MovieImportData data) {
        Assert.hasText(sourceName, "Source name must not be blank");
        Assert.hasText(providerEntityType, "Provider entity type must not be blank");
        Assert.hasText(externalId, "External movie ID must not be blank");
        Assert.notNull(data, "Movie import data must not be null");

        // Insert or update the provider row to allow only one save per provider at a time.
        // Other saves wait until this transaction finishes, preventing simultaneous imports
        // from both treating the same movie as new and creating duplicate entities.
        Long sourceId = dsl.insertInto(DATA_SOURCES).set(DATA_SOURCES.NAME, sourceName)
                .set(DATA_SOURCES.SOURCE_TYPE, DataSourceType.API.getDatabaseValue())
                .onConflict(DATA_SOURCES.NAME).doUpdate().set(DATA_SOURCES.NAME, sourceName)
                .returning(DATA_SOURCES.ID).fetchSingle().getId();

        var mapping = PROVIDER_ENTITY_MAPPINGS.DATA_SOURCE_ID.eq(sourceId)
                .and(PROVIDER_ENTITY_MAPPINGS.PROVIDER_ENTITY_TYPE.eq(providerEntityType))
                .and(PROVIDER_ENTITY_MAPPINGS.PROVIDER_ENTITY_ID.eq(externalId));
        Long entityId =
                dsl.select(PROVIDER_ENTITY_MAPPINGS.ENTITY_ID).from(PROVIDER_ENTITY_MAPPINGS)
                        .where(mapping).fetchOne(PROVIDER_ENTITY_MAPPINGS.ENTITY_ID);
        OffsetDateTime now = OffsetDateTime.now();

        if (entityId == null) {
            entityId = dsl.insertInto(ENTITIES)
                    .set(ENTITIES.ENTITY_TYPE, EntityType.MOVIE.getDatabaseValue())
                    .set(ENTITIES.DISPLAY_NAME, data.title())
                    .set(ENTITIES.OVERVIEW, data.overview())
                    .set(ENTITIES.ORIGINAL_LANGUAGE_CODE, data.originalLanguageCode())
                    .returning(ENTITIES.ID).fetchSingle().getId();

            dsl.insertInto(PROVIDER_ENTITY_MAPPINGS)
                    .set(PROVIDER_ENTITY_MAPPINGS.DATA_SOURCE_ID, sourceId)
                    .set(PROVIDER_ENTITY_MAPPINGS.ENTITY_ID, entityId)
                    .set(PROVIDER_ENTITY_MAPPINGS.PROVIDER_ENTITY_TYPE, providerEntityType)
                    .set(PROVIDER_ENTITY_MAPPINGS.PROVIDER_ENTITY_ID, externalId)
                    .set(PROVIDER_ENTITY_MAPPINGS.LAST_SEEN_AT, now).execute();
        } else {
            int updated = dsl.update(ENTITIES).set(ENTITIES.DISPLAY_NAME, data.title())
                    .set(ENTITIES.OVERVIEW, data.overview())
                    .set(ENTITIES.ORIGINAL_LANGUAGE_CODE, data.originalLanguageCode())
                    .set(ENTITIES.UPDATED_AT, now)
                    .where(ENTITIES.ID.eq(entityId)
                            .and(ENTITIES.ENTITY_TYPE.eq(EntityType.MOVIE.getDatabaseValue())))
                    .execute();
            Assert.state(updated == 1, "Provider mapping does not reference a movie");

            dsl.update(PROVIDER_ENTITY_MAPPINGS).set(PROVIDER_ENTITY_MAPPINGS.LAST_SEEN_AT, now)
                    .set(PROVIDER_ENTITY_MAPPINGS.UPDATED_AT, now).where(mapping).execute();
        }

        dsl.insertInto(MOVIES).set(MOVIES.ENTITY_ID, entityId)
                .set(MOVIES.ORIGINAL_TITLE, data.originalTitle())
                .set(MOVIES.RELEASE_DATE, data.releaseDate())
                .set(MOVIES.RUNTIME_MINUTES, data.runtimeMinutes()).onConflict(MOVIES.ENTITY_ID)
                .doUpdate().set(MOVIES.ORIGINAL_TITLE, data.originalTitle())
                .set(MOVIES.RELEASE_DATE, data.releaseDate())
                .set(MOVIES.RUNTIME_MINUTES, data.runtimeMinutes()).execute();

        return movies.find(entityId);
    }
}
