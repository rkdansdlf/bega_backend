# Cheer Observability Alerts

## 목적
- Cheer 도메인 신규 메트릭 기반으로 장애/성능 저하를 조기 감지한다.
- 알림 발생 시 1차 점검 포인트를 표준화한다.

## 대상 메트릭
- `cheer_battle_vote_total{result=*}`
  - `success`, `already_voted`, `insufficient_points`, `user_not_found`, `unknown`
- `cheer_websocket_events_total{event=*}`
  - `connect`, `disconnect`, `battle_subscribe`
- `image_optimization_total{source=*,result=*}`
  - `optimized`, `fallback`, `skipped`

## 적용된 알림 룰
- `CheerBattleVoteErrorSpike` (warning)
  - 조건: 최근 5분 `user_not_found|unknown` 3건 이상
- `CheerWebSocketDisconnectSurge` (warning)
  - 조건: 최근 5분 `disconnect > connect * 1.2`, `connect >= 20`
- `CheerWebSocketSubscriptionDrop` (warning)
  - 조건: 최근 10분 `battle_subscribe < connect * 0.4`, `connect >= 25`
- `CheerWebSocketDisconnectCritical` (critical)
  - 조건: 최근 5분 `disconnect >= 30` 이고 `disconnect > connect * 2`
- `CheerWebSocketSubscribeStall` (warning)
  - 조건: 최근 10분 `connect >= 15` 이고 `battle_subscribe == 0`
- `ImageOptimizationFallbackRateHigh` (warning)
  - 조건: 최근 10분 fallback 비율 60% 초과, 총 처리량 20건 이상
- `ImageOptimizationNoOptimizedOutput` (warning)
  - 조건: 최근 30분 처리량 30건 이상인데 optimized 0건

## 빠른 점검 커맨드
```bash
curl -fsS "http://localhost:8080/actuator/metrics/cheer_battle_vote_total" | jq '{name, measurements, availableTags}'
curl -fsS "http://localhost:8080/actuator/metrics/cheer_websocket_events_total" | jq '{name, measurements, availableTags}'
curl -fsS "http://localhost:8080/actuator/metrics/image_optimization_total" | jq '{name, measurements, availableTags}'
```

## Grafana 대시보드
- 프로비저닝 경로:
  - `monitoring/grafana/provisioning/datasources/prometheus.yml`
  - `monitoring/grafana/provisioning/dashboards/dashboards.yml`
  - `monitoring/grafana/dashboards/cheer-observability.json`
- 대시보드 이름: `Cheer Observability`
- 주요 패널:
  - `WebSocket Disconnect Ratio (5m)`
  - `WebSocket Event Rate (1m)`
  - `Battle Subscribe Conversion (10m)`
  - `Image Optimization Fallback Ratio (10m)`
  - `Cheer Battle Vote Non-success (5m)`

## 알림 발생 시 1차 대응
1. `CheerBattleVoteErrorSpike`
   - 인증 쿠키/JWT 만료 및 사용자 조회 경로 점검
   - 최근 배포에서 사용자 식별자 매핑 변경 여부 확인
2. `CheerWebSocketDisconnectSurge`, `CheerWebSocketDisconnectCritical`, `CheerWebSocketSubscriptionDrop`, `CheerWebSocketSubscribeStall`
   - STOMP endpoint, reverse proxy(websocket upgrade), frontend 재연결 로그 확인
   - 브로커 자원 사용량 및 네트워크 단절 이벤트 확인
   - `subscribe == 0`이면 프론트 구독 요청(`/topic/battle/{gameId}`) 및 권한/세션 만료 여부 우선 점검
3. `ImageOptimizationFallbackRateHigh`, `ImageOptimizationNoOptimizedOutput`
   - WebP 인코더 로드 여부(`ImageUtil` 초기화 로그) 확인
   - 이미지 원본 포맷/용량 분포 변동 여부 확인

## 운영 메모 (2026-02-27)
- Flyway 정리
  - `V103__normalize_cheer_post_reports_content_to_clob.sql`:
    - Oracle 운영 환경에서 `CHEER_POST_REPORTS.CONTENT` 타입을 `CLOB` 기준으로 정규화한다.
  - `V105__normalize_cheer_post_reports_content_to_varchar.sql`:
    - PostgreSQL 개발 환경에서 동일 컬럼을 `varchar` 기준으로 정규화한다.
  - 의도:
    - 개발(PostgreSQL)과 운영(Oracle)의 타입 차이를 명시적으로 분리해 `ddl-auto=validate` 충돌을 방지한다.

- Prometheus 스크랩 정책
  - `kbo-backend` 잡은 단일 타깃(`backend:8080`)만 사용한다.
  - `backend:8080`와 `host.docker.internal:8080`를 동시에 등록하면 동일 앱이 중복 수집되어
    `up{job="kbo-backend"}` 및 각종 카운터 메트릭이 이중 집계될 수 있다.

## 릴리즈 QA 체크리스트 (실시간 배틀/이미지 경로)
- 실시간 배틀 연결 UX 회귀
  - `cd /Users/mac/project/KBO_platform/bega_frontend`
  - `npm run test:e2e -- --spec cypress/e2e/cheer-battle-connection.cy.ts`
- Cheer 보드 확장 회귀(배틀+보드+결함수정)
  - `cd /Users/mac/project/KBO_platform/bega_frontend`
  - `npm run cy:run:heal -- --spec cypress/e2e/cheer-battle-connection.cy.ts,cypress/e2e/cheer-board.cy.ts,cypress/e2e/cheer-defect-fix-regression.cy.ts --config baseUrl=http://127.0.0.1:5176`
- 프론트 빌드 검증
  - `cd /Users/mac/project/KBO_platform/bega_frontend`
  - `npm run build`
- 알림 룰 로드 확인
  - `curl -sS http://localhost:9090/api/v1/rules | jq '.data.groups[] | select(.name=="cheer-observability-alerts") | .rules[] | .name'`
  - 신규 룰 `CheerWebSocketDisconnectCritical`, `CheerWebSocketSubscribeStall` 포함 여부 확인
- 스테이징 수동 점검
  - `https://www.begabaseball.xyz/cheer` 접속 후 오프라인/온라인 복구 UX 수동 확인
