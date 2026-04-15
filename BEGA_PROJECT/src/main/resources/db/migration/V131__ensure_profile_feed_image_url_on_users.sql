-- V131: Backstop migration for environments where V128 history was repaired
-- after a duplicate-version conflict before profile_feed_image_url was actually added.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'USERS'
       AND column_name = 'PROFILE_FEED_IMAGE_URL';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE users ADD (profile_feed_image_url VARCHAR2(2048))';
    END IF;
END;
/
