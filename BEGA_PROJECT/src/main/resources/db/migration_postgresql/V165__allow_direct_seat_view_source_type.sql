DO $$
DECLARE
    source_type_constraint RECORD;
BEGIN
    IF to_regclass('public.seat_view_photo') IS NOT NULL THEN
        FOR source_type_constraint IN
            SELECT conname
              FROM pg_constraint
             WHERE conrelid = to_regclass('public.seat_view_photo')
               AND contype = 'c'
               AND pg_get_constraintdef(oid) ILIKE '%source_type%'
        LOOP
            EXECUTE format('ALTER TABLE seat_view_photo DROP CONSTRAINT %I', source_type_constraint.conname);
        END LOOP;

        ALTER TABLE seat_view_photo
            ADD CONSTRAINT seat_view_photo_source_type_check
            CHECK (source_type IN ('DIARY_UPLOAD', 'TICKET_SCAN', 'SEATMAP_UPLOAD'));
    END IF;
END $$;
