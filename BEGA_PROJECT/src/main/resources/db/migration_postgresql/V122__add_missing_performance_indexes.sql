-- V122: Add missing performance indexes for frequently queried columns

-- 1. JWT filter: every request checks users(enabled, locked) for account status
DO $$
BEGIN
    IF to_regclass('public.users') IS NULL THEN
        RETURN;
    END IF;

    CREATE INDEX IF NOT EXISTS idx_users_enabled_locked
        ON users (enabled, locked)
        WHERE enabled = true AND locked = false;
END $$;

-- 2. Cheer post author feed: findByAuthor / author-scoped queries
DO $$
BEGIN
    IF to_regclass('public.cheer_post') IS NULL THEN
        RETURN;
    END IF;

    CREATE INDEX IF NOT EXISTS idx_cheer_post_author_createdat
        ON cheer_post (author_id, createdat DESC);
END $$;
