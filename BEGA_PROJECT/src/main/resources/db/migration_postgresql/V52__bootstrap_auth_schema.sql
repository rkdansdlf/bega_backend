-- V52: Bootstrap missing auth schema objects on PostgreSQL dev environments.

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    unique_id UUID,
    handle VARCHAR(15) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password TEXT,
    profile_image_url VARCHAR(2048),
    role VARCHAR(255) NOT NULL,
    bio VARCHAR(500),
    private_account BOOLEAN NOT NULL DEFAULT FALSE,
    favorite_team VARCHAR(255),
    cheer_points INTEGER NOT NULL DEFAULT 0,
    last_bonus_date DATE,
    last_login_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    lock_expires_at TIMESTAMP,
    token_version INTEGER NOT NULL DEFAULT 0,
    provider VARCHAR(255),
    providerid VARCHAR(255),

    CONSTRAINT fk_users_favorite_team
        FOREIGN KEY (favorite_team)
        REFERENCES teams (team_id)
        ON UPDATE NO ACTION
        ON DELETE SET NULL
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS unique_id UUID;
ALTER TABLE users ADD COLUMN IF NOT EXISTS handle VARCHAR(15);
ALTER TABLE users ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(2048);
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS private_account BOOLEAN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS favorite_team VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS cheer_points INTEGER;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_bonus_date DATE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_date TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled BOOLEAN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked BOOLEAN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS lock_expires_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER;
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS providerid VARCHAR(255);

-- Optional compatibility if users table pre-exists from other environments.
UPDATE users
SET unique_id = (
    SELECT
        (SUBSTRING(src.raw FROM 1 FOR 8) || '-' ||
         SUBSTRING(src.raw FROM 9 FOR 4) || '-' ||
         SUBSTRING(src.raw FROM 13 FOR 4) || '-' ||
         SUBSTRING(src.raw FROM 17 FOR 4) || '-' ||
         SUBSTRING(src.raw FROM 21 FOR 12))::uuid
    FROM (SELECT MD5(random()::text || COALESCE(id::text, '') || clock_timestamp()::text) AS raw) src
)
WHERE unique_id IS NULL;

ALTER TABLE users ALTER COLUMN unique_id SET NOT NULL;
ALTER TABLE users ALTER COLUMN handle SET NOT NULL;
ALTER TABLE users ALTER COLUMN email SET NOT NULL;
ALTER TABLE users ALTER COLUMN name SET NOT NULL;
ALTER TABLE users ALTER COLUMN role SET NOT NULL;
ALTER TABLE users ALTER COLUMN private_account SET DEFAULT FALSE;
ALTER TABLE users ALTER COLUMN private_account SET NOT NULL;
ALTER TABLE users ALTER COLUMN cheer_points SET DEFAULT 0;
ALTER TABLE users ALTER COLUMN cheer_points SET NOT NULL;
ALTER TABLE users ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE users ALTER COLUMN enabled SET DEFAULT TRUE;
ALTER TABLE users ALTER COLUMN enabled SET NOT NULL;
ALTER TABLE users ALTER COLUMN locked SET DEFAULT FALSE;
ALTER TABLE users ALTER COLUMN locked SET NOT NULL;
ALTER TABLE users ALTER COLUMN token_version SET DEFAULT 0;
ALTER TABLE users ALTER COLUMN token_version SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_handle ON users (handle);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email ON users (email);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_unique_id ON users (unique_id);

CREATE TABLE IF NOT EXISTS user_providers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    providerid VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    connected_at TIMESTAMP,

    CONSTRAINT uk_user_provider_pid UNIQUE (provider, providerid),
    CONSTRAINT uk_user_provider_uid UNIQUE (user_id, provider),
    CONSTRAINT fk_user_providers_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

ALTER TABLE user_providers
    ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE user_providers
    ADD COLUMN IF NOT EXISTS provider VARCHAR(20);
ALTER TABLE user_providers
    ADD COLUMN IF NOT EXISTS providerid VARCHAR(255);
ALTER TABLE user_providers
    ADD COLUMN IF NOT EXISTS connected_at TIMESTAMP;
ALTER TABLE user_providers
    ADD COLUMN IF NOT EXISTS email VARCHAR(255);

CREATE TABLE IF NOT EXISTS user_block (
    blocker_id BIGINT NOT NULL,
    blocked_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT fk_user_block_blocker
        FOREIGN KEY (blocker_id)
        REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_block_blocked
        FOREIGN KEY (blocked_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_follow (
    follower_id BIGINT NOT NULL,
    following_id BIGINT NOT NULL,
    notify_new_posts BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT fk_user_follow_follower
        FOREIGN KEY (follower_id)
        REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_follow_following
        FOREIGN KEY (following_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expirydate TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255),
    token VARCHAR(1024),
    expirydate TIMESTAMP,
    device_type VARCHAR(32),
    device_label VARCHAR(255),
    browser VARCHAR(64),
    os VARCHAR(64),
    ip VARCHAR(64),
    last_seen_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_providers_user ON user_providers (user_id);
CREATE INDEX IF NOT EXISTS idx_user_block_blocker ON user_block (blocker_id);
CREATE INDEX IF NOT EXISTS idx_user_block_blocked ON user_block (blocked_id);
CREATE INDEX IF NOT EXISTS idx_user_follow_follower ON user_follow (follower_id);
CREATE INDEX IF NOT EXISTS idx_user_follow_following ON user_follow (following_id);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_email ON refresh_tokens (email);
