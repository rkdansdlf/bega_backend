-- Add missing content column for CheerPostReport mapping
DO $$
BEGIN
    IF to_regclass('public.cheer_post_reports') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS content text;
END $$;
