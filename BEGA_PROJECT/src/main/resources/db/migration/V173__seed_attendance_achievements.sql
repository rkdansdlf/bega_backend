-- V173: 직관 출석 업적 4개 추가
INSERT INTO achievements (code, name_ko, name_en, description_ko, rarity, points_required, created_at)
SELECT * FROM (
    SELECT 'FIRST_ATTENDANCE' AS code, '첫 직관' AS name_ko, 'First Game' AS name_en,
           '처음으로 직관 기록을 남겼습니다!' AS description_ko,
           'COMMON' AS rarity, 0 AS points_required, CURRENT_TIMESTAMP AS created_at FROM DUAL
    UNION ALL
    SELECT 'ATTENDANCE_10', '직관 단골', 'Regular', '직관 10회를 달성했습니다!',
           'RARE', 0, CURRENT_TIMESTAMP FROM DUAL
    UNION ALL
    SELECT 'ATTENDANCE_30', '직관 마니아', 'Enthusiast', '직관 30회를 달성했습니다!',
           'EPIC', 0, CURRENT_TIMESTAMP FROM DUAL
    UNION ALL
    SELECT 'ATTENDANCE_50', '직관 레전드', 'Legend', '직관 50회를 달성했습니다!',
           'LEGENDARY', 0, CURRENT_TIMESTAMP FROM DUAL
) t
WHERE NOT EXISTS (SELECT 1 FROM achievements a WHERE a.code = t.code);
