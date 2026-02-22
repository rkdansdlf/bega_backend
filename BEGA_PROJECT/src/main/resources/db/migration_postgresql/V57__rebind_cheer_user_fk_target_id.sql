-- V57: Rebind cheer user-reference foreign keys to the current public.users(id).
-- This handles stale FK metadata where references drift to an older users relation.

DO $$
DECLARE
    v_constraint RECORD;
    v_target RECORD;
BEGIN
    IF to_regclass('public.users') IS NULL THEN
        RETURN;
    END IF;

    FOR v_constraint IN
        SELECT tc.table_name, tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu
          ON tc.table_schema = kcu.table_schema
         AND tc.table_name = kcu.table_name
         AND tc.constraint_name = kcu.constraint_name
        LEFT JOIN information_schema.constraint_column_usage ccu
          ON tc.table_schema = ccu.table_schema
         AND tc.constraint_name = ccu.constraint_name
        WHERE tc.table_schema = 'public'
          AND tc.constraint_type = 'FOREIGN KEY'
          AND (
                (tc.table_name = 'cheer_post' AND kcu.column_name = 'author_id') OR
                (tc.table_name = 'cheer_comment' AND kcu.column_name = 'author_id') OR
                (tc.table_name = 'cheer_comment_like' AND kcu.column_name = 'user_id') OR
                (tc.table_name = 'cheer_post_like' AND kcu.column_name = 'user_id') OR
                (tc.table_name = 'cheer_post_bookmark' AND kcu.column_name = 'user_id') OR
                (tc.table_name = 'cheer_post_repost' AND kcu.column_name = 'user_id') OR
                (tc.table_name = 'cheer_post_reports' AND kcu.column_name = 'reporter_id')
          )
          AND (
            ccu.table_schema IS DISTINCT FROM 'public'
            OR ccu.table_name IS DISTINCT FROM 'users'
            OR ccu.column_name IS DISTINCT FROM 'id'
            OR tc.constraint_name NOT IN (
                'fk_cheer_post_author_id',
                'fk_cheer_comment_author_id',
                'fk_cheer_comment_like_user_id',
                'fk_cheer_post_like_user_id',
                'fk_cheer_post_bookmark_user_id',
                'fk_cheer_post_repost_user_id',
                'fk_cheer_post_reports_reporter_id'
            )
          )
    LOOP
        EXECUTE format(
            'ALTER TABLE public.%I DROP CONSTRAINT %I',
            v_constraint.table_name,
            v_constraint.constraint_name
        );
    END LOOP;

    FOR v_target IN
        SELECT * FROM (
            VALUES
                ('cheer_post',         'author_id',       'fk_cheer_post_author_id'),
                ('cheer_comment',      'author_id',       'fk_cheer_comment_author_id'),
                ('cheer_comment_like', 'user_id',         'fk_cheer_comment_like_user_id'),
                ('cheer_post_like',    'user_id',         'fk_cheer_post_like_user_id'),
                ('cheer_post_bookmark','user_id',         'fk_cheer_post_bookmark_user_id'),
                ('cheer_post_repost',  'user_id',         'fk_cheer_post_repost_user_id'),
                ('cheer_post_reports', 'reporter_id',     'fk_cheer_post_reports_reporter_id')
        ) AS v(table_name, column_name, constraint_name)
    LOOP
        IF to_regclass(format('public.%I', v_target.table_name)) IS NULL THEN
            CONTINUE;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints tc
            WHERE tc.table_schema = 'public'
              AND tc.table_name = v_target.table_name
              AND tc.constraint_name = v_target.constraint_name
              AND tc.constraint_type = 'FOREIGN KEY'
        ) THEN
            EXECUTE format(
                'ALTER TABLE public.%I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES public.users(id) NOT VALID',
                v_target.table_name,
                v_target.constraint_name,
                v_target.column_name
            );
        END IF;
    END LOOP;
END $$;
