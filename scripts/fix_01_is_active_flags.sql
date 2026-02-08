-- ========================================
-- Fix 01: Correct is_active Flags
-- ========================================
-- Purpose: Set is_active=true only for current 10 KBO franchise teams
-- Expected: 10 teams active, 34 teams inactive
-- Safe to run: Only updates boolean flags, no deletions

-- Step 1: Show current state
SELECT
    'BEFORE FIX - Current State' AS status,
    COUNT(*) AS total_teams,
    COUNT(CASE WHEN is_active = true THEN 1 END) AS active_teams,
    COUNT(CASE WHEN is_active = false THEN 1 END) AS inactive_teams
FROM teams;

-- Step 2: Reset all teams to inactive
UPDATE teams SET is_active = false;

-- Step 3: Activate only current franchise teams
UPDATE teams
SET is_active = true
WHERE team_id IN (
    SELECT current_code
    FROM team_franchises
    WHERE current_code IS NOT NULL
);

-- Step 4: Verify the fix
SELECT
    'AFTER FIX - Updated State' AS status,
    COUNT(*) AS total_teams,
    COUNT(CASE WHEN is_active = true THEN 1 END) AS active_teams,
    COUNT(CASE WHEN is_active = false THEN 1 END) AS inactive_teams
FROM teams;

-- Step 5: Show which teams are now active
SELECT
    'Active Teams List' AS status,
    t.team_id,
    t.team_name,
    t.team_short_name,
    tf.franchise_name,
    t.is_active
FROM teams t
INNER JOIN team_franchises tf ON t.team_id = tf.current_code
ORDER BY tf.franchise_id;

-- Step 6: Verify all franchises have exactly one active team
SELECT
    'Franchise Active Team Count' AS status,
    tf.franchise_id,
    tf.franchise_name,
    tf.current_code,
    COUNT(t.team_id) AS active_team_count
FROM team_franchises tf
LEFT JOIN teams t ON tf.current_code = t.team_id AND t.is_active = true
GROUP BY tf.franchise_id, tf.franchise_name, tf.current_code
ORDER BY tf.franchise_id;

-- Step 7: Check for any anomalies
SELECT
    'VALIDATION - Should be empty' AS status,
    team_id,
    team_name,
    is_active,
    franchise_id,
    'Active team not in current franchise codes' AS issue
FROM teams
WHERE is_active = true
  AND team_id NOT IN (SELECT current_code FROM team_franchises WHERE current_code IS NOT NULL);

-- Expected Results:
-- - total_teams: 44
-- - active_teams: 10
-- - inactive_teams: 34
-- - All 10 franchises have exactly 1 active team
-- - Validation query returns 0 rows
