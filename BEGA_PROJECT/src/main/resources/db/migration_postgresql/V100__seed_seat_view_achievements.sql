-- V100: 좌석 시야 UGC 업적 4개 추가
INSERT INTO achievements (code, name_ko, name_en, description_ko, rarity, points_required, created_at)
VALUES
    ('FIRST_SEAT_VIEW', '나만의 뷰', 'My View', '처음으로 좌석 시야 사진을 공유했습니다!', 'COMMON', 0, NOW()),
    ('SEAT_VIEW_5', '시야 수집가', 'View Collector', '5회 좌석 시야 사진을 기여했습니다!', 'RARE', 0, NOW()),
    ('SEAT_VIEW_10', '구장 전문가', 'Stadium Expert', '10회 좌석 시야 사진을 기여했습니다!', 'EPIC', 0, NOW()),
    ('SEAT_VIEW_EXPLORER', '직관 투어러', 'Stadium Explorer', '3개 이상 구장의 시야 사진을 공유했습니다!', 'RARE', 0, NOW())
ON CONFLICT (code) DO NOTHING;
