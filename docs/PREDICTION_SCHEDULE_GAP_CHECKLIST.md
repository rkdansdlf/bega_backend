# Prediction Schedule Gap Checklist

## 목적
- 2026 시즌 예정경기 데이터 단절을 빠르게 점검하고 복구하기 위한 운영 체크리스트.

## 1) 데이터 업서트 실행
```bash
cd /Users/mac/project/KBO_platform/bega_backend
BASEBALL_DB_URL='postgresql://<user>:<pass>@<host>:5432/<db>' \
  ./scripts/run_2026_schedule_upsert.sh
```

## 2) API 스모크 체크
```bash
cd /Users/mac/project/KBO_platform/bega_backend
./scripts/prediction_schedule_gap_smoke.sh http://localhost:8080
```

## 3) 수동 점검 엔드포인트
- `GET /api/kbo/schedule?date=2026-03-22`
- `GET /api/kbo/schedule?date=2026-03-23`
- `GET /api/kbo/schedule?date=2026-03-28`
- `GET /api/matches/range?startDate=2026-03-22&endDate=2026-03-31&withMeta=true`
- `GET /api/matches/bounds`

## 4) 통과 기준
- 각 `schedule` 응답 배열 길이 `> 0`
- `matches/range.content` 길이 `> 0`
- `matches/bounds.hasData=true`
- `matches/bounds.latestGameDate >= 2026-03-22`
