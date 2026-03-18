# Stadium Delivery Pickup Rollout

## Scope
- Delivery pickup zone rollout and validation for stadium guide.
- Repository migration artifact (current):
  - `db/migration_postgresql/V104__refine_delivery_pickup_zone_coordinates.sql`
- Runtime verification target (already applied in target DB):
  - Flyway versions `102`, `103`, `104` all `success=true`

## Current Repo/Runtime State (2026-02-28)
- Source tree currently tracks `V104` for coordinate refinement.
- Runtime DB verification shows `102/103/104` applied and delivery category data present.
- Validation scripts available:
  - `scripts/delivery_pickup_smoke.sh`
  - `scripts/verify_delivery_pickup_seed.sh`

## Pre-Deploy
1. Confirm target backend image/tag includes current `V104` migration file.
2. Ensure production DB backup/snapshot policy is satisfied.
3. Verify target DB Flyway/data baseline:
   ```bash
   cd /Users/mac/project/KBO_platform/bega_backend
   BASEBALL_DB_URL="jdbc:postgresql://<db-host>:5432/<db-name>" \
   BASEBALL_DB_USERNAME="<db-user>" \
   BASEBALL_DB_PASSWORD="<db-pass>" \
   ./scripts/verify_delivery_pickup_seed.sh
   ```

## Deploy
1. Deploy backend as usual.
2. Confirm startup log includes migration/application readiness for stadium guide endpoints.

## Post-Deploy Smoke
1. API smoke only:
   ```bash
   cd /Users/mac/project/KBO_platform/bega_backend
   BACKEND_BASE_URL="https://<prod-backend-host>" \
   ./scripts/delivery_pickup_smoke.sh
   ```

2. API + DB smoke:
   ```bash
   cd /Users/mac/project/KBO_platform/bega_backend
   BACKEND_BASE_URL="https://<prod-backend-host>" \
   BASEBALL_DB_URL="jdbc:postgresql://<db-host>:5432/<db-name>" \
   BASEBALL_DB_USERNAME="<db-user>" \
   BASEBALL_DB_PASSWORD="<db-pass>" \
   ./scripts/delivery_pickup_smoke.sh
   ```

3. DB-only verification:
   ```bash
   cd /Users/mac/project/KBO_platform/bega_backend
   # .env.prod or .env already has BASEBALL_DB_* configured, or pass them inline.
   ./scripts/verify_delivery_pickup_seed.sh
   ```

## Expected Results
- `api/stadiums` returns `9+` stadiums.
- `delivery` coverage: each stadium has at least `1` entry.
- Expected total counts:
  - `delivery|17`
  - `food|24`
- Flyway history includes successful records for:
  - `102|...|true`
  - `103|...|true`
  - `104|...|true`

## Rollback
- Data-only rollback (delivery seed rows):
  ```sql
  DELETE FROM public.places
  WHERE category = 'delivery'
    AND name IN (
      '종합운동장역 6번 출구 픽업존',
      '야구공 조형물 앞 픽업존',
      '1루 매표소 앞 횡단보도 픽업존',
      '동양미래대 정문 앞 픽업존',
      '노브랜드버거 인근 요기요 픽업존',
      '도드람게이트 인근 요기요 픽업존',
      '주출입구(게이트) 밖 픽업존',
      '홈플러스 인근 픽업존',
      '3루 배달존 픽업',
      '1루 배달존(인크커피 인근) 픽업',
      '대공원역 5번 출구 픽업존',
      '출입 게이트 앞 픽업존',
      '야구장 광장 외부 픽업존',
      '입장 게이트 앞 픽업존',
      '외부 횡단보도 부근 픽업존',
      '야구장 앞 광장 외부 픽업존',
      '출입 게이트 밖 픽업존'
    );
  ```
- If rollback is required, apply via controlled DBA process and re-run smoke checks.
