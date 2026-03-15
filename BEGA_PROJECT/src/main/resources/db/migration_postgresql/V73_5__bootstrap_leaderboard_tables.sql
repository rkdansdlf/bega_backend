-- Bootstrap missing leaderboard tables for fresh PostgreSQL schema validation runs.
-- Existing environments with legacy tables are left unchanged via IF NOT EXISTS guards.

CREATE TABLE IF NOT EXISTS user_scores (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_score BIGINT NOT NULL DEFAULT 0,
    season_score BIGINT NOT NULL DEFAULT 0,
    monthly_score BIGINT NOT NULL DEFAULT 0,
    weekly_score BIGINT NOT NULL DEFAULT 0,
    current_streak INTEGER NOT NULL DEFAULT 0,
    max_streak INTEGER NOT NULL DEFAULT 0,
    user_level INTEGER NOT NULL DEFAULT 1,
    experience_points BIGINT NOT NULL DEFAULT 0,
    correct_predictions INTEGER NOT NULL DEFAULT 0,
    total_predictions INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_scores_user UNIQUE (user_id),
    CONSTRAINT fk_user_scores_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS score_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    prediction_id BIGINT,
    game_id VARCHAR(50),
    diary_id BIGINT,
    event_type VARCHAR(50) NOT NULL,
    base_score INTEGER NOT NULL,
    multiplier NUMERIC(5,2) NOT NULL DEFAULT 1.00,
    final_score INTEGER NOT NULL,
    streak_count INTEGER NOT NULL DEFAULT 0,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_score_events_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_powerups (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    powerup_type VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_powerups_user_type UNIQUE (user_id, powerup_type),
    CONSTRAINT fk_user_powerups_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS active_powerups (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    powerup_type VARCHAR(50) NOT NULL,
    game_id VARCHAR(50),
    activated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_active_powerups_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS achievements (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name_ko VARCHAR(100) NOT NULL,
    name_en VARCHAR(100),
    description_ko VARCHAR(500),
    description_en VARCHAR(500),
    icon_url VARCHAR(500),
    rarity VARCHAR(20) NOT NULL DEFAULT 'COMMON',
    points_required BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_achievements_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS user_achievements (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    achievement_id BIGINT NOT NULL,
    earned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_achievements_user_achievement UNIQUE (user_id, achievement_id),
    CONSTRAINT fk_user_achievements_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_user_achievements_achievement
        FOREIGN KEY (achievement_id)
        REFERENCES achievements (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_scores_total
    ON user_scores (total_score DESC);

CREATE INDEX IF NOT EXISTS idx_user_scores_season
    ON user_scores (season_score DESC);

CREATE INDEX IF NOT EXISTS idx_user_scores_monthly
    ON user_scores (monthly_score DESC);

CREATE INDEX IF NOT EXISTS idx_user_scores_weekly
    ON user_scores (weekly_score DESC);

CREATE INDEX IF NOT EXISTS idx_user_scores_level
    ON user_scores (user_level DESC);

CREATE INDEX IF NOT EXISTS idx_user_scores_streak
    ON user_scores (current_streak DESC);

CREATE INDEX IF NOT EXISTS idx_score_events_user
    ON score_events (user_id);

CREATE INDEX IF NOT EXISTS idx_score_events_created
    ON score_events (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_score_events_user_created
    ON score_events (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_active_powerups_user
    ON active_powerups (user_id);

CREATE INDEX IF NOT EXISTS idx_active_powerups_expires
    ON active_powerups (expires_at);
