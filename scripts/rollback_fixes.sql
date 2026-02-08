-- ========================================
-- ROLLBACK Script
-- ========================================
-- Purpose: Revert all team data optimizations if issues occur
-- WARNING: Only run if you need to undo the fixes

-- ========================================
-- Rollback Fix 02: Clear Aliases
-- ========================================

SELECT 'Rolling back: Clearing all aliases' AS action;

UPDATE teams SET aliases = NULL;

-- Verify
SELECT
    'After Rollback - Aliases' AS status,
    COUNT(*) AS total_teams,
    COUNT(CASE WHEN aliases IS NOT NULL AND array_length(aliases, 1) > 0 THEN 1 END) AS with_aliases
FROM teams;

-- ========================================
-- Rollback Fix 01: Reset is_active to All True
-- ========================================

SELECT 'Rolling back: Setting all teams to active' AS action;

UPDATE teams SET is_active = true;

-- Verify
SELECT
    'After Rollback - is_active' AS status,
    COUNT(*) AS total_teams,
    COUNT(CASE WHEN is_active = true THEN 1 END) AS active_teams,
    COUNT(CASE WHEN is_active = false THEN 1 END) AS inactive_teams
FROM teams;

-- ========================================
-- Rollback Fix 03: Edge Cases
-- ========================================

-- If you changed LT to LOT, you'll need to restore from backup
-- This requires manual intervention with your backup data

SELECT 'WARNING: Edge case rollbacks (LOT/LT changes) require backup restoration' AS warning;

-- ========================================
-- Final Verification
-- ========================================

SELECT
    'Rollback Complete - Current State' AS status,
    COUNT(*) AS total_teams,
    COUNT(CASE WHEN is_active = true THEN 1 END) AS active_teams,
    COUNT(CASE WHEN is_active = false THEN 1 END) AS inactive_teams,
    COUNT(CASE WHEN aliases IS NOT NULL AND array_length(aliases, 1) > 0 THEN 1 END) AS teams_with_aliases
FROM teams;

-- Expected after rollback:
-- - All teams have is_active = true (original state)
-- - All aliases are NULL (original state)
-- - LOT/LT changes would need backup restoration
