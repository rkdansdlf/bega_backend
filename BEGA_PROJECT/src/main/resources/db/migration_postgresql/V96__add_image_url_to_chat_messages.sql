-- Add optional image attachment path to chat messages
ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(2048);
