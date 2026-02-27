-- V100: 좌석 시야 UGC 업적 4개 추가
INSERT INTO achievements (code, name_ko, name_en, description_ko, rarity, points_required, created_at)
SELECT * FROM (
    SELECT 'FIRST_SEAT_VIEW' AS code, '나만의 뷰' AS name_ko, 'My View' AS name_en,
           '처음으로 좌석 시야 사진을 공유했습니다!' AS description_ko,
           'COMMON' AS rarity, 0 AS points_required, CURRENT_TIMESTAMP AS created_at FROM DUAL
    UNION ALL
    SELECT 'SEAT_VIEW_5', '시야 수집가', 'View Collector',
           '5회 좌석 시야 사진을 기여했습니다!',
           'RARE', 0, CURRENT_TIMESTAMP FROM DUAL
    UNION ALL
    SELECT 'SEAT_VIEW_10', '구장 전문가', 'Stadium Expert',
           '10회 좌석 시야 사진을 기여했습니다!',
           'EPIC', 0, CURRENT_TIMESTAMP FROM DUAL
    UNION ALL
    SELECT 'SEAT_VIEW_EXPLORER', '직관 투어러', 'Stadium Explorer',
           '3개 이상 구장의 시야 사진을 공유했습니다!',
           'RARE', 0, CURRENT_TIMESTAMP FROM DUAL
) t
WHERE NOT EXISTS (SELECT 1 FROM achievements a WHERE a.code = t.code);
