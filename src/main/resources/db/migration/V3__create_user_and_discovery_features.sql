CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(320) UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    country_code CHAR(2),
    language_code VARCHAR(10),
    timezone VARCHAR(100),
    include_adult_content BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE user_favourites (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, entity_id)
);

CREATE TABLE user_ratings (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, entity_id),

    CONSTRAINT chk_user_ratings_value CHECK (rating BETWEEN 1 AND 10)
);

CREATE TABLE user_movie_states (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    movie_id BIGINT NOT NULL REFERENCES movies(entity_id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    progress_seconds INTEGER,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, movie_id),
    CONSTRAINT chk_user_movie_states_status CHECK (status IN (
        'WATCHLIST', 'WATCHING', 'WATCHED', 'DROPPED'
    ))
);

CREATE TABLE user_tv_states (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tv_show_id BIGINT NOT NULL REFERENCES tv_shows(entity_id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, tv_show_id),
    CONSTRAINT chk_user_tv_states_status CHECK (status IN (
        'WATCHLIST', 'WATCHING', 'WATCHED', 'DROPPED'
    ))
);

CREATE TABLE user_episode_states (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    episode_id BIGINT NOT NULL REFERENCES tv_episodes(entity_id) ON DELETE CASCADE,
    watched BOOLEAN NOT NULL DEFAULT FALSE,
    progress_seconds INTEGER,
    watched_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, episode_id)
);

CREATE TABLE user_book_states (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id BIGINT NOT NULL REFERENCES books(entity_id) ON DELETE CASCADE,
    edition_id BIGINT REFERENCES book_editions(entity_id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL,
    progress_pages INTEGER,
    started_at DATE,
    completed_at DATE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, book_id),
    CONSTRAINT chk_user_book_states_status CHECK (status IN (
        'READLIST', 'READING', 'READ', 'DROPPED'
    ))
);

CREATE TABLE user_game_states (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_game_id BIGINT NOT NULL REFERENCES video_games(entity_id) ON DELETE CASCADE,
    platform_id BIGINT REFERENCES game_platforms(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL,
    playtime_minutes INTEGER,
    started_at DATE,
    completed_at DATE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, video_game_id),
    CONSTRAINT chk_user_game_states_status CHECK (status IN (
        'BACKLOG', 'PLAYING', 'COMPLETED', 'DROPPED'
    ))
);

CREATE TABLE user_lists (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_lists_visibility CHECK (visibility IN ('PRIVATE', 'UNLISTED', 'PUBLIC'))
);

CREATE TABLE user_list_items (
    list_id BIGINT NOT NULL REFERENCES user_lists(id) ON DELETE CASCADE,
    entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    position INTEGER,
    note TEXT,
    added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (list_id, entity_id)
);

CREATE TABLE entity_similarities (
    source_entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    target_entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    algorithm VARCHAR(80) NOT NULL,
    score NUMERIC(7, 6) NOT NULL,
    explanation JSONB,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (source_entity_id, target_entity_id, algorithm),
    CONSTRAINT chk_entity_similarities_score CHECK (score BETWEEN 0 AND 1),
    CONSTRAINT chk_entity_similarities_distinct CHECK (source_entity_id <> target_entity_id)
);

CREATE TABLE user_recommendations (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    algorithm VARCHAR(80) NOT NULL,
    score NUMERIC(7, 6) NOT NULL,
    reason JSONB,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dismissed_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, entity_id, algorithm),
    CONSTRAINT chk_user_recommendations_score CHECK (score BETWEEN 0 AND 1)
);

CREATE TABLE media_assets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_id BIGINT REFERENCES entities(id) ON DELETE CASCADE,
    asset_type VARCHAR(30) NOT NULL,
    storage_provider VARCHAR(50),
    storage_key TEXT,
    external_url TEXT,
    content_type VARCHAR(150),
    byte_size BIGINT,
    width INTEGER,
    height INTEGER,
    language_code VARCHAR(10),
    uploaded_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_media_assets_storage UNIQUE NULLS NOT DISTINCT (storage_provider, storage_key),
    CONSTRAINT chk_media_assets_location CHECK (
        (storage_provider IS NOT NULL AND storage_key IS NOT NULL)
        OR external_url IS NOT NULL
    ),
    CONSTRAINT chk_media_assets_type CHECK (asset_type IN (
        'POSTER', 'BACKDROP', 'PROFILE', 'COVER', 'LOGO', 'AUDIO', 'VIDEO', 'OTHER'
    ))
);

CREATE TABLE swipe_parties (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    join_code VARCHAR(20) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    filters JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMPTZ,
    CONSTRAINT chk_swipe_parties_status CHECK (status IN ('OPEN', 'MATCHED', 'CLOSED', 'EXPIRED'))
);

CREATE TABLE swipe_party_members (
    party_id BIGINT NOT NULL REFERENCES swipe_parties(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (party_id, user_id)
);

CREATE TABLE swipe_party_candidates (
    party_id BIGINT NOT NULL REFERENCES swipe_parties(id) ON DELETE CASCADE,
    movie_id BIGINT NOT NULL REFERENCES movies(entity_id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    PRIMARY KEY (party_id, movie_id),
    UNIQUE (party_id, position)
);

CREATE TABLE swipe_votes (
    party_id BIGINT NOT NULL REFERENCES swipe_parties(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    movie_id BIGINT NOT NULL REFERENCES movies(entity_id) ON DELETE CASCADE,
    decision VARCHAR(10) NOT NULL,
    voted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (party_id, user_id, movie_id),
    CONSTRAINT chk_swipe_votes_decision CHECK (decision IN ('LIKE', 'PASS'))
);

CREATE TABLE swipe_matches (
    party_id BIGINT PRIMARY KEY REFERENCES swipe_parties(id) ON DELETE CASCADE,
    movie_id BIGINT NOT NULL REFERENCES movies(entity_id),
    matched_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_favourites_entity ON user_favourites (entity_id);
CREATE INDEX idx_user_ratings_entity ON user_ratings (entity_id);
CREATE INDEX idx_user_lists_user ON user_lists (user_id);
CREATE INDEX idx_user_list_items_entity ON user_list_items (entity_id);
CREATE INDEX idx_entity_similarities_target_score ON entity_similarities (target_entity_id, score DESC);
CREATE INDEX idx_user_recommendations_user_score ON user_recommendations (user_id, score DESC);
CREATE INDEX idx_media_assets_entity_type ON media_assets (entity_id, asset_type);
CREATE INDEX idx_swipe_votes_party_movie ON swipe_votes (party_id, movie_id, decision);
