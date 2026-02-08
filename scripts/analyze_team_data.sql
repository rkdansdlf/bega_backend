-- Team Data Analysis Script
-- Purpose: Analyze current state of team-related tables and identify data quality issues

-- ========================================
-- 1. TEAM_FRANCHISES ANALYSIS (Expected: 10 franchises)
-- ========================================
SELECT
    '=== TEAM_FRANCHISES OVERVIEW ===' AS section,
    COUNT(*) AS total_franchises,
    COUNT(CASE WHEN metadata_json IS NOT NULL THEN 1 END) AS with_metadata,
    COUNT(CASE WHEN web_url IS NOT NULL THEN 1 END) AS with_web_url
FROM team_franchises;

-- Show all franchises with their current codes
SELECT
    franchise_id,
    franchise_name,
    current_code,
    original_code,
    founded_year,
    CASE WHEN metadata_json IS NOT NULL THEN 'YES' ELSE 'NO' END AS has_metadata,
    CASE WHEN web_url IS NOT NULL THEN 'YES' ELSE 'NO' END AS has_web_url
FROM team_franchises
ORDER BY franchise_id;

-- ========================================
-- 2. TEAMS TABLE ANALYSIS
-- ========================================

-- Total teams and is_active breakdown
SELECT
    '=== TEAMS TABLE OVERVIEW ===' AS section,
    COUNT(*) AS total_teams,
    COUNT(CASE WHEN is_active = true THEN 1 END) AS active_teams,
    COUNT(CASE WHEN is_active = false THEN 1 END) AS inactive_teams,
    COUNT(CASE WHEN franchise_id IS NULL THEN 1 END) AS without_franchise,
    COUNT(CASE WHEN aliases IS NOT NULL AND array_length(aliases, 1) > 0 THEN 1 END) AS with_aliases
FROM teams;

-- Teams grouped by franchise_id and is_active status
SELECT
    '=== TEAMS BY FRANCHISE ===' AS section,
    COALESCE(tf.franchise_name, 'NO FRANCHISE') AS franchise,
    t.franchise_id,
    COUNT(*) AS team_count,
    COUNT(CASE WHEN t.is_active = true THEN 1 END) AS active_count,
    COUNT(CASE WHEN t.is_active = false THEN 1 END) AS inactive_count,
    string_agg(t.team_id, ', ' ORDER BY t.team_id) AS team_codes
FROM teams t
LEFT JOIN team_franchises tf ON t.franchise_id = tf.franchise_id
GROUP BY tf.franchise_name, t.franchise_id
ORDER BY t.franchise_id NULLS LAST;

-- Show all teams with their status
SELECT
    t.team_id,
    t.team_name,
    t.team_short_name,
    t.franchise_id,
    tf.franchise_name,
    t.is_active,
    CASE
        WHEN t.aliases IS NOT NULL AND array_length(t.aliases, 1) > 0
        THEN array_to_string(t.aliases, ', ')
        ELSE 'EMPTY'
    END AS aliases,
    tf.current_code AS franchise_current_code
FROM teams t
LEFT JOIN team_franchises tf ON t.franchise_id = tf.franchise_id
ORDER BY t.franchise_id NULLS LAST, t.team_id;

-- ========================================
-- 3. IDENTIFY DATA QUALITY ISSUES
-- ========================================

-- Issue 1: Teams that should be active but aren't
SELECT
    '=== ISSUE 1: Current teams not marked as active ===' AS issue,
    t.team_id,
    t.team_name,
    t.is_active AS current_status,
    'Should be TRUE' AS expected_status
FROM teams t
INNER JOIN team_franchises tf ON t.team_id = tf.current_code
WHERE t.is_active = false;

-- Issue 2: Historical teams marked as active
SELECT
    '=== ISSUE 2: Historical teams incorrectly marked as active ===' AS issue,
    t.team_id,
    t.team_name,
    t.franchise_id,
    tf.franchise_name,
    t.is_active AS current_status,
    'Should be FALSE' AS expected_status
FROM teams t
LEFT JOIN team_franchises tf ON t.franchise_id = tf.franchise_id
WHERE t.is_active = true
  AND t.team_id NOT IN (
      SELECT current_code FROM team_franchises WHERE current_code IS NOT NULL
  );

-- Issue 3: Teams without franchise_id
SELECT
    '=== ISSUE 3: Teams without franchise assignment ===' AS issue,
    team_id,
    team_name,
    team_short_name,
    CASE
        WHEN team_id IN ('ALLSTAR1', 'ALLSTAR2', 'EA', 'WE', 'OT') THEN 'Special teams'
        WHEN team_id IN ('AU', 'CA', 'CN', 'CU', 'JP', 'KR', 'NE', 'PH', 'TW', 'US',
                         'VE', 'DO', 'IT', 'NI', 'PA', 'PR', 'CZ', 'BR', 'CH', 'MX', 'ES')
        THEN 'International teams'
        ELSE 'Unknown category'
    END AS team_category
