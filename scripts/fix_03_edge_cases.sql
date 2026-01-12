-- ========================================
-- Fix 03: Handle Edge Cases
-- ========================================
-- Purpose: Resolve LOT/LT discrepancy, TP handling, international teams
-- Run after: fix_01_is_active_flags.sql

-- ========================================
-- Part A: Investigate LOT vs LT Issue
-- ========================================

-- Check current state
SELECT
    'LOT/LT Investigation' AS check_type,
    team_id,
    team_name,
    team_short_name,
    franchise_id,
    is_active
FROM teams
WHERE franchise_id = 2  -- 롯데 franchise
   OR team_id IN ('LOT', 'LT');

-- Check what franchise expects
SELECT
    'Franchise Expectation' AS check_type,
    current_code,
    franchise_name
FROM team_franchises
WHERE franchise_id = 2;

-- Check for foreign key references (before any update)
SELECT
    'FK References - Users' AS check_type,
    favorite_team AS team_code,
    COUNT(*) AS user_count
FROM users
WHERE favorite_team IN ('LOT', 'LT')
GROUP BY favorite_team;

-- If LT exists and needs to be changed to LOT:
-- UNCOMMENT ONLY AFTER VERIFYING ABOVE QUERIES

/*
-- Step 1: Update foreign key references
UPDATE users SET favorite_team = 'LOT' WHERE favorite_team = 'LT';
UPDATE predictions SET team_code = 'LOT' WHERE team_code = 'LT';
UPDATE cheer_posts SET team_code = 'LOT' WHERE team_code = 'LT';
-- Add other tables as needed

-- Step 2: Update or remove the LT team record
UPDATE teams SET team_id = 'LOT' WHERE team_id = 'LT';
-- OR if LOT already exists:
DELETE FROM teams WHERE team_id = 'LT';
*/

-- ========================================
-- Part B: TP (태평양) Handling
-- ========================================

-- Check if TP exists
SELECT
    'TP Investigation' AS check_type,
    team_id,
    team_name,
    team_short_name,
    franchise_id,
    is_active,
    founded_year
FROM teams
WHERE team_id = 'TP';

-- Check team_history for TP
SELECT
    'TP History Records' AS check_type,
    season,
    team_name,
    team_code,
    franchise_id
FROM team_history
WHERE team_code = 'TP'
ORDER BY season;

-- Decision: Keep TP as historical team
-- It's a valid team (태평양 돌고래, 1996-1999)
-- Just ensure it's inactive
UPDATE teams
SET is_active = false
WHERE team_id = 'TP';

-- ========================================
-- Part C: International Teams
-- ========================================

-- Ensure all international teams are inactive
UPDATE teams
SET is_active = false
WHERE franchise_id IS NULL;

-- Verify international teams
SELECT
    'International Teams' AS check_type,
    team_id,
    team_name,
    team_short_name,
    is_active,
    CASE
        WHEN team_id IN ('ALLSTAR1', 'ALLSTAR2', 'EA', 'WE', 'OT')
        THEN 'Special Event Teams'
        WHEN team_id IN ('AU', 'CA', 'CN', 'CU', 'JP', 'KR', 'NE', 'PH', 'TW', 'US',
                         'VE', 'DO', 'IT', 'NI', 'PA', 'PR', 'CZ', 'BR', 'CH', 'MX', 'ES')
        THEN 'National Teams'
        ELSE 'Unknown Category'
    END AS category
FROM teams
WHERE franchise_id IS NULL
ORDER BY
    CASE
        WHEN team_id IN ('ALLSTAR1', 'ALLSTAR2', 'EA', 'WE', 'OT') THEN 1
        ELSE 2
    END,
    team_id;

-- ========================================
-- Part D: Verify All Edge Cases Fixed
-- ========================================

-- Final validation check
SELECT
    'Final Validation' AS check_type,
    COUNT(*) AS total_teams,
    COUNT(CASE WHEN is_active = true THEN 1 END) AS active_teams,
    COUNT(CASE WHEN is_active = false THEN 1 END) AS inactive_teams,
    COUNT(CASE WHEN franchise_id IS NULL THEN 1 END) AS international_teams,
    COUNT(CASE WHEN franchise_id IS NOT NULL AND is_active = true THEN 1 END) AS active_kbo_teams
FROM teams;

-- Verify no orphaned foreign keys
SELECT
    'Orphaned FK Check - Should be Empty' AS check_type,
    table_name,
    column_name,
    value AS orphaned_team_code
FROM (
    SELECT 'users' AS table_name, 'favorite_team' AS column_name, favorite_team AS value
    FROM users
    WHERE favorite_team IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM teams WHERE team_id = users.favorite_team)

    UNION ALL

    SELECT 'cheer_posts' AS table_name, 'team_code' AS column_name, team_code AS value
    FROM cheer_posts
    WHERE team_code IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM teams WHERE team_id = cheer_posts.team_code)
    LIMIT 100
) orphans;

-- Expected Results:
-- - LOT team exists with franchise_id = 2
-- - TP exists with is_active = false (historical)
-- - All international teams have is_active = false
-- - No orphaned foreign keys
-- - total_teams: 44, active_teams: 10, inactive_teams: 34
