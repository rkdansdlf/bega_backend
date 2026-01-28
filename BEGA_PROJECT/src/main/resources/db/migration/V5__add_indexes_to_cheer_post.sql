CREATE INDEX idx_cheer_post_type_created_at ON cheer_post (posttype, createdat DESC);
CREATE INDEX idx_cheer_team_post_type_created_at ON cheer_post (team_id, posttype, createdat DESC);
