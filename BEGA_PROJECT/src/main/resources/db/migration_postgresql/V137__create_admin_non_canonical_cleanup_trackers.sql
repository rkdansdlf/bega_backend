CREATE TABLE IF NOT EXISTS admin_non_canonical_cleanup_trackers (
    id BIGSERIAL PRIMARY KEY,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    ticket_url VARCHAR(500),
    assignee VARCHAR(120),
    status VARCHAR(32) NOT NULL,
    note_text TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by_admin_id BIGINT,
    game_ids_json TEXT NOT NULL DEFAULT '[]',
    CONSTRAINT uk_admin_non_canonical_cleanup_tracker_range UNIQUE (start_date, end_date)
);

CREATE INDEX IF NOT EXISTS idx_admin_non_canonical_cleanup_trackers_updated_at
    ON admin_non_canonical_cleanup_trackers (updated_at DESC);
