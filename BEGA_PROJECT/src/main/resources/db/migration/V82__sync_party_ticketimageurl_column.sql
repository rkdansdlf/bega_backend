-- V82: Ensure parties.ticketimageurl exists for Party.ticketImageUrl mapping.
DECLARE
    v_has_ticketimageurl   NUMBER := 0;
    v_has_ticket_image_url NUMBER := 0;
BEGIN
    SELECT COUNT(*)
    INTO v_has_ticketimageurl
    FROM user_tab_columns
    WHERE table_name = 'PARTIES'
      AND column_name = 'TICKETIMAGEURL';

    IF v_has_ticketimageurl = 0 THEN
        SELECT COUNT(*)
        INTO v_has_ticket_image_url
        FROM user_tab_columns
        WHERE table_name = 'PARTIES'
          AND column_name = 'TICKET_IMAGE_URL';

        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (ticketimageurl VARCHAR2(500))';

        IF v_has_ticket_image_url > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET ticketimageurl = ticket_image_url WHERE ticketimageurl IS NULL';
        END IF;
    END IF;
END;
/
