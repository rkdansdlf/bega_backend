CREATE TABLE IF NOT EXISTS user_stadium_favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stadium_id VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (user_id, stadium_id)
);
CREATE INDEX idx_usf_user_id ON user_stadium_favorites(user_id);
