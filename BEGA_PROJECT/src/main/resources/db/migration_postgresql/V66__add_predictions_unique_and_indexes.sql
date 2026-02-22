-- V66: Improve /matches/range and vote aggregation lookup performance (PostgreSQL)

-- 1) Remove duplicate votes for same (user_id, game_id) pair by keeping latest row
WITH duplicate_rows AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY user_id, game_id
            ORDER BY id DESC
        ) AS rn
    FROM predictions
    WHERE user_id IS NOT NULL
)
DELETE FROM predictions p
USING duplicate_rows d
WHERE p.id = d.id
  AND d.rn > 1;

-- 2) Add unique constraint for vote deduplication
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class r ON c.conrelid = r.oid
        WHERE r.relname = 'predictions'
          AND c.conname = 'uk_predictions_user_game'
    ) THEN
        ALTER TABLE predictions
            ADD CONSTRAINT uk_predictions_user_game UNIQUE (user_id, game_id);
    END IF;
END
$$;

-- 3) Add supporting indexes for lookup paths
CREATE INDEX IF NOT EXISTS idx_predictions_user_game
    ON predictions (user_id, game_id);

CREATE INDEX IF NOT EXISTS idx_predictions_user_id
    ON predictions (user_id);

CREATE INDEX IF NOT EXISTS idx_predictions_game_voted
    ON predictions (game_id, voted_team);

CREATE INDEX IF NOT EXISTS idx_game_range_filter
    ON game (game_date, is_dummy, game_status, home_team, away_team, game_id);

CREATE INDEX IF NOT EXISTS idx_game_status_dummy_date
    ON game (game_status, is_dummy, game_date);
