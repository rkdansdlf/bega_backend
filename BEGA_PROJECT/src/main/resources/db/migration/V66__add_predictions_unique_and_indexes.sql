-- V66: Ensure predictions uniqueness and optimize prediction/game range lookups (Oracle)

-- 1) Remove duplicate votes for same (user_id, game_id) pair by keeping latest row
DELETE FROM predictions p
WHERE p.id IN (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY user_id, game_id ORDER BY id DESC) AS rn
        FROM predictions
        WHERE user_id IS NOT NULL
    )
    WHERE rn > 1
);

-- 2) Add unique constraint for vote deduplication
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_constraints
    WHERE table_name = 'PREDICTIONS'
      AND constraint_name = 'UK_PREDICTIONS_USER_GAME'
      AND constraint_type = 'U';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE predictions ADD CONSTRAINT UK_PREDICTIONS_USER_GAME UNIQUE (user_id, game_id)';
    END IF;
END;
/

-- 3) Add supporting indexes for frequent lookup patterns
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_indexes
    WHERE table_name = 'PREDICTIONS'
      AND index_name = 'IDX_PREDICTIONS_USER_ID';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_PREDICTIONS_USER_ID ON predictions(user_id)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_indexes
    WHERE table_name = 'PREDICTIONS'
      AND index_name = 'IDX_PREDICTIONS_GAME_ID';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_PREDICTIONS_GAME_ID ON predictions(game_id)';
    END IF;
END;
/

-- 4) Optimize game date-range reads used by /matches/range
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_indexes
    WHERE table_name = 'GAME'
      AND index_name = 'IDX_GAME_DATE_GAME_ID';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_GAME_DATE_GAME_ID ON game(game_date, game_id)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_indexes
    WHERE table_name = 'GAME'
      AND index_name = 'IDX_GAME_DATE_HOME_AWAY_DUMMY';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_GAME_DATE_HOME_AWAY_DUMMY ON game(game_date, home_team, away_team, is_dummy)';
    END IF;
END;
/
