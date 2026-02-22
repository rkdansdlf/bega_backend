DECLARE
  index_exists EXCEPTION;
  PRAGMA EXCEPTION_INIT(index_exists, -955); -- ORA-00955: name is already used by an existing object
  col_indexed EXCEPTION;
  PRAGMA EXCEPTION_INIT(col_indexed, -1408); -- ORA-01408: such column list already indexed
BEGIN
  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_post_type_created_at ON cheer_post (posttype, createdat DESC)';
  EXCEPTION
    WHEN index_exists OR col_indexed THEN NULL;
  END;

  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_team_post_type_created_at ON cheer_post (team_id, posttype, createdat DESC)';
  EXCEPTION
    WHEN index_exists OR col_indexed THEN NULL;
  END;
END;
/
