-- V179: 직관 출석 업적 4개 추가
INSERT INTO achievements (code, name_ko, name_en, description_ko, rarity, points_required, created_at)
VALUES
    ('FIRST_ATTENDANCE', '첫 직관', 'First Game', '처음으로 직관 기록을 남겼습니다!', 'COMMON', 0, NOW()),
    ('ATTENDANCE_10', '직관 단골', 'Regular', '직관 10회를 달성했습니다!', 'RARE', 0, NOW()),
    ('ATTENDANCE_30', '직관 마니아', 'Enthusiast', '직관 30회를 달성했습니다!', 'EPIC', 0, NOW()),
    ('ATTENDANCE_50', '직관 레전드', 'Legend', '직관 50회를 달성했습니다!', 'LEGENDARY', 0, NOW())
ON CONFLICT (code) DO NOTHING;
