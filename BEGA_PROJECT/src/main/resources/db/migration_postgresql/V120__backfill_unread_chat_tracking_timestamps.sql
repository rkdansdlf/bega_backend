DO $$
DECLARE
    has_party_createdat BOOLEAN;
    has_party_created_at BOOLEAN;
    has_application_createdat BOOLEAN;
    has_application_created_at BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'parties'
          AND column_name = 'createdat'
    ) INTO has_party_createdat;

    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'parties'
          AND column_name = 'created_at'
    ) INTO has_party_created_at;

    EXECUTE format(
        'UPDATE public.parties
            SET host_last_read_chat_at = COALESCE(host_last_read_chat_at%s%s, NOW())
          WHERE host_last_read_chat_at IS NULL',
        CASE WHEN has_party_createdat THEN ', createdat' ELSE '' END,
        CASE WHEN has_party_created_at THEN ', created_at' ELSE '' END
    );

    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'createdat'
    ) INTO has_application_createdat;

    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'created_at'
    ) INTO has_application_created_at;

    EXECUTE format(
        'UPDATE public.party_applications
            SET last_read_chat_at = COALESCE(last_read_chat_at%s%s, NOW())
          WHERE last_read_chat_at IS NULL',
        CASE WHEN has_application_created_at THEN ', created_at' ELSE '' END,
        CASE WHEN has_application_createdat THEN ', createdat' ELSE '' END
    );
END;
$$;
