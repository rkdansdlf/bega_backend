-- Track read timestamps for unread chat count aggregation
ALTER TABLE parties
    ADD COLUMN IF NOT EXISTS host_last_read_chat_at TIMESTAMPTZ;

ALTER TABLE party_applications
    ADD COLUMN IF NOT EXISTS last_read_chat_at TIMESTAMPTZ;
