-- Bootstrap game event tables required by V147 and strict dev schema guard.

CREATE TABLE IF NOT EXISTS game_events (
    id SERIAL PRIMARY KEY,
    game_id VARCHAR(20) NOT NULL,
    event_seq INTEGER NOT NULL,
    inning INTEGER,
    inning_half VARCHAR(255),
    outs INTEGER,
    batter_id INTEGER,
    batter_name VARCHAR(255),
    pitcher_id INTEGER,
    pitcher_name VARCHAR(255),
    description TEXT,
    event_type VARCHAR(255),
    result_code VARCHAR(255),
    rbi INTEGER,
    bases_before VARCHAR(255),
    bases_after VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    wpa DOUBLE PRECISION,
    win_expectancy_before DOUBLE PRECISION,
    win_expectancy_after DOUBLE PRECISION,
    score_diff INTEGER,
    base_state INTEGER,
    home_score INTEGER,
    away_score INTEGER
);

CREATE TABLE IF NOT EXISTS game_play_by_play (
    id SERIAL PRIMARY KEY,
    game_id VARCHAR(20) NOT NULL,
    inning INTEGER,
    inning_half VARCHAR(10),
    pitcher_name VARCHAR(50),
    batter_name VARCHAR(50),
    play_description VARCHAR(1000),
    event_type VARCHAR(50),
    result VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
