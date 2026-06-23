-- V153: Add user/post indexes for Cheer viewer status batch lookups on Oracle.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'CHEER_POST_LIKE'
       AND index_name = 'IDX_CHEER_POST_LIKE_USER_POST';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_post_like_user_post ON cheer_post_like(user_id, post_id)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'CHEER_POST_BOOKMARK'
       AND index_name = 'IDX_CHEER_POST_BOOKMARK_USER_POST';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_post_bookmark_user_post ON cheer_post_bookmark(user_id, post_id)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'CHEER_POST_REPOST'
       AND index_name = 'IDX_CHEER_POST_REPOST_USER_POST';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_post_repost_user_post ON cheer_post_repost(user_id, post_id)';
    END IF;
END;
/
