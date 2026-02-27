-- V104: Refine delivery pickup zone coordinates for stadium guide.
-- Source baseline: ArcGIS geocoding candidates + manual entrance-side offset tuning.

UPDATE public.places
SET lat = 37.510930,
    lng = 127.072710,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '종합운동장역 6번 출구 픽업존';

UPDATE public.places
SET lat = 37.498900,
    lng = 126.867150,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '야구공 조형물 앞 픽업존';

UPDATE public.places
SET lat = 37.498250,
    lng = 126.866450,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '1루 매표소 앞 횡단보도 픽업존';

UPDATE public.places
SET lat = 37.500720,
    lng = 126.867860,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '동양미래대 정문 앞 픽업존';

UPDATE public.places
SET lat = 37.436565,
    lng = 126.686457,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '노브랜드버거 인근 요기요 픽업존';

UPDATE public.places
SET lat = 37.435900,
    lng = 126.687200,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '도드람게이트 인근 요기요 픽업존';

UPDATE public.places
SET lat = 37.299928,
    lng = 127.009693,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '주출입구(게이트) 밖 픽업존';

UPDATE public.places
SET lat = 37.304051,
    lng = 127.015080,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '홈플러스 인근 픽업존';

UPDATE public.places
SET lat = 35.169900,
    lng = 126.888100,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '3루 배달존 픽업';

UPDATE public.places
SET lat = 35.169050,
    lng = 126.889300,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '1루 배달존(인크커피 인근) 픽업';

UPDATE public.places
SET lat = 35.843800,
    lng = 128.677260,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '대공원역 5번 출구 픽업존';

UPDATE public.places
SET lat = 35.222507,
    lng = 128.582317,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '출입 게이트 앞 픽업존';

UPDATE public.places
SET lat = 35.223100,
    lng = 128.583050,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '야구장 광장 외부 픽업존';

UPDATE public.places
SET lat = 36.316158,
    lng = 127.431543,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '입장 게이트 앞 픽업존';

UPDATE public.places
SET lat = 36.316700,
    lng = 127.432200,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '외부 횡단보도 부근 픽업존';

UPDATE public.places
SET lat = 35.194171,
    lng = 129.061481,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '야구장 앞 광장 외부 픽업존';

UPDATE public.places
SET lat = 35.194700,
    lng = 129.062100,
    updated_at = NOW()
WHERE category = 'delivery'
  AND name = '출입 게이트 밖 픽업존';
