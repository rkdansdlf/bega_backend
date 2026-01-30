-- V14: Consolidate player movement data and remove dummy entries
-- This script handles the transition from 'player_movement' (singular) to 'player_movements' (plural)
-- and cleans up the dummy seed data from V13.

-- 1. Clear dummy data from plural table (Standard SQL)
DELETE FROM player_movements 
WHERE player_name IN ('Heo Gyeong-min', 'Sim Woo-jun', 'Jang Hyun-sik', 'Choi Won-tae')
  AND section = 'FA Contract'
  AND (remarks LIKE '4Y %' OR remarks LIKE '3Y %');

-- 2. Migrate data from singular to plural if it exists (Oracle PL/SQL)
DECLARE
    v_count NUMBER;
BEGIN
    -- Check if 'PLAYER_MOVEMENT' exists in the current schema
    SELECT COUNT(*) INTO v_count 
    FROM user_tables 
    WHERE table_name = 'PLAYER_MOVEMENT';

    IF v_count > 0 THEN
        -- Use EXECUTE IMMEDIATE to prevent compilation errors if table doesn't exist
        EXECUTE IMMEDIATE '
            INSERT INTO player_movements (player_name, team_code, section, movement_date, remarks, created_at, updated_at)
            SELECT player_name, team_code, section, movement_date, remarks, created_at, updated_at
            FROM player_movement
            WHERE NOT EXISTS (
                SELECT 1 FROM player_movements pm 
                WHERE pm.player_name = player_movement.player_name 
                AND pm.movement_date = player_movement.movement_date
            )
        ';
    END IF;
END;
/
