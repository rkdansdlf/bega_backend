-- Drop title column from cheer_post table
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE cheer_post DROP COLUMN title';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -904 THEN
      RAISE;
    END IF;
END;
/
