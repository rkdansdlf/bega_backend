ALTER TABLE IF EXISTS public.bega_diary
    ALTER COLUMN winning DROP NOT NULL;

DO $$
DECLARE
    v_constraint_name text;
BEGIN
    IF to_regclass('public.bega_diary') IS NULL THEN
        RETURN;
    END IF;

    FOR v_constraint_name IN
        SELECT tc.constraint_name
          FROM information_schema.table_constraints tc
          JOIN information_schema.key_column_usage kcu
            ON tc.constraint_schema = kcu.constraint_schema
           AND tc.constraint_name = kcu.constraint_name
           AND tc.table_schema = kcu.table_schema
           AND tc.table_name = kcu.table_name
         WHERE tc.table_schema = 'public'
           AND tc.table_name = 'bega_diary'
           AND tc.constraint_type = 'UNIQUE'
         GROUP BY tc.constraint_name
        HAVING COUNT(*) = 1
           AND MAX(kcu.column_name) = 'diarydate'
    LOOP
        EXECUTE format('ALTER TABLE public.bega_diary DROP CONSTRAINT IF EXISTS %I', v_constraint_name);
    END LOOP;
END $$;

DO $$
BEGIN
    IF to_regclass('public.bega_diary') IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.table_constraints tc
          JOIN information_schema.key_column_usage kcu
            ON tc.constraint_schema = kcu.constraint_schema
           AND tc.constraint_name = kcu.constraint_name
           AND tc.table_schema = kcu.table_schema
           AND tc.table_name = kcu.table_name
         WHERE tc.table_schema = 'public'
           AND tc.table_name = 'bega_diary'
           AND tc.constraint_type = 'UNIQUE'
           AND kcu.column_name IN ('user_id', 'diarydate')
         GROUP BY tc.constraint_name
        HAVING COUNT(*) = 2
           AND SUM(CASE WHEN kcu.column_name = 'user_id' THEN 1 ELSE 0 END) = 1
           AND SUM(CASE WHEN kcu.column_name = 'diarydate' THEN 1 ELSE 0 END) = 1
    ) THEN
        ALTER TABLE public.bega_diary
            ADD CONSTRAINT uk_bega_diary_user_date UNIQUE (user_id, diarydate);
    END IF;
END $$;
