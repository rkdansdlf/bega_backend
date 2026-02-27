-- V98: Sync chat_messages created_at/createdat legacy timestamp columns.
-- Some environments still keep legacy createdat (NOT NULL) without default, which breaks inserts.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
    ) THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'created_at'
    ) THEN
        ALTER TABLE chat_messages ADD COLUMN created_at TIMESTAMPTZ;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'createdat'
    ) THEN
        EXECUTE '
            UPDATE chat_messages
            SET created_at = COALESCE(created_at, createdat)
            WHERE created_at IS NULL
        ';

        EXECUTE '
            UPDATE chat_messages
            SET createdat = COALESCE(createdat, created_at, NOW())
            WHERE createdat IS NULL
        ';

        EXECUTE '
            ALTER TABLE chat_messages
            ALTER COLUMN createdat SET DEFAULT NOW()
        ';
    END IF;
END;
$$;

UPDATE chat_messages
SET created_at = COALESCE(created_at, NOW())
WHERE created_at IS NULL;

ALTER TABLE chat_messages
    ALTER COLUMN created_at SET DEFAULT NOW(),
    ALTER COLUMN created_at SET NOT NULL;
