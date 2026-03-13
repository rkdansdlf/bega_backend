-- V106: Ensure bega_diary.game_id foreign key to game.id is present without
-- blocking startup on legacy orphan rows.
-- - If FK is missing, create it as NOT VALID so existing bad rows do not fail startup.
-- - If no orphan rows remain, validate the FK.

DO $$
DECLARE
    v_constraint_name text;
    v_orphan_count bigint;
BEGIN
    IF to_regclass('public.bega_diary') IS NULL
       OR to_regclass('public.game') IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'bega_diary'
          AND column_name = 'game_id'
    ) OR NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'game'
          AND column_name = 'id'
    ) THEN
        RETURN;
    END IF;

    SELECT tc.constraint_name
    INTO v_constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.key_column_usage kcu
      ON tc.constraint_name = kcu.constraint_name
     AND tc.table_schema = kcu.table_schema
    JOIN information_schema.constraint_column_usage ccu
      ON tc.constraint_name = ccu.constraint_name
     AND tc.table_schema = ccu.table_schema
    WHERE tc.table_schema = 'public'
      AND tc.table_name = 'bega_diary'
      AND tc.constraint_type = 'FOREIGN KEY'
      AND kcu.column_name = 'game_id'
      AND ccu.table_schema = 'public'
      AND ccu.table_name = 'game'
      AND ccu.column_name = 'id'
    LIMIT 1;

    IF v_constraint_name IS NULL THEN
        v_constraint_name := 'fkjgbnwssi3o44cc3xuiu7iilym';
        EXECUTE format(
            'ALTER TABLE public.bega_diary ADD CONSTRAINT %I FOREIGN KEY (game_id) REFERENCES public.game(id) NOT VALID',
            v_constraint_name
        );
    END IF;

    SELECT COUNT(*)
    INTO v_orphan_count
    FROM public.bega_diary bd
    LEFT JOIN public.game g
      ON g.id = bd.game_id
    WHERE bd.game_id IS NOT NULL
      AND g.id IS NULL;

    IF v_orphan_count = 0
       AND EXISTS (
           SELECT 1
           FROM pg_constraint con
           JOIN pg_class rel
             ON rel.oid = con.conrelid
           JOIN pg_namespace nsp
             ON nsp.oid = rel.relnamespace
           WHERE nsp.nspname = 'public'
             AND rel.relname = 'bega_diary'
             AND con.conname = v_constraint_name
             AND con.contype = 'f'
             AND con.convalidated = false
       ) THEN
        EXECUTE format(
            'ALTER TABLE public.bega_diary VALIDATE CONSTRAINT %I',
            v_constraint_name
        );
    END IF;
END $$;
