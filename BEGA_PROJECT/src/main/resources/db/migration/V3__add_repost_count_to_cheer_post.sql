-- Add repost_count column to cheer_post table
ALTER TABLE cheer_post ADD COLUMN repostcount INTEGER NOT NULL DEFAULT 0;
