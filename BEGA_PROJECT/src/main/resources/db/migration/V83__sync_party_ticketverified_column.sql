-- V83: Ensure parties.ticketverified exists for Party.ticketVerified mapping.

DECLARE
    v_has_ticketverified NUMBER := 0;
BEGIN
    SELECT COUNT(*)
    INTO v_has_ticketverified
    FROM user_tab_columns
    WHERE table_name = 'PARTIES'
      AND column_name = 'TICKETVERIFIED';

    IF v_has_ticketverified = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (ticketverified NUMBER(1))';
        EXECUTE IMMEDIATE 'UPDATE parties SET ticketverified = 0 WHERE ticketverified IS NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (ticketverified DEFAULT 0 NOT NULL)';
    END IF;
END;
/
