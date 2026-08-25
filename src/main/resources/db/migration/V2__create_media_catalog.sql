CREATE TABLE movies (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    release_date DATE,
    runtime_minutes INTEGER,
    budget_amount BIGINT,
    revenue_amount BIGINT,
    original_title VARCHAR(500),
    status VARCHAR(30),

    CONSTRAINT chk_movies_runtime CHECK (runtime_minutes IS NULL OR runtime_minutes > 0),
    CONSTRAINT chk_movies_status CHECK (status IS NULL OR status IN (
        'RUMOURED', 'PLANNED', 'IN_PRODUCTION', 'POST_PRODUCTION',
        'RELEASED', 'CANCELLED'
    ))
);

CREATE TABLE tv_shows (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    first_air_date DATE,
    last_air_date DATE,
    original_title VARCHAR(500),
    episode_runtime_minutes INTEGER,
    status VARCHAR(30),

    CONSTRAINT chk_tv_shows_status CHECK (status IS NULL OR status IN (
        'PLANNED', 'IN_PRODUCTION', 'RETURNING', 'ENDED', 'CANCELLED'
    ))
);

CREATE TABLE tv_seasons (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    tv_show_id BIGINT NOT NULL REFERENCES tv_shows(entity_id) ON DELETE CASCADE,
    season_number INTEGER NOT NULL,
    air_date DATE,
    UNIQUE (tv_show_id, season_number)
);

CREATE TABLE tv_episodes (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    tv_season_id BIGINT NOT NULL REFERENCES tv_seasons(entity_id) ON DELETE CASCADE,
    episode_number INTEGER NOT NULL,
    air_date DATE,
    runtime_minutes INTEGER,
    UNIQUE (tv_season_id, episode_number)
);

CREATE TABLE books (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    original_title VARCHAR(500),
    first_published_date DATE
);

CREATE TABLE book_editions (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    book_id BIGINT NOT NULL REFERENCES books(entity_id) ON DELETE CASCADE,
    isbn_10 CHAR(10),
    isbn_13 CHAR(13),
    edition_name VARCHAR(250),
    format VARCHAR(40),
    publication_date DATE,
    page_count INTEGER,
    publisher_id BIGINT REFERENCES organisations(entity_id),
    language_code VARCHAR(10),

    CONSTRAINT uq_book_editions_isbn_10 UNIQUE (isbn_10),
    CONSTRAINT uq_book_editions_isbn_13 UNIQUE (isbn_13),
    CONSTRAINT chk_book_editions_format CHECK (format IS NULL OR format IN (
        'HARDCOVER', 'PAPERBACK', 'EBOOK', 'AUDIOBOOK', 'OTHER'
    ))
);

CREATE TABLE songs (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    composition_date DATE,
    lyrics TEXT
);

CREATE TABLE recordings (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    song_id BIGINT REFERENCES songs(entity_id),
    isrc VARCHAR(20) UNIQUE,
    duration_seconds INTEGER,
    recording_date DATE,
    release_date DATE,
    explicit BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_recordings_duration CHECK (duration_seconds IS NULL OR duration_seconds > 0)
);

CREATE TABLE albums (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    album_type VARCHAR(30) NOT NULL,
    release_date DATE,
    label_id BIGINT REFERENCES organisations(entity_id),
    upc VARCHAR(30),

    CONSTRAINT chk_albums_type CHECK (album_type IN (
        'ALBUM', 'EP', 'SINGLE', 'COMPILATION', 'SOUNDTRACK', 'OTHER'
    ))
);

CREATE TABLE album_tracks (
    album_id BIGINT NOT NULL REFERENCES albums(entity_id) ON DELETE CASCADE,
    recording_id BIGINT NOT NULL REFERENCES recordings(entity_id) ON DELETE CASCADE,
    disc_number INTEGER NOT NULL DEFAULT 1,
    track_number INTEGER NOT NULL,
    title_override VARCHAR(500),
    PRIMARY KEY (album_id, disc_number, track_number),
    UNIQUE (album_id, recording_id, disc_number, track_number)
);

