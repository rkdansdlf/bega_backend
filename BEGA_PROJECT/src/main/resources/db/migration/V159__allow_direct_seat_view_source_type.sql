DECLARE
    v_table_exists NUMBER := 0;
    v_constraint_exists NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_table_exists
      FROM user_tables
     WHERE table_name = 'SEAT_VIEW_PHOTO';

    IF v_table_exists > 0 THEN
        FOR source_type_constraint IN (
            SELECT constraint_name
              FROM user_constraints
             WHERE table_name = 'SEAT_VIEW_PHOTO'
               AND constraint_type = 'C'
               AND UPPER(search_condition_vc) LIKE '%SOURCE_TYPE%'
        ) LOOP
            EXECUTE IMMEDIATE 'ALTER TABLE seat_view_photo DROP CONSTRAINT ' || source_type_constraint.constraint_name;
        END LOOP;

        SELECT COUNT(*)
          INTO v_constraint_exists
          FROM user_constraints
         WHERE table_name = 'SEAT_VIEW_PHOTO'
           AND constraint_name = 'CK_SEAT_VIEW_PHOTO_SOURCE_TYPE';

        IF v_constraint_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE seat_view_photo ADD CONSTRAINT ck_seat_view_photo_source_type CHECK (source_type IN (''DIARY_UPLOAD'', ''TICKET_SCAN'', ''SEATMAP_UPLOAD''))';
        END IF;
    END IF;
END;
/
