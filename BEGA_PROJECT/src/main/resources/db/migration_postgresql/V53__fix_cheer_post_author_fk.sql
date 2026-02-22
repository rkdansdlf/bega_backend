-- V53: Fix stale FK constraint on cheer_post.author_id -> public.users
-- Handles two PostgreSQL dev cases:
--   1) users/cheer_post were created before/after with stale FK metadata
--   2) orphaned cheer_post rows exist temporarily after schema reset
--
-- This migration makes FK repair idempotent and avoids startup failure from
-- old-orphaned rows by re-adding the FK as NOT VALID.

DO $$
DECLARE
    v_constraint RECORD;
    v_orphan_count BIGINT;
BEGIN
    IF to_regclass('public.cheer_post') IS NULL OR to_regclass('public.users') IS NULL THEN
        RETURN;
    END IF;

    FOR v_constraint IN
        SELECT tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.constraint_name = kcu.constraint_name
         AND tc.table_schema = kcu.table_schema
        WHERE tc.constraint_type = 'FOREIGN KEY'
          AND tc.table_schema = 'public'
          AND tc.table_name = 'cheer_post'
          AND kcu.column_name = 'author_id'
    LOOP
        EXECUTE format('ALTER TABLE public.cheer_post DROP CONSTRAINT %I', v_constraint.constraint_name);
    END LOOP;

    SELECT COUNT(*)
    INTO v_orphan_count
    FROM public.cheer_post cp
    WHERE NOT EXISTS (
        SELECT 1
        FROM public.users u
        WHERE u.id = cp.author_id
    );

    IF v_orphan_count > 0 THEN
        RAISE WARNING 'V53 detected % orphaned cheer_post rows (author_id does not exist in public.users).', v_orphan_count;
    END IF;

    EXECUTE '
        ALTER TABLE public.cheer_post
        ADD CONSTRAINT fk_cheer_post_author_id
        FOREIGN KEY (author_id)
        REFERENCES public.users(id)
        NOT VALID
    ';
END $$;
