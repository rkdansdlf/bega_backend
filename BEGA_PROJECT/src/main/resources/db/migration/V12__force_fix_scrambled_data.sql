-- V12: Force fix of scrambled team_franchises and teams data
-- This is necessary because V10/V11 were marked as applied but the data is still incorrect.

-- 1. Fix team_franchises current_code and original_code to match their NAME/ID
-- Based on confirmed IDs: 1:한화, 2:KIA, 3:KT, 4:LG, 5:롯데, 6:NC, 7:두산, 8:삼성, 9:SSG, 10:키움
UPDATE team_franchises SET current_code = 'HH',  original_code = 'HH' WHERE id = 1;
UPDATE team_franchises SET current_code = 'KIA', original_code = 'HT' WHERE id = 2;
UPDATE team_franchises SET current_code = 'KT',  original_code = 'KT' WHERE id = 3;
UPDATE team_franchises SET current_code = 'LG',  original_code = 'LG' WHERE id = 4;
UPDATE team_franchises SET current_code = 'LT',  original_code = 'LT' WHERE id = 5;
UPDATE team_franchises SET current_code = 'NC',  original_code = 'NC' WHERE id = 6;
UPDATE team_franchises SET current_code = 'OB',  original_code = 'OB' WHERE id = 7;
UPDATE team_franchises SET current_code = 'SS',  original_code = 'SS' WHERE id = 8;
UPDATE team_franchises SET current_code = 'SSG', original_code = 'SK' WHERE id = 9;
UPDATE team_franchises SET current_code = 'WO',  original_code = 'WO' WHERE id = 10;

-- 2. Correct franchise_id in teams table for all teams to match the above IDs
UPDATE teams SET franchise_id = 1  WHERE team_id IN ('HH', 'BE');               -- 한화 (빙그레)
UPDATE teams SET franchise_id = 2  WHERE team_id IN ('HT', 'KIA');             -- KIA (해태)
UPDATE teams SET franchise_id = 3  WHERE team_id IN ('KT');                     -- KT
UPDATE teams SET franchise_id = 4  WHERE team_id IN ('LG', 'MBC');              -- LG (MBC)
UPDATE teams SET franchise_id = 5  WHERE team_id IN ('LT');                     -- 롯데
UPDATE teams SET franchise_id = 6  WHERE team_id IN ('NC');                     -- NC
UPDATE teams SET franchise_id = 7  WHERE team_id IN ('OB', 'DO');               -- 두산 (OB)
UPDATE teams SET franchise_id = 8  WHERE team_id IN ('SS');                     -- 삼성
UPDATE teams SET franchise_id = 9  WHERE team_id IN ('SK', 'SSG', 'SL');         -- SSG (SK, 쌍방울)
UPDATE teams SET franchise_id = 10 WHERE team_id IN ('WO', 'KI', 'NX');         -- 키움 (우리, 히어로즈, 넥센)

-- 3. Set franchise_id to NULL for defunct teams not directly inherited by active 10
UPDATE teams SET franchise_id = NULL WHERE team_id IN ('CB', 'HU', 'SM', 'TD', 'TP');
