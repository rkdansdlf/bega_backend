-- V131: Backstop migration for environments where V128 history was repaired
-- after a duplicate-version conflict before profile_feed_image_url was actually added.

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
