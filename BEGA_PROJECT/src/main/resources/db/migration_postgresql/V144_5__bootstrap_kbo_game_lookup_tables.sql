-- Bootstrap KBO game lookup tables required by V145 and strict dev schema guard.
-- This mirrors the PostgreSQL baseball baseline shape needed by the local dev stack.

CREATE TABLE IF NOT EXISTS game (
    id BIGSERIAL NOT NULL,
    game_id VARCHAR(20) NOT NULL,
    game_date DATE,
    stadium VARCHAR(50),
    home_team VARCHAR(20),
    away_team VARCHAR(20),
    home_score INTEGER,
    away_score INTEGER,
    winning_team VARCHAR(20),
    winning_score INTEGER,
    season_id INTEGER,
    stadium_id VARCHAR(50),
    game_status VARCHAR(20),
    is_dummy BOOLEAN,
    home_pitcher VARCHAR(30),
    away_pitcher VARCHAR(30),
    CONSTRAINT pk_game PRIMARY KEY (id),
    CONSTRAINT uq_game_game_id UNIQUE (game_id)
);

CREATE TABLE IF NOT EXISTS kbo_seasons (
    season_id INTEGER NOT NULL,
    season_year INTEGER NOT NULL,
    league_type_code INTEGER NOT NULL,
    start_date DATE,
    CONSTRAINT pk_kbo_seasons PRIMARY KEY (season_id)
);

CREATE TABLE IF NOT EXISTS game_metadata (
    game_id VARCHAR(20) NOT NULL,
    stadium_code VARCHAR(20),
    stadium_name VARCHAR(50),
    attendance INTEGER,
    start_time TIME,
    end_time TIME,
    game_time_minutes INTEGER,
    weather VARCHAR(50),
    source_payload VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT pk_game_metadata PRIMARY KEY (game_id)
);

CREATE TABLE IF NOT EXISTS game_summary (
    id SERIAL NOT NULL,
    game_id VARCHAR(20) NOT NULL,
    summary_type VARCHAR(50),
    player_id INTEGER,
    player_name VARCHAR(50),
    detail_text VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT pk_game_summary PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS game_inning_scores (
    id SERIAL NOT NULL,
    game_id VARCHAR(20) NOT NULL,
    team_side VARCHAR(10) NOT NULL,
    team_code VARCHAR(10),
    inning INTEGER NOT NULL,
    runs INTEGER,
    is_extra BOOLEAN,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT pk_game_inning_scores PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_game_date
    ON game (game_date);

CREATE INDEX IF NOT EXISTS idx_game_season_id
    ON game (season_id);

CREATE INDEX IF NOT EXISTS idx_game_teams
    ON game (home_team, away_team);

CREATE INDEX IF NOT EXISTS idx_game_range_lookup
    ON game (game_date, game_status, season_id);
