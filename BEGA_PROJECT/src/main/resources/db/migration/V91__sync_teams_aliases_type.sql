-- V91: Align teams.aliases with LONGVARCHAR mapping.

DECLARE
    v_has_table NUMBER := 0;
    v_has_aliases NUMBER := 0;
    v_has_aliases_vc NUMBER := 0;
    v_has_aliases_clob NUMBER := 0;
    v_data_type VARCHAR2(128);
    v_max_len NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_has_table
      FROM user_tables
     WHERE table_name = 'TEAMS';

    IF v_has_table = 0 THEN
        RETURN;
    END IF;

    SELECT COUNT(*)
      INTO v_has_aliases
      FROM user_tab_cols
     WHERE table_name = 'TEAMS'
       AND column_name = 'ALIASES';

    SELECT COUNT(*)
      INTO v_has_aliases_vc
      FROM user_tab_cols
     WHERE table_name = 'TEAMS'
       AND column_name = 'ALIASES_VC';

    SELECT COUNT(*)
      INTO v_has_aliases_clob
      FROM user_tab_cols
     WHERE table_name = 'TEAMS'
       AND column_name = 'ALIASES_CLOB_BAK';

    IF v_has_aliases = 1 THEN
        SELECT data_type
          INTO v_data_type
          FROM user_tab_cols
         WHERE table_name = 'TEAMS'
           AND column_name = 'ALIASES';

        IF v_data_type = 'CLOB' THEN
            IF v_has_aliases_vc = 0 THEN
                EXECUTE IMMEDIATE 'ALTER TABLE teams ADD (aliases_vc VARCHAR2(32600 CHAR))';
                v_has_aliases_vc := 1;
            END IF;

            SELECT NVL(MAX(DBMS_LOB.GETLENGTH(aliases)), 0)
              INTO v_max_len
              FROM teams;

            IF v_max_len > 32600 THEN
                RAISE_APPLICATION_ERROR(-20002, 'teams.aliases exceeds 32600 chars; manual migration required');
            END IF;

            EXECUTE IMMEDIATE '
                UPDATE teams
                   SET aliases_vc = DBMS_LOB.SUBSTR(aliases, 32600, 1)
                 WHERE aliases IS NOT NULL
                   AND aliases_vc IS NULL
            ';

            IF v_has_aliases_clob = 0 THEN
                EXECUTE IMMEDIATE 'ALTER TABLE teams RENAME COLUMN aliases TO aliases_clob_bak';
            ELSE
                EXECUTE IMMEDIATE 'ALTER TABLE teams DROP COLUMN aliases';
            END IF;

            EXECUTE IMMEDIATE 'ALTER TABLE teams RENAME COLUMN aliases_vc TO aliases';
        END IF;
    ELSIF v_has_aliases = 0 AND v_has_aliases_vc = 1 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE teams RENAME COLUMN aliases_vc TO aliases';
    END IF;
END;
/