FROM teams
WHERE franchise_id IS NULL
ORDER BY team_id;

-- Issue 4: Empty aliases column
SELECT
    '=== ISSUE 4: Teams that should have aliases ===' AS issue,
    t.team_id,
    t.team_name,
    tf.franchise_name,
    'Empty aliases array' AS current_aliases,
    CASE t.team_id
        WHEN 'OB' THEN 'Should include: 두산, DO'
        WHEN 'HT' THEN 'Should include: KIA, 기아'
        WHEN 'SSG' THEN 'Should include: SK, SL'
        WHEN 'MBC' THEN 'Should include: LG'
        WHEN 'BE' THEN 'Should include: HH, 한화'
        ELSE 'Check historical names'
    END AS suggested_aliases
FROM teams t
LEFT JOIN team_franchises tf ON t.franchise_id = tf.franchise_id
WHERE t.franchise_id IS NOT NULL
  AND (t.aliases IS NULL OR array_length(t.aliases, 1) IS NULL)
  AND t.team_id != tf.current_code
ORDER BY t.franchise_id;

-- ========================================
-- 4. TEAM_HISTORY ANALYSIS
-- ========================================
SELECT
    '=== TEAM_HISTORY OVERVIEW ===' AS section,
    COUNT(*) AS total_records,
    COUNT(DISTINCT season) AS distinct_seasons,
    MIN(season) AS earliest_season,
    MAX(season) AS latest_season,
    COUNT(DISTINCT team_code) AS distinct_team_codes,
    COUNT(CASE WHEN franchise_id IS NOT NULL THEN 1 END) AS with_franchise
FROM team_history;

-- Show team history distribution by franchise
SELECT
    tf.franchise_name,
    th.franchise_id,
    COUNT(*) AS history_records,
    MIN(th.season) AS first_season,
    MAX(th.season) AS last_season,
    COUNT(DISTINCT th.team_code) AS team_codes_used,
    string_agg(DISTINCT th.team_code, ', ' ORDER BY th.team_code) AS codes
FROM team_history th
LEFT JOIN team_franchises tf ON th.franchise_id = tf.franchise_id
GROUP BY tf.franchise_name, th.franchise_id
ORDER BY th.franchise_id NULLS LAST;

-- ========================================
-- 5. CROSS-TABLE VALIDATION
-- ========================================

-- Check if all current_code from franchises exist in teams
SELECT
    '=== VALIDATION: Franchise current_code in teams table ===' AS validation,
    tf.current_code,
    tf.franchise_name,
    CASE WHEN t.team_id IS NOT NULL THEN 'EXISTS' ELSE 'MISSING' END AS in_teams_table
FROM team_franchises tf
LEFT JOIN teams t ON tf.current_code = t.team_id
ORDER BY tf.franchise_id;

-- Check if all team_codes in team_history exist in teams
SELECT
    '=== VALIDATION: Historical team codes in teams table ===' AS validation,
    th.team_code,
    COUNT(DISTINCT th.season) AS seasons_used,
    CASE WHEN t.team_id IS NOT NULL THEN 'EXISTS' ELSE 'MISSING' END AS in_teams_table
FROM team_history th
LEFT JOIN teams t ON th.team_code = t.team_id
GROUP BY th.team_code, t.team_id
HAVING t.team_id IS NULL
ORDER BY th.team_code;

-- ========================================
-- 6. RECOMMENDED FIX QUERIES (Preview)
-- ========================================

-- Show what the is_active fix would look like
SELECT
    '=== RECOMMENDED FIX PREVIEW: is_active updates ===' AS fix_preview,
    t.team_id,
    t.team_name,
    t.is_active AS current_value,
    CASE
        WHEN t.team_id IN (SELECT current_code FROM team_franchises WHERE current_code IS NOT NULL)
        THEN true
        ELSE false
    END AS recommended_value,
    CASE
        WHEN t.team_id IN (SELECT current_code FROM team_franchises WHERE current_code IS NOT NULL)
            AND t.is_active = true THEN 'CORRECT'
        WHEN t.team_id NOT IN (SELECT current_code FROM team_franchises WHERE current_code IS NOT NULL)
            AND t.is_active = false THEN 'CORRECT'
        ELSE 'NEEDS UPDATE'
    END AS status
FROM teams t
ORDER BY
    CASE WHEN t.is_active !=
        CASE WHEN t.team_id IN (SELECT current_code FROM team_franchises) THEN true ELSE false END
    THEN 0 ELSE 1 END,
    t.franchise_id NULLS LAST,
    t.team_id;
