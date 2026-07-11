-- V167: Add composite indexes for public mate party list filters on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_parties_status_created_id
    ON parties (status, createdat DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_parties_team_status_created_id
    ON parties (teamid, status, createdat DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_parties_gamedate_status_created_id
    ON parties (gamedate, status, createdat DESC, id DESC);
