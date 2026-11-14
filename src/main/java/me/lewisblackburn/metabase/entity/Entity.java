package me.lewisblackburn.metabase.entity;

import java.time.OffsetDateTime;
import lombok.Builder;

/**
 * The shared catalogue identity for every item in the system.
 *
 * <p>Media-specific models, such as {@code Movie}, contain attributes that do not belong on this
 * common identity. User-owned relationships such as favourites and ratings should reference this
 * entity rather than a particular media type.
 */
@Builder
public record Entity(
    Long id,
    EntityType type,
    String displayName,
    String slug,
    String overview,
    String originalLanguageCode,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
