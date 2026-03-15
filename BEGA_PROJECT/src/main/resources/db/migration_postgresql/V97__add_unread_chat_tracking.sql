-- Track read timestamps for unread chat count aggregation
ALTER TABLE IF EXISTS parties
    ADD COLUMN IF NOT EXISTS host_last_read_chat_at TIMESTAMPTZ;

ALTER TABLE IF EXISTS party_applications
    ADD COLUMN IF NOT EXISTS last_read_chat_at TIMESTAMPTZ;
