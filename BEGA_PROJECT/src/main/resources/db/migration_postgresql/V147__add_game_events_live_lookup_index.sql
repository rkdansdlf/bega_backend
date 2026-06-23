-- V147: Speed up live summary latest-event lookups on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_game_events_live_lookup
  ON game_events(game_id, event_seq);
