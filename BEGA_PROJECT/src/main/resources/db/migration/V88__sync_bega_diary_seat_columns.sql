-- V88: Ensure bega_diary seat columns exist for both legacy and current mappings.
-- Some deployments expect seatnumber/seatrow, while newer code uses seat_number/seat_row.

DECLARE
    v_has_table NUMBER := 0;
    v_has_seatnumber NUMBER := 0;
    v_has_seat_number NUMBER := 0;
    v_has_seatrow NUMBER := 0;
    v_has_seat_row NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_has_table
      FROM user_tables
     WHERE table_name = 'BEGA_DIARY';

    IF v_has_table > 0 THEN
        SELECT COUNT(*)
          INTO v_has_seatnumber
          FROM user_tab_cols
         WHERE table_name = 'BEGA_DIARY'
           AND column_name = 'SEATNUMBER';

        IF v_has_seatnumber = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE bega_diary ADD (seatnumber VARCHAR2(50))';
            v_has_seatnumber := 1;
        END IF;

        SELECT COUNT(*)
          INTO v_has_seat_number
          FROM user_tab_cols
         WHERE table_name = 'BEGA_DIARY'
           AND column_name = 'SEAT_NUMBER';

        IF v_has_seat_number = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE bega_diary ADD (seat_number VARCHAR2(50))';
            v_has_seat_number := 1;
        END IF;

        IF v_has_seatnumber = 1 AND v_has_seat_number = 1 THEN
            EXECUTE IMMEDIATE 'UPDATE bega_diary SET seat_number = seatnumber WHERE seat_number IS NULL AND seatnumber IS NOT NULL';
            EXECUTE IMMEDIATE 'UPDATE bega_diary SET seatnumber = seat_number WHERE seatnumber IS NULL AND seat_number IS NOT NULL';
        END IF;

        SELECT COUNT(*)
          INTO v_has_seatrow
          FROM user_tab_cols
         WHERE table_name = 'BEGA_DIARY'
           AND column_name = 'SEATROW';

        IF v_has_seatrow = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE bega_diary ADD (seatrow VARCHAR2(50))';
            v_has_seatrow := 1;
        END IF;

        SELECT COUNT(*)
          INTO v_has_seat_row
          FROM user_tab_cols
         WHERE table_name = 'BEGA_DIARY'
           AND column_name = 'SEAT_ROW';

        IF v_has_seat_row = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE bega_diary ADD (seat_row VARCHAR2(50))';
            v_has_seat_row := 1;
        END IF;

        IF v_has_seatrow = 1 AND v_has_seat_row = 1 THEN
            EXECUTE IMMEDIATE 'UPDATE bega_diary SET seat_row = seatrow WHERE seat_row IS NULL AND seatrow IS NOT NULL';
            EXECUTE IMMEDIATE 'UPDATE bega_diary SET seatrow = seat_row WHERE seatrow IS NULL AND seat_row IS NOT NULL';
        END IF;
    END IF;
END;
/
