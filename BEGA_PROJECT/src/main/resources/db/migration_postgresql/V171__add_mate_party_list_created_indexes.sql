-- V171: Add created-at pagination indexes for public mate party lists on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_parties_created_id
    ON parties (createdat DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_parties_team_created_id
    ON parties (teamid, createdat DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_parties_gamedate_created_id
    ON parties (gamedate, createdat DESC, id DESC);
