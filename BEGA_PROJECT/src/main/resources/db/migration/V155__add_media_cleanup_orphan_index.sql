DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'MEDIA_ASSETS'
       AND index_name = 'IDX_MEDIA_ASSETS_STATUS_CREATED_ID';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_media_assets_status_created_id ON media_assets(status, created_at, id)';
    END IF;
END;
/
