CREATE TABLE IF NOT EXISTS dm_rooms (
    id BIGSERIAL PRIMARY KEY,
    participant_one_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    participant_two_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dm_rooms_participant_order CHECK (participant_one_id < participant_two_id),
    CONSTRAINT uk_dm_rooms_participants UNIQUE (participant_one_id, participant_two_id)
);

CREATE TABLE IF NOT EXISTS dm_messages (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES dm_rooms(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content VARCHAR(1000) NOT NULL,
    client_message_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_dm_messages_room_sender_client UNIQUE (room_id, sender_id, client_message_id)
);

CREATE INDEX IF NOT EXISTS idx_dm_messages_room_created_at
    ON dm_messages (room_id, created_at);
