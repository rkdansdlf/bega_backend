-- V159: Add read-tracking columns to dm_rooms for DM Inbox unread badge support.
ALTER TABLE dm_rooms
    ADD COLUMN IF NOT EXISTS participant_one_last_read_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS participant_two_last_read_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_dm_rooms_participant_one
    ON dm_rooms (participant_one_id, created_at);

CREATE INDEX IF NOT EXISTS idx_dm_rooms_participant_two
    ON dm_rooms (participant_two_id, created_at);
