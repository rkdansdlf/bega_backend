-- V155: Guard home bootstrap and auth lookup paths against migration drift on PostgreSQL.

DO $$
DECLARE
    v_game_date_attnum smallint;
BEGIN
    IF to_regclass('public.game') IS NOT NULL THEN
        SELECT attnum
          INTO v_game_date_attnum
          FROM pg_attribute
         WHERE attrelid = 'public.game'::regclass
           AND attname = 'game_date'
           AND NOT attisdropped;

        IF v_game_date_attnum IS NOT NULL
           AND NOT EXISTS (
                SELECT 1
                  FROM pg_index i
                 WHERE i.indrelid = 'public.game'::regclass
                   AND i.indisvalid
                   AND i.indpred IS NULL
                   AND i.indkey[0] = v_game_date_attnum
           ) THEN
            EXECUTE 'CREATE INDEX idx_game_date_lookup ON game (game_date)';
        END IF;

        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_game_home_scheduled_window
                 ON game (game_date, upper(game_status), game_id)
                 WHERE is_dummy IS NOT TRUE
                   AND game_id NOT LIKE ''MOCK%''';
    END IF;
END $$;

DO $$
DECLARE
    v_email_attnum smallint;
    v_token_attnum smallint;
BEGIN
    IF to_regclass('public.refresh_tokens') IS NOT NULL THEN
        SELECT attnum
          INTO v_email_attnum
          FROM pg_attribute
         WHERE attrelid = 'public.refresh_tokens'::regclass
           AND attname = 'email'
           AND NOT attisdropped;

        IF v_email_attnum IS NOT NULL
           AND NOT EXISTS (
                SELECT 1
                  FROM pg_index i
                 WHERE i.indrelid = 'public.refresh_tokens'::regclass
                   AND i.indisvalid
                   AND i.indkey[0] = v_email_attnum
           ) THEN
            EXECUTE 'CREATE INDEX idx_refresh_tokens_email_lookup ON refresh_tokens (email)';
        END IF;

        SELECT attnum
          INTO v_token_attnum
          FROM pg_attribute
         WHERE attrelid = 'public.refresh_tokens'::regclass
           AND attname = 'token'
           AND NOT attisdropped;

        IF v_token_attnum IS NOT NULL
           AND NOT EXISTS (
                SELECT 1
                  FROM pg_index i
                 WHERE i.indrelid = 'public.refresh_tokens'::regclass
                   AND i.indisvalid
                   AND i.indkey[0] = v_token_attnum
           ) THEN
            EXECUTE 'CREATE INDEX idx_refresh_tokens_token_lookup ON refresh_tokens (token)';
        END IF;
    END IF;
END $$;

DO $$
DECLARE
    v_email_attnum smallint;
BEGIN
    IF to_regclass('public.users') IS NOT NULL THEN
        SELECT attnum
          INTO v_email_attnum
          FROM pg_attribute
         WHERE attrelid = 'public.users'::regclass
           AND attname = 'email'
           AND NOT attisdropped;

        IF v_email_attnum IS NOT NULL
           AND NOT EXISTS (
                SELECT 1
                  FROM pg_index i
                 WHERE i.indrelid = 'public.users'::regclass
                   AND i.indisvalid
                   AND i.indkey[0] = v_email_attnum
           ) THEN
            EXECUTE 'CREATE INDEX idx_users_email_lookup ON users (email)';
        END IF;
    END IF;
END $$;
