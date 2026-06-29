CREATE TABLE IF NOT EXISTS user_party_favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    party_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (user_id, party_id)
);
CREATE INDEX IF NOT EXISTS idx_upf_user_id ON user_party_favorites(user_id);
