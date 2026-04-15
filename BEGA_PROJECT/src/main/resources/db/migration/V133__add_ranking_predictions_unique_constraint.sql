-- V133: Enforce one ranking prediction per user and season (Oracle)

DECLARE
    v_table_count NUMBER;
    v_duplicate_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_table_count
    FROM user_tables
    WHERE table_name = 'RANKING_PREDICTIONS';

    IF v_table_count = 0 THEN
        RETURN;
    END IF;

    SELECT COUNT(*)
    INTO v_duplicate_count
    FROM (
        SELECT user_id, season_year
        FROM ranking_predictions
        WHERE user_id IS NOT NULL
        GROUP BY user_id, season_year
        HAVING COUNT(*) > 1
    );

    IF v_duplicate_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20002, 'Cannot apply V133: duplicate ranking_predictions rows already exist.');
    END IF;
END;
/

DECLARE
    v_constraint_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_constraint_count
    FROM user_constraints
    WHERE table_name = 'RANKING_PREDICTIONS'
      AND constraint_name = 'UK_RANK_PRED_USER_SEASON'
      AND constraint_type = 'U';

    IF v_constraint_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ranking_predictions ADD CONSTRAINT UK_RANK_PRED_USER_SEASON UNIQUE (user_id, season_year)';
    END IF;
END;
/
