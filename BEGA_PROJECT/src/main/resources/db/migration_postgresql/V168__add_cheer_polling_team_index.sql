-- V168: Add team/id index for Cheer post change polling on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_cheer_post_team_id_desc
    ON cheer_post (team_id, id DESC);
