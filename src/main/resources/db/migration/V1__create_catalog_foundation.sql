CREATE TABLE entities (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type VARCHAR(40) NOT NULL,
    display_name VARCHAR(500) NOT NULL,
    slug VARCHAR(550),
    overview TEXT,
    original_language_code VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_entities_type CHECK (entity_type IN (
        'MOVIE', 'TV_SHOW', 'TV_SEASON', 'TV_EPISODE',
        'BOOK', 'BOOK_EDITION', 'SONG', 'RECORDING', 'ALBUM',
        'VIDEO_GAME', 'PERSON', 'ORGANISATION', 'COLLECTION'
    )),
    CONSTRAINT uq_entities_type_slug UNIQUE (entity_type, slug)
);

CREATE TABLE entity_names (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    name VARCHAR(500) NOT NULL,
    name_type VARCHAR(30) NOT NULL DEFAULT 'ALTERNATIVE',
    language_code VARCHAR(10),
    country_code CHAR(2),

    CONSTRAINT chk_entity_names_type CHECK (name_type IN (
        'ALTERNATIVE', 'ORIGINAL', 'TRANSLATION', 'FORMER', 'SEARCH_ALIAS'
    )),
    CONSTRAINT uq_entity_names UNIQUE NULLS NOT DISTINCT
        (entity_id, name, name_type, language_code, country_code)
);

CREATE TABLE genres (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE entity_genres (
    entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    genre_id BIGINT NOT NULL REFERENCES genres(id) ON DELETE CASCADE,
    PRIMARY KEY (entity_id, genre_id)
);

CREATE TABLE people (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    given_name VARCHAR(200),
    family_name VARCHAR(200),
    birth_date DATE,
    death_date DATE,
    birth_place VARCHAR(300),
    biography TEXT,
    gender VARCHAR(50)
);

CREATE TABLE organisations (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    organisation_type VARCHAR(40) NOT NULL,
    founded_date DATE,
    dissolved_date DATE,
    country_code CHAR(2),
    website_url TEXT,

    CONSTRAINT chk_organisations_type CHECK (organisation_type IN (
        'STUDIO', 'PRODUCTION_COMPANY', 'PUBLISHER', 'RECORD_LABEL',
        'GAME_DEVELOPER', 'GAME_PUBLISHER', 'NETWORK', 'DISTRIBUTOR', 'OTHER'
    ))
);

CREATE TABLE collections (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    collection_type VARCHAR(40) NOT NULL,

    CONSTRAINT chk_collections_type CHECK (collection_type IN (
        'MOVIE_COLLECTION', 'TV_FRANCHISE', 'BOOK_SERIES', 'GAME_SERIES',
        'MUSIC_SERIES', 'FRANCHISE', 'OTHER'
    ))
);

CREATE TABLE collection_members (
    collection_id BIGINT NOT NULL REFERENCES collections(entity_id) ON DELETE CASCADE,
    entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    position NUMERIC(8, 2),
    label VARCHAR(200),
    PRIMARY KEY (collection_id, entity_id)
);

CREATE TABLE entity_relationships (
    source_entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    target_entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    relationship_type VARCHAR(50) NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (source_entity_id, target_entity_id, relationship_type),

    CONSTRAINT chk_entity_relationships_type CHECK (relationship_type IN (
        'ADAPTATION_OF', 'BASED_ON', 'REMAKE_OF', 'SPIN_OFF_OF', 'SEQUEL_TO',
        'PREQUEL_TO', 'INSPIRED_BY', 'FEATURES', 'RELATED_TO'
    )),
    CONSTRAINT chk_entity_relationships_distinct CHECK (source_entity_id <> target_entity_id)
);

CREATE TABLE data_sources (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    source_type VARCHAR(30) NOT NULL,
    base_url TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_data_sources_type CHECK (source_type IN (
        'API', 'FILE', 'MANUAL', 'SCRAPER', 'OTHER'
    ))
);

CREATE TABLE provider_entity_mappings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data_source_id BIGINT NOT NULL REFERENCES data_sources(id),
    entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    provider_entity_type VARCHAR(80) NOT NULL,
    provider_entity_id VARCHAR(300) NOT NULL,
    provider_url TEXT,
    match_method VARCHAR(30) NOT NULL DEFAULT 'PROVIDER_ID',
    match_confidence NUMERIC(5, 4),
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_provider_entity_id UNIQUE
        (data_source_id, provider_entity_type, provider_entity_id),
    CONSTRAINT chk_provider_match_method CHECK (match_method IN (
        'PROVIDER_ID', 'EXACT', 'AUTOMATIC', 'MANUAL'
    )),
    CONSTRAINT chk_provider_match_confidence CHECK (
        match_confidence IS NULL OR match_confidence BETWEEN 0 AND 1
    )
);

CREATE TABLE import_runs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data_source_id BIGINT NOT NULL REFERENCES data_sources(id),
    import_type VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    cursor_value TEXT,
    records_seen INTEGER NOT NULL DEFAULT 0,
    records_created INTEGER NOT NULL DEFAULT 0,
    records_updated INTEGER NOT NULL DEFAULT 0,
    records_failed INTEGER NOT NULL DEFAULT 0,
    error_summary TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,

    CONSTRAINT chk_import_runs_status CHECK (status IN (
        'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED', 'CANCELLED'
    ))
);

CREATE TABLE import_failures (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    import_run_id BIGINT NOT NULL REFERENCES import_runs(id) ON DELETE CASCADE,
    provider_entity_id VARCHAR(300),
    error_message TEXT NOT NULL,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_entities_type ON entities (entity_type);
CREATE INDEX idx_entities_display_name ON entities (LOWER(display_name));
CREATE INDEX idx_entity_names_name ON entity_names (LOWER(name));
CREATE INDEX idx_collection_members_entity ON collection_members (entity_id);
CREATE INDEX idx_entity_relationships_target ON entity_relationships (target_entity_id);
CREATE INDEX idx_provider_mappings_entity ON provider_entity_mappings (entity_id);
CREATE INDEX idx_import_runs_source_started ON import_runs (data_source_id, started_at DESC);