CREATE TABLE soundtracks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    media_entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    album_id BIGINT REFERENCES albums(entity_id) ON DELETE SET NULL,
    name VARCHAR(500),
    soundtrack_type VARCHAR(30) NOT NULL DEFAULT 'OFFICIAL',

    CONSTRAINT chk_soundtracks_type CHECK (soundtrack_type IN (
        'OFFICIAL', 'SCORE', 'FEATURED_MUSIC', 'GAME_SOUNDTRACK', 'OTHER'
    )),
    CONSTRAINT uq_soundtracks_media_album UNIQUE NULLS NOT DISTINCT (media_entity_id, album_id, soundtrack_type)
);

CREATE TABLE soundtrack_recordings (
    soundtrack_id BIGINT NOT NULL REFERENCES soundtracks(id) ON DELETE CASCADE,
    recording_id BIGINT NOT NULL REFERENCES recordings(entity_id) ON DELETE CASCADE,
    position INTEGER,
    usage_note VARCHAR(300),
    PRIMARY KEY (soundtrack_id, recording_id)
);

CREATE TABLE video_games (
    entity_id BIGINT PRIMARY KEY REFERENCES entities(id) ON DELETE CASCADE,
    initial_release_date DATE,
    game_status VARCHAR(30),

    CONSTRAINT chk_video_games_status CHECK (game_status IS NULL OR game_status IN (
        'ANNOUNCED', 'EARLY_ACCESS', 'RELEASED', 'CANCELLED'
    ))
);

CREATE TABLE game_platforms (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    slug VARCHAR(140) NOT NULL UNIQUE,
    manufacturer_id BIGINT REFERENCES organisations(entity_id)
);

CREATE TABLE game_releases (
    video_game_id BIGINT NOT NULL REFERENCES video_games(entity_id) ON DELETE CASCADE,
    platform_id BIGINT NOT NULL REFERENCES game_platforms(id),
    region_code VARCHAR(10) NOT NULL DEFAULT 'WORLDWIDE',
    release_date DATE,
    PRIMARY KEY (video_game_id, platform_id, region_code)
);

CREATE TABLE credits (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credited_entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    person_id BIGINT NOT NULL REFERENCES people(entity_id) ON DELETE CASCADE,
    department VARCHAR(80),
    job VARCHAR(120) NOT NULL,
    character_name VARCHAR(500),
    credited_as VARCHAR(300),
    credit_order INTEGER,
    episode_count INTEGER,

    CONSTRAINT uq_credits UNIQUE NULLS NOT DISTINCT
        (credited_entity_id, person_id, job, character_name, credited_as)
);

CREATE TABLE organisation_credits (
    entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    organisation_id BIGINT NOT NULL REFERENCES organisations(entity_id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (entity_id, organisation_id, role),

    CONSTRAINT chk_organisation_credits_role CHECK (role IN (
        'PRODUCTION', 'STUDIO', 'PUBLISHER', 'DEVELOPER', 'DISTRIBUTOR',
        'NETWORK', 'LABEL', 'OTHER'
    ))
);

CREATE TABLE release_regions (
    entity_id BIGINT NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
    country_code CHAR(2) NOT NULL,
    release_date DATE NOT NULL,
    release_type VARCHAR(40),
    certification VARCHAR(30),
    note VARCHAR(300),
    PRIMARY KEY (entity_id, country_code, release_date, release_type)
);

CREATE INDEX idx_tv_seasons_show ON tv_seasons (tv_show_id, season_number);
CREATE INDEX idx_tv_episodes_season ON tv_episodes (tv_season_id, episode_number);
CREATE INDEX idx_book_editions_book ON book_editions (book_id);
CREATE INDEX idx_recordings_song ON recordings (song_id);
CREATE INDEX idx_album_tracks_recording ON album_tracks (recording_id);
CREATE INDEX idx_soundtracks_media ON soundtracks (media_entity_id);
CREATE INDEX idx_soundtrack_recordings_recording ON soundtrack_recordings (recording_id);
CREATE INDEX idx_credits_entity_order ON credits (credited_entity_id, credit_order);
CREATE INDEX idx_credits_person ON credits (person_id);
CREATE INDEX idx_organisation_credits_organisation ON organisation_credits (organisation_id);

