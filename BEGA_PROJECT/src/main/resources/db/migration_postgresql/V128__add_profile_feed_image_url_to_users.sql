-- V128: add profile_feed_image_url for Cheer feed thumbnail usage on PostgreSQL.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'users'
          AND column_name = 'profile_feed_image_url'
    ) THEN
        ALTER TABLE users ADD COLUMN profile_feed_image_url VARCHAR(2048);
    END IF;
END;
$$;

