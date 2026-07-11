-- V172: Add participant-count sort indexes for public mate party lists on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_parties_current_id
    ON parties (currentparticipants DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_parties_team_current_id
    ON parties (teamid, currentparticipants DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_parties_date_current_id
    ON parties (gamedate, currentparticipants DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_parties_status_current_id
    ON parties (status, currentparticipants DESC, id DESC);
