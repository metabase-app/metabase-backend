package me.lewisblackburn.metabase.movie;

import static me.lewisblackburn.metabase.jooq.tables.Credits.CREDITS;
import static me.lewisblackburn.metabase.jooq.tables.Entities.ENTITIES;
import static me.lewisblackburn.metabase.jooq.tables.Movies.MOVIES;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MovieRepository {

    private final DSLContext dsl;

    public List<Movie> findAll() {
        return dsl.select(
                        ENTITIES.ID,
                        ENTITIES.DISPLAY_NAME,
                        ENTITIES.OVERVIEW,
                        MOVIES.RELEASE_DATE,
                        MOVIES.RUNTIME_MINUTES)
                .from(MOVIES)
                .join(ENTITIES).on(ENTITIES.ID.eq(MOVIES.ENTITY_ID))
                .orderBy(ENTITIES.DISPLAY_NAME)
                .fetch(record -> new Movie(
                        record.get(ENTITIES.ID),
                        record.get(ENTITIES.DISPLAY_NAME),
                        record.get(ENTITIES.OVERVIEW),
                        record.get(MOVIES.RELEASE_DATE),
                        record.get(MOVIES.RUNTIME_MINUTES)));
    }

    public Movie find(Long id) {
        return dsl.select(
                        ENTITIES.ID,
                        ENTITIES.DISPLAY_NAME,
                        ENTITIES.OVERVIEW,
                        MOVIES.RELEASE_DATE,
                        MOVIES.RUNTIME_MINUTES)
                .from(MOVIES)
                .join(ENTITIES).on(ENTITIES.ID.eq(MOVIES.ENTITY_ID))
                .where(ENTITIES.ID.eq(id))
                .fetchOne(record -> new Movie(
                        record.get(ENTITIES.ID),
                        record.get(ENTITIES.DISPLAY_NAME),
                        record.get(ENTITIES.OVERVIEW),
                        record.get(MOVIES.RELEASE_DATE),
                        record.get(MOVIES.RUNTIME_MINUTES)));
    }

    public Map<Long, List<CastMember>> findCastByMovieIds(List<Long> movieIds) {
        return dsl.select(
                        CREDITS.CREDITED_ENTITY_ID,
                        CREDITS.PERSON_ID,
                        ENTITIES.DISPLAY_NAME,
                        CREDITS.CHARACTER_NAME,
                        CREDITS.CREDIT_ORDER)
                .from(CREDITS)
                .join(ENTITIES).on(ENTITIES.ID.eq(CREDITS.PERSON_ID))
                .where(CREDITS.CREDITED_ENTITY_ID.in(movieIds))
                .and(CREDITS.JOB.eq("Actor"))
                .orderBy(CREDITS.CREDITED_ENTITY_ID, CREDITS.CREDIT_ORDER.asc().nullsLast())
                .fetch()
                .stream()
                .collect(Collectors.groupingBy(
                        record -> record.get(CREDITS.CREDITED_ENTITY_ID),
                        Collectors.mapping(
                                record -> new CastMember(
                                        record.get(CREDITS.PERSON_ID),
                                        record.get(ENTITIES.DISPLAY_NAME),
                                        record.get(CREDITS.CHARACTER_NAME),
                                        record.get(CREDITS.CREDIT_ORDER)),
                                Collectors.toList())));
    }
}

