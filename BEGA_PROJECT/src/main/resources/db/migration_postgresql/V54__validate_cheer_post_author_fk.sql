-- V54: Validate cheer_post.author_id foreign key after V53
-- This migration only validates when no orphaned rows exist, so startup stays safe.

DO $$
DECLARE
    v_constraint_name text;
    v_orphan_count bigint;
BEGIN
    IF to_regclass('public.cheer_post') IS NULL OR to_regclass('public.users') IS NULL THEN
        RETURN;
    END IF;

    SELECT tc.constraint_name
    INTO v_constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
     AND tc.table_schema = kcu.table_schema
    WHERE tc.table_schema = 'public'
      AND tc.table_name = 'cheer_post'
      AND tc.constraint_type = 'FOREIGN KEY'
      AND kcu.column_name = 'author_id'
    LIMIT 1;

    IF v_constraint_name IS NULL THEN
        RETURN;
    END IF;

    SELECT COUNT(*)
    INTO v_orphan_count
    FROM public.cheer_post cp
    WHERE NOT EXISTS (
        SELECT 1
        FROM public.users u
        WHERE u.id = cp.author_id
    );

    IF v_orphan_count > 0 THEN
        RETURN;
    END IF;

    EXECUTE format(
        'ALTER TABLE public.cheer_post VALIDATE CONSTRAINT %I',
        v_constraint_name
    );
END $$;
