-- Align PostgreSQL schema with ClientErrorFeedbackEntity after avoiding reserved COMMENT usage.

DO $$
BEGIN
    IF to_regclass('public.client_error_feedback') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'client_error_feedback'
              AND column_name = 'comment'
        ) AND NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'client_error_feedback'
              AND column_name = 'feedback_comment'
        ) THEN
            ALTER TABLE public.client_error_feedback
                RENAME COLUMN comment TO feedback_comment;
        END IF;
    END IF;
END;
$$;
