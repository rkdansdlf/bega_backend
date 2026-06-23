-- V150: Create operator-data P0 recovery tables and lineup conflict target.

CREATE TABLE IF NOT EXISTS operator_data_items (
    queue_id TEXT PRIMARY KEY,
    priority TEXT,
    domain TEXT NOT NULL,
    contract_code TEXT NOT NULL,
    question TEXT NOT NULL,
    operator_status TEXT NOT NULL,
    validation_status TEXT NOT NULL,
    apply_target TEXT,
    payload JSONB NOT NULL,
    payload_hash TEXT NOT NULL,
    source_name TEXT NOT NULL,
    source_checked_at TIMESTAMPTZ NOT NULL,
    is_verified BOOLEAN NOT NULL,
    confidence NUMERIC NOT NULL,
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_operator_data_items_domain
    ON operator_data_items (domain);

CREATE INDEX IF NOT EXISTS idx_operator_data_items_payload_hash
    ON operator_data_items (payload_hash);

CREATE INDEX IF NOT EXISTS idx_operator_data_items_applied_at
    ON operator_data_items (applied_at);

CREATE TABLE IF NOT EXISTS operator_season_events (
    queue_id TEXT PRIMARY KEY REFERENCES operator_data_items(queue_id) ON DELETE CASCADE,
    season_year INTEGER NOT NULL,
    event_name TEXT NOT NULL,
    event_date DATE NOT NULL,
    stadium_name TEXT,
    payload_hash TEXT NOT NULL,
    source_name TEXT NOT NULL,
    source_checked_at TIMESTAMPTZ NOT NULL,
    is_verified BOOLEAN NOT NULL,
    confidence NUMERIC NOT NULL,
    applied_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_operator_season_events_lookup
    ON operator_season_events (season_year, event_date);

CREATE TABLE IF NOT EXISTS operator_schedule_items (
    queue_id TEXT PRIMARY KEY REFERENCES operator_data_items(queue_id) ON DELETE CASCADE,
    game_date DATE NOT NULL,
    game_id TEXT NOT NULL,
    home_team TEXT NOT NULL,
    away_team TEXT NOT NULL,
    stadium_name TEXT,
    start_time TEXT NOT NULL,
    game_status TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    source_name TEXT NOT NULL,
    source_checked_at TIMESTAMPTZ NOT NULL,
    is_verified BOOLEAN NOT NULL,
    confidence NUMERIC NOT NULL,
    applied_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_operator_schedule_items_date
    ON operator_schedule_items (game_date, start_time);

CREATE INDEX IF NOT EXISTS idx_operator_schedule_items_game_id
    ON operator_schedule_items (game_id);

CREATE INDEX IF NOT EXISTS idx_operator_schedule_items_teams
    ON operator_schedule_items (home_team, away_team);

CREATE TABLE IF NOT EXISTS operator_roster_events (
    queue_id TEXT PRIMARY KEY REFERENCES operator_data_items(queue_id) ON DELETE CASCADE,
    season_year INTEGER NOT NULL,
    team_code TEXT NOT NULL,
    player_name TEXT NOT NULL,
    roster_event_type TEXT NOT NULL,
    effective_date DATE NOT NULL,
    status_text TEXT NOT NULL,
    payload_hash TEXT NOT NULL,
    source_name TEXT NOT NULL,
    source_checked_at TIMESTAMPTZ NOT NULL,
    is_verified BOOLEAN NOT NULL,
    confidence NUMERIC NOT NULL,
    applied_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_operator_roster_events_lookup
    ON operator_roster_events (season_year, team_code, effective_date);

CREATE INDEX IF NOT EXISTS idx_operator_roster_events_player
    ON operator_roster_events (player_name);

CREATE TABLE IF NOT EXISTS game_lineups (
    game_id TEXT NOT NULL,
    team_side TEXT,
    team_code TEXT NOT NULL,
    player_id TEXT NOT NULL,
    player_name TEXT,
    batting_order INTEGER NOT NULL,
    position TEXT,
    is_starter BOOLEAN,
    appearance_seq INTEGER,
    notes TEXT,
    uniform_no TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE game_lineups
    ADD COLUMN IF NOT EXISTS team_side TEXT;

ALTER TABLE game_lineups
    ADD COLUMN IF NOT EXISTS player_name TEXT;

ALTER TABLE game_lineups
    ADD COLUMN IF NOT EXISTS is_starter BOOLEAN;

ALTER TABLE game_lineups
    ADD COLUMN IF NOT EXISTS appearance_seq INTEGER;

ALTER TABLE game_lineups
    ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE game_lineups
    ADD COLUMN IF NOT EXISTS uniform_no TEXT;

ALTER TABLE game_lineups
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT now();

ALTER TABLE game_lineups
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT now();

WITH ranked_game_lineups AS (
    SELECT
        ctid,
        ROW_NUMBER() OVER (
            PARTITION BY game_id, team_code, batting_order
            ORDER BY
                CASE WHEN is_starter IS TRUE THEN 0 ELSE 1 END,
                CASE WHEN notes::TEXT ILIKE '%manual_lineup%' THEN 0 ELSE 1 END,
                COALESCE(updated_at, created_at, TIMESTAMPTZ 'epoch') DESC,
                COALESCE(created_at, TIMESTAMPTZ 'epoch') DESC,
                COALESCE(NULLIF(player_id::TEXT, ''), '~') ASC,
                COALESCE(NULLIF(player_name, ''), '~') ASC,
                ctid
        ) AS duplicate_rank
    FROM game_lineups
    WHERE game_id IS NOT NULL
      AND team_code IS NOT NULL
      AND batting_order IS NOT NULL
)
DELETE FROM game_lineups gl
USING ranked_game_lineups ranked
WHERE gl.ctid = ranked.ctid
  AND ranked.duplicate_rank > 1;

CREATE UNIQUE INDEX IF NOT EXISTS ux_game_lineups_game_team_batting_order
    ON game_lineups (game_id, team_code, batting_order);

CREATE INDEX IF NOT EXISTS idx_game_lineups_game_id
    ON game_lineups (game_id);

CREATE INDEX IF NOT EXISTS idx_game_lineups_team_code
    ON game_lineups (team_code);
