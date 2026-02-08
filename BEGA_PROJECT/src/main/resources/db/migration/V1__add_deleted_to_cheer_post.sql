-- Migration: Add 'deleted' column to cheer_post table
-- Oracle compatible version with existence check

BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD (deleted NUMBER(1,0) DEFAULT 0 NOT NULL)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1430 THEN -- ORA-01430: column being added already exists in table
      RAISE;
    END IF;
END;
/
