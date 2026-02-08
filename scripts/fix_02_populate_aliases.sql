-- ========================================
-- Fix 02: Populate Aliases Column
-- ========================================
-- Purpose: Add historical team code mappings to aliases for flexible lookups
-- Optional: Run only if alias search functionality is needed
-- Safe to run: Only updates aliases column, no structural changes

-- Step 1: Verify current state
SELECT
    'BEFORE - Aliases Status' AS status,
    COUNT(*) AS total_teams,
    COUNT(CASE WHEN aliases IS NOT NULL AND array_length(aliases, 1) > 0 THEN 1 END) AS with_aliases,
    COUNT(CASE WHEN aliases IS NULL OR array_length(aliases, 1) IS NULL THEN 1 END) AS without_aliases
FROM teams;

-- Step 2: Populate aliases for historical teams

-- LG Franchise (franchise_id = 3)
UPDATE teams SET aliases = ARRAY['MBC'] WHERE team_id = 'LG';
UPDATE teams SET aliases = ARRAY['LG'] WHERE team_id = 'MBC';

-- 두산 Franchise (franchise_id = 4)
UPDATE teams SET aliases = ARRAY['두산', 'DO'] WHERE team_id = 'OB';
UPDATE teams SET aliases = ARRAY['OB'] WHERE team_id = 'DO';

-- KIA Franchise (franchise_id = 5)
UPDATE teams SET aliases = ARRAY['해태', 'HT'] WHERE team_id = 'KIA';
UPDATE teams SET aliases = ARRAY['KIA', '기아'] WHERE team_id = 'HT';

-- 한화 Franchise (franchise_id = 7)
UPDATE teams SET aliases = ARRAY['빙그레', 'BE'] WHERE team_id = 'HH';
UPDATE teams SET aliases = ARRAY['한화', 'HH'] WHERE team_id = 'BE';

-- SSG Franchise (franchise_id = 8)
UPDATE teams SET aliases = ARRAY['SK', 'SL', 'SK 와이번스'] WHERE team_id = 'SSG';
UPDATE teams SET aliases = ARRAY['SSG', '랜더스'] WHERE team_id = 'SK';
UPDATE teams SET aliases = ARRAY['SSG', '랜더스'] WHERE team_id = 'SL';

-- 키움 Franchise (franchise_id = 6) - Complex history
UPDATE teams SET aliases = ARRAY['태평양', 'TP'] WHERE team_id = 'CB';
UPDATE teams SET aliases = ARRAY['청보', 'CB'] WHERE team_id = 'HU';
UPDATE teams SET aliases = ARRAY['현대', 'HU'] WHERE team_id = 'KI';
UPDATE teams SET aliases = ARRAY['넥센', 'NX'] WHERE team_id = 'WO';
UPDATE teams SET aliases = ARRAY['서울', 'SM'] WHERE team_id = 'KI' AND team_name LIKE '%서울%';

-- Handle TP if it exists separately
UPDATE teams SET aliases = ARRAY['CB', '청보'] WHERE team_id = 'TP' AND EXISTS (SELECT 1 FROM teams WHERE team_id = 'TP');

-- Step 3: Add Korean names to current teams (optional)
UPDATE teams SET aliases = ARRAY['삼성', '라이온즈'] WHERE team_id = 'SS';
UPDATE teams SET aliases = ARRAY['롯데', '자이언츠'] WHERE team_id = 'LOT';
UPDATE teams SET aliases = ARRAY['LG', '트윈스'] WHERE team_id = 'LG' AND array_length(aliases, 1) IS NULL;
UPDATE teams SET aliases = ARRAY['두산', '베어스'] WHERE team_id = 'OB' AND array_length(aliases, 1) <= 2;
UPDATE teams SET aliases = ARRAY['KIA', '기아', '타이거즈'] WHERE team_id = 'KIA' AND array_length(aliases, 1) <= 2;
UPDATE teams SET aliases = ARRAY['키움', '히어로즈'] WHERE team_id = 'WO' AND array_length(aliases, 1) <= 1;
UPDATE teams SET aliases = ARRAY['한화', '이글스'] WHERE team_id = 'HH' AND array_length(aliases, 1) IS NULL;
UPDATE teams SET aliases = ARRAY['SSG', '랜더스'] WHERE team_id = 'SSG' AND array_length(aliases, 1) <= 3;
UPDATE teams SET aliases = ARRAY['NC', '다이노스'] WHERE team_id = 'NC';
UPDATE teams SET aliases = ARRAY['KT', '위즈'] WHERE team_id = 'KT';

-- Step 4: Verify updates
SELECT
    'AFTER - Aliases Status' AS status,
    COUNT(*) AS total_teams,
    COUNT(CASE WHEN aliases IS NOT NULL AND array_length(aliases, 1) > 0 THEN 1 END) AS with_aliases,
    COUNT(CASE WHEN aliases IS NULL OR array_length(aliases, 1) IS NULL THEN 1 END) AS without_aliases
FROM teams;

-- Step 5: Show teams with their aliases
SELECT
    t.team_id,
    t.team_name,
    tf.franchise_name,
    t.is_active,
    CASE
        WHEN t.aliases IS NOT NULL AND array_length(t.aliases, 1) > 0
        THEN array_to_string(t.aliases, ', ')
        ELSE 'NO ALIASES'
    END AS aliases
FROM teams t
LEFT JOIN team_franchises tf ON t.franchise_id = tf.franchise_id
WHERE t.franchise_id IS NOT NULL
ORDER BY tf.franchise_id, t.is_active DESC, t.team_id;

-- Step 6: Test alias lookup examples
SELECT
    'TEST - Alias Lookup Examples' AS test_type,
    search_term,
    t.team_id,
    t.team_name,
    t.is_active
FROM (
    VALUES
        ('두산'),
        ('해태'),
        ('SK'),
        ('MBC'),
        ('넥센')
) AS search(search_term)
LEFT JOIN teams t ON search_term = ANY(t.aliases) OR search_term = t.team_id;

-- Expected Results:
-- - Most teams should have aliases populated
-- - Historical teams have mappings to current teams
-- - Alias lookups return correct teams
