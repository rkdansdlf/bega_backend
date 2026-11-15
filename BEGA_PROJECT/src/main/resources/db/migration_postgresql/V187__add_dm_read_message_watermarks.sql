ALTER TABLE dm_rooms
    ADD COLUMN IF NOT EXISTS participant_one_last_read_message_id BIGINT,
    ADD COLUMN IF NOT EXISTS participant_two_last_read_message_id BIGINT;
