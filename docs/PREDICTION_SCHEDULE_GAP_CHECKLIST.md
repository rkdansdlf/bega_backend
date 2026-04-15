# Prediction Schedule Gap Checklist

## 목적
- 홈/Prediction 일정이 비거나 시즌 단계가 어긋날 때, 자동 크롤링 없이 운영자 요청형 절차로 전환하기 위한 체크리스트.

## 1) JSON 409 / SSE meta 확인
- 다음 엔드포인트에서 `MANUAL_BASEBALL_DATA_REQUIRED`가 내려오는지 확인한다.
- `GET /api/kbo/schedule?date=2026-04-05`
- `GET /api/home/bootstrap?date=2026-04-05`
- `GET /api/matches/day?date=2026-04-05`
- `GET /api/matches/20260405HHOB0`
- `GET /api/games/past`
- `POST /ai/coach/analyze`

응답에서 확인할 필드:
- `code = MANUAL_BASEBALL_DATA_REQUIRED`
- `data.scope`
- `data.missingItems[]`
- `data.operatorMessage`
- `data.blocking = true`

코치 SSE meta에서 확인할 필드:
- `validation_status = manual_data_required`
- `manual_data_request`
- `data_quality = insufficient`

## 2) 운영자 요청 메시지 기록
- API 또는 SSE에서 반환한 `operatorMessage`를 그대로 운영 채널에 전달한다.
- 최소 요청 항목은 다음을 포함해야 한다.
- 경기 날짜
- 경기 ID
- 경기 상태
- 최종 점수
- 선발 정보
- 라인업
- 시즌/리그 구분

## 3) 수동 데이터 정리 런북 실행
- 2026 시즌 `season_id` 오염은 자동 보정하지 않는다.
- 수동 SQL과 검증 쿼리는 다음 문서를 사용한다.
- `/Users/mac/project/KBO_platform/task/operations/manual-baseball-data-remediation-2026.md`

## 4) 내부 동기화만 재실행
- 외부 웹/KBO 조회는 사용하지 않는다.
- 필요한 경우 내부 Oracle -> PostgreSQL 동기화만 재실행한다.

```bash
cd /Users/mac/project/KBO_platform
./.venv/bin/python scripts/sync_kbo_data.py
```

## 5) 검증 기준
- 문제 엔드포인트가 더 이상 stage mismatch를 반환하지 않는다.
- 완료 경기라면 점수와 상태가 함께 채워진다.
- 코치 분석은 근거 부족 fallback 문구 대신 정상 분석 또는 `manual_data_required` meta만 반환한다.
- 홈 일정과 Prediction 일정이 동일한 `gameId` 세트를 사용한다.
