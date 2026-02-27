-- Canonicalize legacy team codes in team_daily_roster for canonical window safety.
-- Canonical set: SS, LT, LG, DB, KIA, KH, HH, SSG, NC, KT
-- Legacy mappings:
-- HT -> KIA
-- DO -> DB
-- OB -> DB
-- KI -> KH
-- NX -> KH
-- WO -> KH
-- KW -> KH
-- SK -> SSG
-- SL -> SSG
-- BE -> HH
-- MBC -> LG
-- LOT -> LT
DECLARE
    v_has_table NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_has_table
    FROM user_tables
    WHERE table_name = 'TEAM_DAILY_ROSTER';

    IF v_has_table > 0 THEN
        EXECUTE IMMEDIATE q'[
            UPDATE team_daily_roster
            SET team_code = CASE team_code
                WHEN 'HT' THEN 'KIA'
                WHEN 'DO' THEN 'DB'
                WHEN 'OB' THEN 'DB'
                WHEN 'KI' THEN 'KH'
                WHEN 'NX' THEN 'KH'
                WHEN 'WO' THEN 'KH'
                WHEN 'KW' THEN 'KH'
                WHEN 'SK' THEN 'SSG'
                WHEN 'SL' THEN 'SSG'
                WHEN 'BE' THEN 'HH'
                WHEN 'MBC' THEN 'LG'
                WHEN 'LOT' THEN 'LT'
                ELSE team_code
            END
            WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL', 'BE', 'MBC', 'LOT')
        ]';
    END IF;
END;
/
