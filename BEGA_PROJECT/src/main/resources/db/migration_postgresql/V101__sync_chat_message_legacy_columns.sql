DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'partyid'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'party_id'
    ) THEN
        EXECUTE 'UPDATE public.chat_messages SET partyid = party_id WHERE partyid IS NULL AND party_id IS NOT NULL';
        EXECUTE 'UPDATE public.chat_messages SET party_id = partyid WHERE party_id IS NULL AND partyid IS NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'senderid'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'sender_id'
    ) THEN
        EXECUTE 'UPDATE public.chat_messages SET senderid = sender_id WHERE senderid IS NULL AND sender_id IS NOT NULL';
        EXECUTE 'UPDATE public.chat_messages SET sender_id = senderid WHERE sender_id IS NULL AND senderid IS NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'sendername'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'sender_name'
    ) THEN
        EXECUTE 'UPDATE public.chat_messages SET sendername = sender_name WHERE sendername IS NULL AND sender_name IS NOT NULL';
        EXECUTE 'UPDATE public.chat_messages SET sender_name = sendername WHERE sender_name IS NULL AND sendername IS NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'createdat'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'created_at'
    ) THEN
        EXECUTE 'UPDATE public.chat_messages SET createdat = created_at WHERE createdat IS NULL AND created_at IS NOT NULL';
        EXECUTE 'UPDATE public.chat_messages SET created_at = createdat WHERE created_at IS NULL AND createdat IS NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'partyid'
          AND is_nullable = 'NO'
    ) THEN
        EXECUTE 'ALTER TABLE public.chat_messages ALTER COLUMN partyid DROP NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'senderid'
          AND is_nullable = 'NO'
    ) THEN
        EXECUTE 'ALTER TABLE public.chat_messages ALTER COLUMN senderid DROP NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'chat_messages'
          AND column_name = 'sendername'
          AND is_nullable = 'NO'
    ) THEN
        EXECUTE 'ALTER TABLE public.chat_messages ALTER COLUMN sendername DROP NOT NULL';
    END IF;
END
$$;
