-- Bootstrap shared KBO reference tables required by primary schema validation.
-- These tables are referenced by auth/homepage/team recommendation entities and
-- must exist before V52 bootstrap_auth_schema adds users.favorite_team FK.

CREATE TABLE IF NOT EXISTS team_franchises (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    original_code VARCHAR(10) NOT NULL UNIQUE,
    current_code VARCHAR(10) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata_json TEXT,
    web_url VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS teams (
    team_id VARCHAR(255) PRIMARY KEY,
    team_name VARCHAR(255) NOT NULL,
    team_short_name VARCHAR(255) NOT NULL,
    city VARCHAR(30) NOT NULL,
    stadium_name VARCHAR(50),
    founded_year INTEGER,
    color VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    franchise_id BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    aliases TEXT,
    CONSTRAINT fk_teams_franchise
        FOREIGN KEY (franchise_id)
        REFERENCES team_franchises (id)
        ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS team_history (
    id BIGSERIAL PRIMARY KEY,
    franchise_id BIGINT NOT NULL,
    season INTEGER NOT NULL,
    team_name VARCHAR(50) NOT NULL,
    team_code VARCHAR(10) NOT NULL,
    logo_url VARCHAR(255),
    ranking INTEGER,
    stadium VARCHAR(50),
    city VARCHAR(30),
    color VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_team_history_season_code UNIQUE (season, team_code),
    CONSTRAINT fk_team_history_franchise
        FOREIGN KEY (franchise_id)
        REFERENCES team_franchises (id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS team_profiles (
    team_id VARCHAR(255) NOT NULL,
    profile TEXT NOT NULL,
    CONSTRAINT fk_team_profiles_team
        FOREIGN KEY (team_id)
        REFERENCES teams (team_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS awards (
    id BIGSERIAL PRIMARY KEY,
    award_type VARCHAR(100) NOT NULL,
    player_name VARCHAR(100) NOT NULL,
    award_year INTEGER NOT NULL,
    position VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_teams_franchise_id
    ON teams (franchise_id);

CREATE INDEX IF NOT EXISTS idx_team_history_franchise_season
    ON team_history (franchise_id, season DESC);

CREATE INDEX IF NOT EXISTS idx_team_profiles_team_id
    ON team_profiles (team_id);
