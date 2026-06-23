-- V158: Add user/post indexes for Cheer viewer status batch lookups on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_cheer_post_like_user_post
    ON cheer_post_like (user_id, post_id);

CREATE INDEX IF NOT EXISTS idx_cheer_post_bookmark_user_post
    ON cheer_post_bookmark (user_id, post_id);

CREATE INDEX IF NOT EXISTS idx_cheer_post_repost_user_post
    ON cheer_post_repost (user_id, post_id);
