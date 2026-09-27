ALTER TABLE users
    ADD COLUMN username VARCHAR(50),
    ADD COLUMN password_hash VARCHAR(255),
    ADD COLUMN last_seen_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ;

-- Existing catalogue users have no credentials; give them unique profile handles.
UPDATE users SET username = 'user_' || id;

ALTER TABLE users
    ALTER COLUMN username SET NOT NULL,
    ADD CONSTRAINT chk_users_username CHECK (username ~ '^[A-Za-z0-9_]{3,50}$'),
    ADD CONSTRAINT chk_users_password_hash CHECK (
        password_hash IS NULL OR btrim(password_hash) <> ''
    );

CREATE UNIQUE INDEX uq_users_username_lower ON users (lower(username));

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    PRIMARY KEY (user_id, role)
);

INSERT INTO user_roles (user_id, role) SELECT id, 'USER' FROM users;

CREATE TABLE user_follows (
    follower_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followed_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, followed_id),
    CONSTRAINT chk_user_follows_not_self CHECK (follower_id <> followed_id)
);

CREATE INDEX idx_user_follows_followed ON user_follows (followed_id, follower_id);
