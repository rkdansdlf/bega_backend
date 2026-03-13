-- V74: Ensure legacy and underscored party participant count columns both exist.
-- Keep compatibility with environments where columns are named with underscores.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'MAXPARTICIPANTS';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (maxparticipants NUMBER)';

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'MAX_PARTICIPANTS';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET maxparticipants = max_participants WHERE maxparticipants IS NULL AND max_participants IS NOT NULL';
        END IF;
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'CURRENTPARTICIPANTS';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (currentparticipants NUMBER)';

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'CURRENT_PARTICIPANTS';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET currentparticipants = current_participants WHERE currentparticipants IS NULL AND current_participants IS NOT NULL';
        END IF;
    END IF;

END;
/
