-- Bootstrap missing BegaDiary tables for fresh PostgreSQL schema validation runs.
-- Existing environments with legacy tables are left unchanged via IF NOT EXISTS guards.

CREATE TABLE IF NOT EXISTS bega_diary (
    id BIGSERIAL PRIMARY KEY,
    diarydate DATE NOT NULL,
    game_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    memo VARCHAR(500),
    team VARCHAR(255) NOT NULL,
    stadium VARCHAR(255) NOT NULL,
    mood VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    winning VARCHAR(255) NOT NULL,
    createdat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ticket_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ticket_verified_at TIMESTAMP,
    section VARCHAR(50),
    block VARCHAR(50),
    seat_row VARCHAR(50),
    seat_number VARCHAR(50),
    CONSTRAINT uk_bega_diary_diarydate UNIQUE (diarydate),
    CONSTRAINT fk_bega_diary_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bega_diary_photo_urls (
    bega_diary_id BIGINT NOT NULL,
    photo_urls VARCHAR(2048) NOT NULL,
    PRIMARY KEY (bega_diary_id, photo_urls),
    CONSTRAINT fk_bega_diary_photo_urls_bega_diary
        FOREIGN KEY (bega_diary_id)
        REFERENCES bega_diary (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_bega_diary_user_id
    ON bega_diary (user_id);

CREATE INDEX IF NOT EXISTS idx_bega_diary_game_id
    ON bega_diary (game_id);

CREATE INDEX IF NOT EXISTS idx_bega_diary_photo_urls_begadiary_id
    ON bega_diary_photo_urls (bega_diary_id);
